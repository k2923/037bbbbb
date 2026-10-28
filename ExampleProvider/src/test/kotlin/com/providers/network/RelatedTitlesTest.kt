package com.providers.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RelatedTitlesTest {
    private val base = "https://anime.example/doraemon/"
    private fun card(url: String, title: String = "Doraemon Adventure") = RelatedTitle(title, url, null)
    private suspend fun load(direct: List<RelatedTitle> = emptyList(), genres: List<String> = emptyList(),
                             genre: suspend (String) -> List<RelatedTitle> = { error("unexpected genre request") },
                             search: suspend (String) -> List<RelatedTitle> = { error("unexpected search request") },
                             timeout: Long = 1500) = RelatedTitles.load(base, "Doraemon (2026) โดเรม่อน พากย์ไทย", direct, genres,
        { it.url }, { it.title }, genre, search, timeout)

    @Test fun `site related cards take priority without any network request`() = runBlocking {
        val result = load(listOf(card(base), card("http://www.anime.example/doraemon/#top"), card("https://anime.example/other/"), card("https://anime.example/other/#a"),
            card("https://ads.example/ad")), listOf("https://anime.example/genre/comedy/"))
        assertEquals(listOf("https://anime.example/other/"), result.map { it.url })
    }

    @Test fun `same genre can recommend different titles and has a twelve card limit`() = runBlocking {
        var calls = 0
        val result = load(genres = listOf("https://anime.example/genre/comedy/", "https://anime.example/genre/family/"), genre = {
            calls++
            (1..20).map { card("https://anime.example/shinchan-$it/", "Shinchan $it") }
        })
        assertEquals(1, calls)
        assertEquals(12, result.size)
        assertTrue(result.first().title.startsWith("Shinchan"))
    }

    @Test fun `title fallback keeps only matching franchise and removes current title`() = runBlocking {
        val result = load(search = { query ->
            assertEquals("Doraemon", query)
            listOf(card(base), card("https://anime.example/adventure/"), card("https://anime.example/random/", "Unrelated title"))
        })
        assertEquals(listOf("https://anime.example/adventure/"), result.map { it.url })
    }

    @Test fun `lookup failures and own deadline do not fail details`() = runBlocking {
        assertTrue(load(search = { error("HTTP 403") }).isEmpty())
        assertTrue(load(search = { delay(1000); emptyList() }, timeout = 10).isEmpty())
    }

    @Test fun `user cancellation is preserved`() = runBlocking {
        val expected = CancellationException("left detail")
        val error = runCatching { load(search = { throw expected }) }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(expected.message, error?.message)
    }

    @Test fun `related parser excludes ads navigation and episode links`() {
        val doc = Jsoup.parse("""<aside><a href="/random"><img alt="Random"></a></aside>
          <div class="yarpp-related">
          <a href="/good/" title="Good"><img data-src="https://images.example/good.jpg" src="data:image/gif,x"></a>
          <a href="/watch/ep1"><img alt="Episode"></a><a href="https://ads.example/x"><img alt="Ad"></a>
          <a href="/category/comedy"><img alt="Category"></a></div>""", base)
        assertEquals(listOf(RelatedTitle("Good", "https://anime.example/good/", "https://images.example/good.jpg")), RelatedTitles.inline(doc))
    }

    @Test fun `genre extraction uses detail metadata and excludes language years and menus`() {
        val doc = Jsoup.parse("""<nav><div class="sgeneros"><a href="/genre/horror/">Horror</a></div></nav>
            <div class="movie-tags"><a href="/category/comedy/">Comedy</a><a href="/category/new-2026/">New</a>
            <a href="/category/thai-dub/">Dub</a><a href="https://ads.example/genre/romance/">Ad</a></div>
            <footer><a href="/genre/action/">Action</a></footer>""", base)
        assertEquals(listOf("https://anime.example/category/comedy/"), RelatedTitles.genres(doc))
    }

    @Test fun `external genre cannot redirect lookup off the provider`() = runBlocking {
        var searched = false
        load(genres = listOf("https://ads.example/genre/comedy"), search = { searched = true; emptyList() })
        assertTrue(searched)
    }
    @Test fun `real site detail fixtures expose related cards and detail genres`() {
        fun doc(path: String, base: String) = Jsoup.parse(java.io.File("../samples/$path").readText(), base)
        for (host in listOf("indy-anime.net", "anime-your.com")) {
            val page = doc("$host/movie.html", "https://$host/movie/")
            val cards = RelatedTitles.inline(page)
            assertEquals(host, 4, cards.size)
            assertTrue(cards.all { it.url.startsWith("https://$host/") && it.title.isNotBlank() })
            assertTrue(cards.all { it.poster?.startsWith("https://") == true })
            assertTrue(RelatedTitles.genres(page).isNotEmpty())
        }
        val movie = doc("movie2freehd.com/movie.html", "https://movie2freehd.com/movies/breakdown/")
        assertTrue(RelatedTitles.inline(movie).size >= 10)
        assertEquals(2, RelatedTitles.genres(movie).size)
        // AHD's sidebar genre directory describes the site, not the selected title.
        assertTrue(RelatedTitles.genres(doc("ahdzero.com/series-detail.html", "https://ahdzero.com/anime/1")).isEmpty())
    }

    @Test fun `parent deadline cancels enrichment instead of returning an empty success`() = runBlocking {
        val error = runCatching {
            kotlinx.coroutines.withTimeout(10) { load(search = { delay(1000); emptyList() }) }
        }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.TimeoutCancellationException)
    }

}
