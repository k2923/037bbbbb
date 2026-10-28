package com.goseries4k

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URLDecoder

class GoSeriesParserTest {
    private fun fixture(name: String) = File("../samples/regression/goseries4k.com/$name.html").readText().let {
        val url = Jsoup.parse(it).selectFirst("link[rel=canonical]")!!.attr("href")
        Jsoup.parse(it, url)
    }
    private val embed = "https://torbo007.com/embed/85a263a6ed2131af9ad984f632eac5b6"

    @Test fun `home cards exclude duplicate sections and use lazy posters`() {
        val cards = GoSeriesParser.cards(fixture("home"))
        assertEquals(16, cards.size) // 20 cards, four repeated across home sections.
        assertTrue(cards.all { it.poster?.startsWith("https://") == true })
        assertTrue(cards.any { it.url.endsWith("/tigers-sniff-the-rose/") })
    }
    @Test fun `search layout and category pages use real title links`() {
        for (name in listOf("search-empty", "category", "category-page2")) {
            val cards = GoSeriesParser.cards(fixture(name))
            assertEquals(name, 16, cards.size)
            assertTrue(cards.all { it.url.startsWith("https://goseries4k.com/") && it.title.isNotBlank() })
        }
        assertNotEquals(GoSeriesParser.cards(fixture("category")).map { it.url }, GoSeriesParser.cards(fixture("category-page2")).map { it.url })
    }
    @Test fun `only fourteen actual episode buttons despite title forty`() {
        val detail = GoSeriesParser.detail(fixture("series"))
        assertEquals((1..14).toList(), detail.episodes.map { it.number })
        assertEquals("134522", detail.episodes.first().ids.single())
        assertEquals("134726", detail.episodes.last().ids.single())
        assertTrue(detail.episodes.all { it.audio == "ซับไทย" })
        assertEquals(2026, detail.year)
        assertTrue(detail.plot!!.contains("มู่หว่านเหยา"))
        assertTrue(detail.poster!!.startsWith("https://"))
    }
    @Test fun `recommendations are content cards even inside list ads wrapper`() {
        val related = GoSeriesParser.cards(fixture("series"))
        assertEquals(8, related.size)
        assertTrue(related.any { it.url.endsWith("/the-first-jasmine/") })
        assertTrue(related.none { it.url.contains("ibit.ly") })
    }
    @Test fun `fresh default iframe is never returned for another episode`() {
        assertEquals(embed, GoSeriesParser.initialPlayer(fixture("series"), "134522"))
        assertNull(GoSeriesParser.initialPlayer(fixture("series"), "134525"))
        val doc = fixture("series")
        doc.selectFirst("#mpPlayer iframe")!!.attr("src", "https://ads.invalid/embed/ad")
        assertNull(GoSeriesParser.initialPlayer(doc, "134522"))
    }
    @Test fun `pagination preserves search and replaces an existing page suffix`() {
        val doc = fixture("category")
        assertTrue(GoSeriesParser.hasNext(doc))
        assertEquals(fixture("category-page2").baseUri(), GoSeriesParser.pageUrl(doc.baseUri(), 2))
        assertEquals("https://goseries4k.com/page/3/?s=hello", GoSeriesParser.pageUrl("https://goseries4k.com/page/2/?s=hello", 3))
        doc.select("a.next.page-numbers").attr("href", "#")
        assertFalse(GoSeriesParser.hasNext(doc))
    }
    @Test fun `listing rejects sidebar ads and foreign detail links`() {
        val doc = fixture("home")
        doc.selectFirst(".-movie .entry-title a")!!.attr("href", "https://ads.invalid/ad")
        doc.body().append("<aside><div class=-movie><h2 class=entry-title><a href=/ad/>AD</a></h2></div></aside>")
        assertEquals(16, GoSeriesParser.cards(doc).size) // duplicate of replaced card still occurs in another section.
        assertTrue(GoSeriesParser.cards(doc).none { it.title == "AD" || it.url.contains("ads.invalid") })
    }
    @Test fun `duplicate episode backups preserved and audio groups stay distinct`() {
        val doc = fixture("series")
        doc.selectFirst(".mp-ep-list")!!.append("<button class=mp-ep-btn data-id=999>EP 1</button><button class=mp-ep-btn data-id=999>EP 1</button>")
        val detail = GoSeriesParser.detail(doc)
        assertEquals(14, detail.episodes.size)
        assertEquals(listOf("134522", "999"), detail.episodes.first().ids)
        doc.selectFirst(".mp-ep-group")!!.after("<div class=mp-ep-group><div class=mp-epg-title>พากย์ไทย</div><button class=mp-ep-btn data-id=888>EP 1</button></div>")
        assertEquals(15, GoSeriesParser.detail(doc).episodes.size)
    }
    @Test fun `configured category routes exist in uploaded menu`() {
        val html = File("../samples/regression/goseries4k.com/home.html").readText()
        // Reduced fixture contains home section category headers; full upload contains the dub menu.
        assertTrue(URLDecoder.decode(html, "UTF-8").contains("/category/ดู-ซีรี่ย์จีน-ออนไลน์/"))
        assertTrue(URLDecoder.decode(html, "UTF-8").contains("/category/ดูซีรี่ย์เกาหลี-ออนไลน์/"))
    }
    @Test fun `episode payload contains only identity and rejects foreign pages`() {
        val data = SeriesPlayback("https://goseries4k.com/tigers-sniff-the-rose/", listOf("134525", "134525"))
        assertEquals(data.copy(ids = listOf("134525")), SeriesPlayback.decode(data.encode()))
        assertFalse(data.encode().contains("torbo"))
        assertThrows(IllegalArgumentException::class.java) { SeriesPlayback.decode("""{"page":"https://ads.invalid/","ids":["12"]}""") }
        assertThrows(IllegalArgumentException::class.java) { SeriesPlayback.decode("""{"page":"https://goseries4k.com/","ids":["not-an-id"]}""") }
    }
    private fun snapshot(current: String, player: String, initial: String = "134522", original: String = embed) = ObjectMapper().writeValueAsString(
        mapOf("id" to "134525", "currentId" to current, "player" to player, "initialId" to initial, "initialPlayer" to original))

    @Test fun `DOM guard rejects old iframe while ajax episode switch is incomplete`() {
        assertNull(EpisodeSelection.selected("134525", snapshot("134522", embed)))
        assertNull(EpisodeSelection.selected("134525", snapshot("134525", embed)))
        val next = "https://torbo007.com/embed/abc123"
        assertEquals(next, EpisodeSelection.selected("134525", snapshot("134525", next)))
        // Android evaluateJavascript wraps a JS return string in JSON quotes.
        assertEquals(next, EpisodeSelection.selected("134525", ObjectMapper().writeValueAsString(snapshot("134525", next))))
        assertNull(EpisodeSelection.selected("134525", snapshot("134525", "https://ads.invalid/embed/ad")))
        assertNull(EpisodeSelection.selected("134525", "null"))
    }
    @Test fun `only observed content HLS path accepted preserving signature`() {
        val media = "https://torbo007.com/api/stream/abc123/index.m3u8?token=synthetic&expires=123&parent=torbo007.com&sid=test"
        assertTrue(TorboPlayer.media(media, embed))
        for (bad in listOf(embed, "https://ads.invalid/ad.m3u8", "https://torbo007.com/ad.m3u8", "https://torbo007.com/api/stream/abc123/index.m3u8/evil", "https://torbo007.com:444/api/stream/abc123/index.m3u8")) {
            assertFalse(bad, TorboPlayer.media(bad, embed))
        }
        assertNull(TorboPlayer.embed("https://torbo007.com.evil.invalid/embed/abc123"))
        assertThrows(IllegalArgumentException::class.java) { EpisodeSelection.script("1';alert(1)") }
    }
}
