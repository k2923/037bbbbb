package com.twentyfourhd

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import kotlin.coroutines.cancellation.CancellationException

class TwentyFourCatalogTest {
    private val base = "https://www.24hd.today"
    private val json = ObjectMapper()
    private fun fixture(name: String) = File("../samples/regression/24hd.today/api/$name.json").readText()
    private fun source(name: String) = json.readTree(fixture(name))[0].path("link").asText()

    @Test fun `public listings keep original URLs posters types and real page count`() = runBlocking {
        val listing = TwentyFourCatalog(base) { url, fresh ->
            assertTrue(url.contains("page=2"))
            assertFalse(fresh)
            CatalogPage(fixture("listing"), 200, 3)
        }.listing(base, 2)
        assertEquals(2, listing.cards.size)
        assertFalse(listing.cards[0].series)
        assertTrue(listing.cards[1].series)
        assertEquals(source("movie"), listing.cards[0].url)
        assertTrue(listing.cards.all { it.poster?.startsWith("https://") == true })
        assertTrue(listing.hasNext)
    }

    @Test fun `categories resolve actual term IDs and preserve category on subsequent pages`() = runBlocking {
        val calls = mutableListOf<String>()
        val catalog = TwentyFourCatalog(base) { url, _ ->
            calls += url
            if (url.contains("/categories?")) CatalogPage(fixture("category"), 200)
            else {
                assertTrue(url.contains("categories=36300"))
                CatalogPage(fixture("listing"), 200, 2)
            }
        }
        val category = "$base/category/ซีรี่ย์จีน"
        assertTrue(catalog.listing(category, 1).hasNext)
        assertFalse(catalog.listing(category, 2).hasNext)
        assertEquals(1, calls.count { it.contains("/categories?") })
    }

    @Test fun `search encodes query and an empty result remains empty`() = runBlocking {
        val result = TwentyFourCatalog(base) { url, _ ->
            assertTrue(URLDecoder.decode(url, "UTF-8").contains("search=หนัง & ซีรีส์"))
            CatalogPage("[]", 200, 0)
        }.listing(base, 1, "หนัง & ซีรีส์")
        assertTrue(result.cards.isEmpty())
        assertFalse(result.hasNext)
    }

    @Test fun `movie detail reads real ACF player synopsis and language freshly`() = runBlocking {
        val detail = TwentyFourCatalog(base) { url, fresh ->
            assertTrue(fresh)
            assertTrue(url.contains("slug=the-monster-she-married-2026"))
            CatalogPage(fixture("movie"), 200)
        }.detail(source("movie"), fresh = true)
        assertEquals("The Monster She Married (2026)", detail.title)
        assertEquals("ซับไทย", detail.audio)
        assertTrue(detail.plot.orEmpty().contains("หญิงสาว"))
        assertTrue(detail.episodes.isEmpty())
        assertEquals(listOf("https://player77hdfree.xyz/embed/rODV0n_DN-wwA"), detail.players)
    }

    @Test fun `series uses only real episodes and merges duplicate episode backup URLs`() = runBlocking {
        val posts = json.readTree(fixture("series"))
        val eps = posts[0].path("acf").path("ep_list") as com.fasterxml.jackson.databind.node.ArrayNode
        eps.addObject().put("title", "EP.1").put("link", "https://backup.example/ep1")
        eps.addObject().put("title", "EP.99").put("link", "javascript:alert(1)")
        val detail = TwentyFourCatalog(base) { _, _ -> CatalogPage(json.writeValueAsString(posts), 200) }
            .detail(source("series"), true)
        assertEquals((1..12).toList(), detail.episodes.map { it.number })
        assertEquals(2, detail.episodes.first().players.size)
        assertTrue(detail.episodes.first().players.contains("https://backup.example/ep1"))
    }

    @Test fun `missing category rejected instead of returning unrelated homepage posts`() = runBlocking {
        var calls = 0
        val error = runCatching {
            TwentyFourCatalog(base) { _, _ -> calls++; CatalogPage("[]", 200) }.listing("$base/category/missing", 1)
        }.exceptionOrNull()
        assertNotNull(error)
        assertEquals(1, calls)
    }

    @Test fun `failed or malformed API never looks like a successful empty catalog`() = runBlocking {
        for (page in listOf(CatalogPage("Denied", 403), CatalogPage("{}", 200), CatalogPage("null", 200))) {
            assertNotNull(runCatching { TwentyFourCatalog(base) { _, _ -> page }.listing(base, 1) }.exceptionOrNull())
        }
    }

    @Test fun `missing or invalid ACF players must fail so HTML fallback can run`() = runBlocking {
        for (acf in listOf("{}", "{\"file\":\"javascript:alert(1)\",\"file_2\":\"\"}")) {
            val posts = json.readTree(fixture("movie"))
            (posts[0] as com.fasterxml.jackson.databind.node.ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("acf", json.readTree(acf))
            val error = runCatching {
                TwentyFourCatalog(base) { _, _ -> CatalogPage(json.writeValueAsString(posts), 200) }
                    .detail(source("movie"), true)
            }.exceptionOrNull()
            assertNotNull(error)
        }
    }

    @Test fun `detail must match selected URL and propagate cancellation`() = runBlocking {
        assertNotNull(runCatching {
            TwentyFourCatalog(base) { _, _ -> CatalogPage(fixture("movie"), 200) }.detail("$base/another-film/")
        }.exceptionOrNull())
        assertTrue(runCatching {
            TwentyFourCatalog(base) { _, _ -> throw CancellationException() }.detail(source("movie"))
        }.exceptionOrNull() is CancellationException)
    }
}
