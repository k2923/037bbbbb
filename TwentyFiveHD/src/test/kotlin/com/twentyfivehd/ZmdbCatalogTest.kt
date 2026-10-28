package com.twentyfivehd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ZmdbCatalogTest {
    private val embed = "https://zmdb.net/embed?id=123&type=tv&season=2&episode=7&token=old"
    private val api = "https://zmdb.net/api/embed/bootstrap?id=123&type=tv"
    private val content = """{"mediaType":"tv","seasons":[{"seasonNumber":2,"episodes":[
        {"episodeNumber":7,"hasLinks":true},{"episodeNumber":8,"hasLinks":false}
    ]}]}"""
    private val html = """<script id="bootstrap">{"content":$content}</script>"""
    private val backup = """{"success":true,"linkToken":"new-token","content":$content}"""

    @Test fun `movie metadata needs no player request even when player is unavailable`() = runBlocking {
        val resolver = ZmdbResolver { _, _, _ -> error("Metadata must not request a movie player") }
        assertEquals(ZmdbCatalog(series = false), resolver.catalog("https://zmdb.net/embed?id=123&type=movie", "https://25-hd.com/film/"))
    }

    @Test fun `series retains actual episodes from available HTML without a backup request`() = runBlocking {
        var requests = 0
        val catalog = ZmdbResolver { url, _, _ ->
            requests++
            assertEquals(embed, url)
            WebPage(url, html)
        }.catalog(embed, "https://25-hd.com/show/")
        assertTrue(catalog.series)
        assertEquals(listOf(2 to 7), catalog.episodes.map { it.season to it.number })
        assertEquals(1, requests)
    }

    @Test fun `series uses public bootstrap on server error missing script and transport failure`() = runBlocking {
        for (failure in listOf("http", "html", "io")) {
            val requests = mutableListOf<String>()
            val catalog = ZmdbResolver { url, ref, headers ->
                requests += url
                if (url == embed) when (failure) {
                    "http" -> WebPage(url, "Maintenance", 503)
                    "html" -> WebPage(url, "<html>Missing player</html>")
                    else -> throw IOException("Synthetic timeout")
                } else {
                    assertEquals(api, url)
                    assertEquals(embed, ref)
                    assertTrue(headers.isEmpty())
                    WebPage(url, backup)
                }
            }.catalog(embed, "https://25-hd.com/show/")
            assertEquals(listOf(embed, api), requests)
            assertTrue(catalog.series)
            assertEquals(listOf(2 to 7), catalog.episodes.map { it.season to it.number })
            assertTrue(catalog.episodes.single().url.contains("season=2&episode=7"))
        }
    }

    @Test fun `unavailable series never becomes a movie or invents episodes`() = runBlocking {
        for (response in listOf(WebPage(api, "{}", 503), WebPage(api, """{"success":false}"""),
            WebPage(api, """{"success":true,"content":{}}"""),
            WebPage(api, """{"success":true,"content":{"mediaType":"movie"}}"""))) {
            val failure = runCatching {
                ZmdbResolver { url, _, _ -> if (url == embed) WebPage(url, "Down", 503) else response }
                    .catalog(embed, "https://25-hd.com/show/")
            }.exceptionOrNull()
            assertNotNull(failure)
        }
    }

    @Test fun `cancellation stops both HTML and API requests without more fallbacks`() = runBlocking {
        for (cancelApi in listOf(false, true)) {
            var requests = 0
            val failure = runCatching {
                ZmdbResolver { url, _, _ ->
                    requests++
                    if (cancelApi && url == embed) WebPage(url, "Down", 503) else throw CancellationException()
                }.catalog(embed, "https://25-hd.com/show/")
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals(if (cancelApi) 2 else 1, requests)
        }
    }

    @Test fun `playback uses a fresh API token and original embed referer after HTML failure`() = runBlocking {
        val calls = mutableListOf<String>()
        val streams = ZmdbResolver { url, ref, headers ->
            calls += url
            when {
                url == embed -> WebPage(url, "Down", 503)
                url == api -> WebPage(url, backup)
                url.contains("/api/embed/links?") -> {
                    assertEquals("https://zmdb.net/api/embed/links?id=123&type=tv&season=2&episode=7", url)
                    assertEquals(embed, ref)
                    assertEquals("Bearer new-token", headers["Authorization"])
                    WebPage(url, """{"success":true,"playerEmbedLinks":[{"embedUrl":"https://backup.example/embed/123"}]}""")
                }
                else -> error("Unexpected request $url")
            }
        }.resolve(embed, "https://25-hd.com/show/")
        assertEquals(3, calls.size)
        assertEquals("https://backup.example/embed/123", streams.single().url)
        assertEquals(embed, streams.single().referer)
    }

    @Test fun `backup VIP metadata does not request playback links`() = runBlocking {
        var requests = 0
        val streams = ZmdbResolver { url, _, _ ->
            requests++
            if (url == embed) WebPage(url, "Down", 503)
            else WebPage(url, """{"success":true,"content":{"mediaType":"tv","isVipOnly":true}}""")
        }.resolve(embed, "https://25-hd.com/show/")
        assertTrue(streams.isEmpty())
        assertEquals(2, requests)
    }
}
