package com.goseries4k

import com.providers.network.CatalogPagination
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal data class SeriesCard(val title: String, val url: String, val poster: String?)
internal data class SeriesEpisode(val number: Int, val audio: String, val ids: List<String>)
internal data class SeriesDetail(val title: String, val poster: String?, val plot: String?, val year: Int?, val episodes: List<SeriesEpisode>)

internal object GoSeriesParser {
    const val BASE = "https://goseries4k.com"
    private val episodeNumber = Regex("(?i)\\bEP\\s*\\.?\\s*(\\d+)\\b")
    private val year = Regex("\\b(20\\d{2}|19\\d{2})\\b")
    fun local(value: String, base: String = BASE): String? = base.toHttpUrlOrNull()?.resolve(value)?.takeIf {
        value.isNotBlank() && it.host == "goseries4k.com" && it.username.isEmpty() && it.password.isEmpty()
    }?.newBuilder()?.fragment(null)?.build()?.toString()

    private fun poster(node: Element, base: String): String? = node.selectFirst("img")?.let { image ->
        listOf("data-src", "data-lazy-src", "src").firstNotNullOfOrNull { attr ->
            image.attr(attr).takeIf { it.isNotBlank() }?.let { base.toHttpUrlOrNull()?.resolve(it) }
                ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
        }
    }
    fun cards(doc: Document): List<SeriesCard> = doc.select("#main .list-movie .-movie, #main .s-grid .-movie").mapNotNull { card ->
        val a = card.selectFirst(".entry-title a[href]") ?: return@mapNotNull null
        val url = local(a.attr("href"), doc.baseUri()) ?: return@mapNotNull null
        val title = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SeriesCard(title, url, poster(card, doc.baseUri()))
    }.distinctBy { it.url }

    fun pageUrl(base: String, page: Int) = CatalogPagination.path(base, page)
    fun hasNext(doc: Document) = CatalogPagination.hasNext(doc, "a.next.page-numbers[href]")

    fun detail(doc: Document): SeriesDetail {
        val title = doc.selectFirst("#main .-titlehead h2.entry-title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() } ?: error("ไม่พบชื่อซีรีส์ในหน้ารายละเอียด")
        val episodes = doc.select("#main .mp-ep-group").flatMap { group ->
            val audio = group.selectFirst(".mp-epg-title")?.text().orEmpty().trim()
            group.select("button.mp-ep-btn[data-id]").mapNotNull { button ->
                val id = button.attr("data-id").takeIf { it.matches(Regex("[0-9]+")) } ?: return@mapNotNull null
                val number = episodeNumber.find(button.text())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                SeriesEpisode(number, audio, listOf(id))
            }
        }.groupBy { it.audio to it.number }.map { (_, copies) ->
            copies.first().copy(ids = copies.flatMap { it.ids }.distinct())
        }.sortedBy { it.number }
        return SeriesDetail(title, doc.selectFirst("#main .-boximg")?.let { poster(it, doc.baseUri()) },
            doc.select("#main .-boxdetail p").joinToString("\n") { it.text() }.takeIf { it.isNotBlank() },
            year.find(title)?.value?.toIntOrNull(), episodes)
    }

    /** Only use the fresh default iframe when the page identifies it as the requested episode. */
    fun initialPlayer(doc: Document, id: String): String? {
        val player = doc.selectFirst("#mpPlayer[data-post-id]") ?: return null
        if (player.attr("data-post-id") != id) return null
        val active = doc.select("button.mp-ep-btn.active[data-id]")
        if (active.isNotEmpty() && (active.size != 1 || active.first()!!.attr("data-id") != id)) return null
        return player.select("iframe[src]").firstNotNullOfOrNull { TorboPlayer.embed(it.absUrl("src")) }
    }
}
