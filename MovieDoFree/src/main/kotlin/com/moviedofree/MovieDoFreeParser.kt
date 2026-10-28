package com.moviedofree

import com.providers.network.CatalogPagination
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class MovieCard(val title: String, val url: String, val poster: String?)
internal data class MovieDetail(val title: String, val poster: String?, val plot: String?, val player: String)

internal object MovieDoFreeParser {
    private fun poster(image: Element?): String? {
        if (image == null) return null
        return listOf("data-src", "data-lazy-src", "data-original", "src").asSequence()
            .map { image.absUrl(it) }.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)

    fun cards(document: Document): List<MovieCard> = document.select(".column_1_center > .row")
        .lastOrNull { it.selectFirst(".movie-box") != null }?.select(".movie-box").orEmpty().mapNotNull { card ->
        val anchor = card.selectFirst(".movie-title a[href]") ?: return@mapNotNull null
        val title = anchor.text().trim()
        val url = anchor.absUrl("href")
        if (title.isEmpty() || !url.startsWith("https://")) return@mapNotNull null
        MovieCard(title, url, poster(card.selectFirst(".movie-image img")))
    }.distinctBy { it.url }

    fun detail(document: Document): MovieDetail = MovieDetail(
        document.selectFirst(".box-header .title h1")?.text()?.trim().orEmpty(),
        poster(document.selectFirst(".column_1_center center img")),
        document.select(".column_1_center .blog.column > p, .column_1_center .entry-content p, .column_1_center .synopsis")
            .asSequence().map { it.text() }.firstOrNull { it.isNotBlank() },
        document.selectFirst("iframe#frame[src]")?.absUrl("src") ?: error("ไม่พบตัวเล่นของ Moviedofree")
    )

    fun streams(html: String, playerUrl: String): List<String> {
        // Only the main JWPlayer setup contains the movie; ignore the separate ads player.
        val sources = Regex("""jwplayer\(["']jwplayer["']\)\s*\.setup\s*\(\s*\{[\s\S]*?sources\s*:\s*\[([\s\S]*?)\]""")
            .find(html)?.groupValues?.get(1) ?: return emptyList()
        return Regex("""file\s*:\s*["']([^"']+)["']""").findAll(sources).mapNotNull {
            val url = runCatching { URI(playerUrl).resolve(it.groupValues[1].replace("\\/", "/")).toString() }.getOrNull()
            url?.takeIf { value -> value.startsWith("https://") && value.substringBefore('?').endsWith(".m3u8") }
        }.distinct().toList()
    }

    fun hasNext(document: Document): Boolean =
        CatalogPagination.hasNext(document, "a.next.page-numbers[href]")
}
