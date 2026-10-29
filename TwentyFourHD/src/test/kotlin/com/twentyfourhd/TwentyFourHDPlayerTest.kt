package com.twentyfourhd

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class TwentyFourHDPlayerTest {
    private val page = "https://www.24hd.today/bloodhounds-2023/"
    private val wrapper = "https://ongetplay.xyz/playhls/play.php?id=28489"
    private val embed = "https://playermhd.p2phls.xyz/embed/cqvhgyvw7e/"
    private val playlist = "https://vdohls.com/cqvhgyvw7e/playlist.m3u8"
    private fun html(name: String) = File("../samples/regression/24hd.today/bloodhounds/$name.html").readText()
    private fun document(url: String, text: String, status: Int = 200) = PlayerDocument(url, Jsoup.parse(text, url), status)

    @Test fun `Bloodhounds wrapper resolves exact player and uses its referer`() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val result = TwentyFourHDPlayer.resolve(wrapper, page) { url, referer, allowRedirects ->
            assertEquals(url == wrapper, allowRedirects)
            calls += url to referer
            when (url) {
                wrapper -> document(url, html("wrapper"))
                embed -> document(url, html("embed"))
                else -> error("Unexpected request: $url")
            }
        }
        assertEquals(PlayerStream(playlist, embed), result)
        assertEquals(listOf(wrapper to page, embed to wrapper), calls)
    }

    @Test fun `direct player remains supported without following iframes`() = runBlocking {
        var calls = 0
        val direct = "https://player77hdfree.xyz/embed/safe"
        val result = TwentyFourHDPlayer.resolve(direct, page) { url, referer, allowRedirects ->
            assertTrue(allowRedirects)
            calls++
            assertEquals(page, referer)
            document(url, html("embed"))
        }
        assertEquals(PlayerStream(playlist, direct), result)
        assertEquals(1, calls)
    }

    @Test fun `empty foreign or malformed wrapper iframes never trigger a request`() = runBlocking {
        for (src in listOf("", "https://ads.invalid/embed/cqvhgyvw7e/", "https://playermhd.p2phls.xyz.evil.invalid/embed/cqvhgyvw7e/", "https://playermhd.p2phls.xyz:444/embed/cqvhgyvw7e/", "https://playermhd.p2phls.xyz/embed/other/../../private")) {
            var calls = 0
            val result = TwentyFourHDPlayer.resolve(wrapper, page) { url, _, _ ->
                calls++
                document(url, """<iframe id="player" src="$src"></iframe>""")
            }
            assertNull(src, result)
            assertEquals(src, 1, calls)
        }
    }

    @Test fun `redirect to another episode or foreign host is rejected`() = runBlocking {
        for (redirect in listOf("https://ongetplay.xyz/playhls/play.php?id=28490", "https://ads.invalid/playhls/play.php?id=28489")) {
            var calls = 0
            for (content in listOf("wrapper", "embed")) {
                calls = 0
                assertNull(TwentyFourHDPlayer.resolve(wrapper, page) { _, _, _ -> calls++; document(redirect, html(content)) })
                assertEquals(1, calls)
            }
        }
        for (redirect in listOf("https://playermhd.p2phls.xyz/embed/other/", "https://ads.invalid/embed/cqvhgyvw7e/")) {
            var calls = 0
            assertNull(TwentyFourHDPlayer.resolve(wrapper, page) { url, _, _ ->
                calls++
                if (url == wrapper) document(url, html("wrapper")) else document(redirect, html("embed"))
            })
            assertEquals(2, calls)
        }
    }

    @Test fun `backup empty iframe and unsuccessful responses do not produce a video`() = runBlocking {
        val backup = "https://ongetplay.xyz/playproxy/play.php?id=28489"
        assertNull(TwentyFourHDPlayer.resolve(backup, page) { url, _, _ -> document(url, "<iframe id=player src=''></iframe>") })
        assertNull(TwentyFourHDPlayer.resolve(wrapper, page) { url, _, _ -> document(url, html("embed"), 403) })
        assertNull(TwentyFourHDPlayer.resolve(wrapper, page) { url, _, _ ->
            if (url == wrapper) document(url, html("wrapper")) else document(url, html("embed"), 403)
        })
    }

    @Test fun `nested player redirects are not followed`() = runBlocking {
        var calls = 0
        assertNull(TwentyFourHDPlayer.resolve(wrapper, page) { url, _, allowRedirects ->
            calls++
            if (url == wrapper) {
                assertTrue(allowRedirects)
                document(url, html("wrapper"))
            } else {
                assertFalse(allowRedirects)
                document(url, "", 302)
            }
        })
        assertEquals(2, calls)
    }

    @Test fun `cancellation is propagated`() = runBlocking {
        val error = runCatching { TwentyFourHDPlayer.resolve(wrapper, page) { _, _, _ -> throw CancellationException() } }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
