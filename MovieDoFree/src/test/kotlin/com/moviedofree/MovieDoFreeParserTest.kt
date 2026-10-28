package com.moviedofree

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MovieDoFreeParserTest {
    private val base = "https://moviedofree.com/"

    @Test fun `reads movie posters not IMDb stars and excludes sidebar cards`() {
        val card = """<div class="movie-box"><div class="movie-title"><a href="/movie/">A movie</a></div>
            <div class="movie-imdb"><img src="/star.png"></div><div class="movie-image"><img src="/poster.jpg"></div></div>"""
        val doc = Jsoup.parse("<aside>$card</aside><div class='column_1_center'><div class='row'>$card$card</div></div>", base)
        val cards = MovieDoFreeParser.cards(doc)
        assertEquals(1, cards.size)
        assertEquals("A movie", cards.single().title)
        assertEquals(base + "poster.jpg", cards.single().poster)
        assertEquals(base + "movie/", cards.single().url)
    }

    @Test fun `uses paginated grid after repeated featured movie row`() {
        fun card(url: String, title: String) = """<div class="movie-box"><div class="movie-title"><a href="$url">$title</a></div></div>"""
        val doc = Jsoup.parse("""<div class="column_1_center">
            <div class="row">${card("/featured/", "Featured")}</div>
            <div class="row">${card("/page2/", "Page two movie")}</div>
            <div class="row"><a href="https://ads.example">Ad</a></div>
        </div>""", base)
        assertEquals("Page two movie", MovieDoFreeParser.cards(doc).single().title)
    }

    @Test fun `empty search does not return sidebar recommendations`() {
        val doc = Jsoup.parse("""<div class="column_1_center">ไม่พบข้อมูล</div>
            <aside><div class="movie-box"><div class="movie-title"><a href="/recommended/">Recommended</a></div></div></aside>""", base)
        assertTrue(MovieDoFreeParser.cards(doc).isEmpty())
        assertFalse(MovieDoFreeParser.hasNext(doc))
    }

    @Test fun `detail selects main player and leaves missing synopsis empty`() {
        val doc = Jsoup.parse("""<h1>Site name</h1><div class="column_1_center">
            <div class="box-header"><div class="title"><h1>A movie</h1></div></div><center><img src="/poster.jpg"></center>
            <iframe src="https://ads.example"></iframe><iframe id="frame" src="/play.php?vid=M1"></iframe>
        </div>""", base)
        val detail = MovieDoFreeParser.detail(doc)
        assertEquals("A movie", detail.title)
        assertEquals(base + "poster.jpg", detail.poster)
        assertEquals(base + "play.php?vid=M1", detail.player)
        assertNull(detail.plot)
    }

    @Test fun `current site synopsis is read from content blog not sidebar`() {
        val doc = Jsoup.parse("""<aside><div class="blog column"><p>Advertisement</p></div></aside>
            <div class="column_1_center"><div class="box-header"><div class="title"><h1>Spermworld (2024)</h1></div></div>
            <iframe id="frame" src="/play.php?vid=M14310"></iframe>
            <div class="blog column"><p>ชายสามคนเข้าสู่ฟอรั่มออนไลน์</p></div></div>""", base)
        assertEquals("ชายสามคนเข้าสู่ฟอรั่มออนไลน์", MovieDoFreeParser.detail(doc).plot)
    }

    @Test fun `empty synopsis paragraphs do not hide later content`() {
        val doc = Jsoup.parse("""<div class="column_1_center">
            <iframe id="frame" src="/play.php?vid=M14310"></iframe>
            <div class="blog column"><p></p><p> &nbsp; </p><p>เรื่องย่อที่มีข้อความ</p></div></div>""", base)
        assertEquals("เรื่องย่อที่มีข้อความ", MovieDoFreeParser.detail(doc).plot)
    }

    @Test fun `legacy synopsis selectors ignore sidebar content`() {
        for (sidebar in listOf("<div class=entry-content><p>Advertisement</p></div>", "<div class=synopsis>Advertisement</div>")) {
            val doc = Jsoup.parse("""<aside>$sidebar</aside><div class=column_1_center>
                <iframe id=frame src="/play.php?vid=M1"></iframe>
                <div class=synopsis>Actual synopsis</div></div>""", base)
            assertEquals("Actual synopsis", MovieDoFreeParser.detail(doc).plot)
        }
    }

    @Test fun `extracts signed main movie HLS and ignores advertisement video`() {
        val html = """
            jwplayer('adsplayer').setup({sources:[{file:'https://ads.example/ad.m3u8'}]});
            jwplayer("jwplayer").setup({sources:[{file:'/movie/master.m3u8?sig=synthetic',label:'Default'}]});
        """
        assertEquals(listOf(base + "movie/master.m3u8?sig=synthetic"), MovieDoFreeParser.streams(html, base + "play.php?vid=M1"))
        assertTrue(MovieDoFreeParser.streams("<h1>File unavailable</h1>", base).isEmpty())
    }

    @Test fun `year pagination retains the year query after the page path`() {
        assertEquals(base + "fillter-year/page/2/?y=2024", MovieDoFreeParser.pageUrl(base + "fillter-year?y=2024", 2))
        assertEquals(base + "netflix/page/3/", MovieDoFreeParser.pageUrl(base + "netflix/", 3))
    }

    @Test fun `lazy poster uses the real image rather than an inline placeholder`() {
        val doc = Jsoup.parse("""<div class="column_1_center"><div class="row"><div class="movie-box">
            <div class="movie-title"><a href="/movie/">Movie</a></div>
            <div class="movie-image"><img src="data:image/gif;base64,AA" data-src="/real.jpg"></div>
        </div></div></div>""", base)
        assertEquals(base + "real.jpg", MovieDoFreeParser.cards(doc).single().poster)
    }

    @Test fun `disabled and current page controls do not request another page`() {
        val base = "https://moviedofree.com/page/2/"
        for (href in listOf("", "#", "#top", base, "javascript:void(0)")) {
            val doc = org.jsoup.Jsoup.parse("""<div class=pagination><a class="next page-numbers" href="$href">Next</a></div>""", base)
            assertFalse(href, MovieDoFreeParser.hasNext(doc))
        }
        val doc = org.jsoup.Jsoup.parse("""<div class=pagination><a class="next page-numbers" href="/page/3/">Next</a></div>""", base)
        assertTrue(MovieDoFreeParser.hasNext(doc))
    }

    @Test fun `pagination replaces an existing page while preserving category filters`() {
        assertEquals("https://moviedofree.com/category/action/page/3/?year=2024&lang=th",
            MovieDoFreeParser.pageUrl("https://moviedofree.com/category/action/page/2/?year=2024&lang=th", 3))
    }

    @Test fun `every category uses an exact captured website menu destination`() {
        val doc = Jsoup.parse(java.io.File("../samples/regression/moviedofree.com/menu.html").readText(), base)
        val destinations = doc.select("a[href]").map { it.absUrl("href") }.toSet()
        SiteCategories.entries.forEach { (path, title) -> assertTrue(title, base.trimEnd('/') + path in destinations) }
        assertEquals(listOf("หนังล่าสุด", "Action บู้", "Cartoon การ์ตูน", "หนังไทย"), SiteCategories.homeEntries.map { it.second })
    }
}
