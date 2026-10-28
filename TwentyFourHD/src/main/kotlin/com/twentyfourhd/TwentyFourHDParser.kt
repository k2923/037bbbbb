package com.twentyfourhd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.Base64Variants
import com.providers.network.CatalogPagination
import org.jsoup.nodes.Document
import java.net.URI

internal data class SiteCard(val title: String, val url: String, val poster: String?, val series: Boolean)
internal data class SiteEpisode(val number: Int, val title: String, val players: List<String>)
internal data class SiteDetail(val title: String, val poster: String?, val plot: String?, val episodes: List<SiteEpisode>, val players: List<String>, val audio: String?)

internal object TwentyFourHDParser {
    private val json = ObjectMapper()
    private val number = Regex("(?:EP\\.?|ตอนที่)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val cssUrl = Regex("""background-image\s*:\s*url\(["']?([^"')]+)["']?\)""", RegexOption.IGNORE_CASE)

    private val cssRule = Regex("([^{}]+)\\{([^{}]*)\\}")
    private val cardClass = Regex("\\.(e-loop-item-\\d+)(?![\\w-])")
    private val cardId = Regex("e-loop-item-\\d+")

    fun httpUrl(base: String, value: String): String? = runCatching {
        if (value.isBlank() || value.contains("_files/") || value.startsWith("data:")) return null
        URI(base).resolve(value).takeIf { it.scheme in listOf("https", "http") && it.host != null }?.toASCIIString()
    }.getOrNull()

    private fun listing(doc: Document) = doc.select(".elementor-widget-loop-grid").let { grids ->
        grids.firstOrNull { it.selectFirst("[data-next-page]") != null } ?: grids.lastOrNull()
    } ?: doc

    fun cards(doc: Document): List<SiteCard> {
        val styles = doc.select("style").joinToString("\n") { style ->
            val lazyCss = runCatching { String(Base64Variants.getDefaultVariant().decode(style.attr("data-vip24hd-css")), Charsets.UTF_8) }.getOrDefault("")
            style.data() + "\n" + lazyCss
        }
        // Index each style rule once instead of scanning the stylesheet for every card.
        val posters = mutableMapOf<String, String>()
        cssRule.findAll(styles).forEach { rule ->
            val image = cssUrl.find(rule.groupValues[2])?.groupValues?.get(1) ?: return@forEach
            cardClass.findAll(rule.groupValues[1]).forEach { match ->
                posters.getOrPut(match.groupValues[1]) { image }
            }
        }
        return listing(doc).select(".e-loop-item, article.elementor-post").mapNotNull { e ->
            val a = e.selectFirst("a.e-con[href], .elementor-post__title a[href]") ?: return@mapNotNull null
            val url = httpUrl(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val title = e.select(".elementor-heading-title").lastOrNull()?.text()?.trim()
                ?: e.selectFirst(".elementor-post__title")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val id = e.classNames().firstOrNull { cardId.matches(it) }
            val image: String? = posters[id]
                ?: e.selectFirst("img")?.let { img ->
                    listOf("data-src", "data-lazy-src", "src").firstNotNullOfOrNull { httpUrl(doc.baseUri(), img.attr(it)) }
                }
            SiteCard(title, url, image?.let { httpUrl(doc.baseUri(), it) }, number.containsMatchIn(title) || title.contains("Season", true))
        }.distinctBy { it.url }
    }

    fun detail(doc: Document): SiteDetail {
        val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        require(title.isNotBlank()) { "ไม่พบข้อมูลเรื่องจาก 24HD" }
        val episodes = doc.select("button.swicth-ep").mapNotNull { e ->
            val n = number.find(e.text())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val players = listOf("data-link", "data-link2").mapNotNull { httpUrl(doc.baseUri(), e.attr(it)) }.distinct()
            if (players.isEmpty()) null else SiteEpisode(n, e.text(), players)
        }.groupBy { it.number }.values.map { rows ->
            rows.first().copy(players = rows.flatMap { it.players }.distinct())
        }.sortedBy { it.number }
        val players = (doc.select("a[target=box-player][href]").map { it.attr("href") } +
            doc.select("iframe[name=box-player]").flatMap { listOf(it.attr("data-src"), it.attr("src")) })
            .mapNotNull { httpUrl(doc.baseUri(), it) }.distinct()
        val audio = doc.select("h2").firstOrNull { it.text().startsWith("เสียง") }?.text()?.substringAfter(':')?.trim()
        return SiteDetail(title, httpUrl(doc.baseUri(), doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()),
            doc.selectFirst(".elementor-widget-theme-post-content")?.text()?.takeIf { it.isNotBlank() }, episodes, players, audio)
    }

    fun stream(doc: Document): String? {
        val config = doc.select("script").firstNotNullOfOrNull { script ->
            Regex("window\\.PLAYER_CONFIG\\s*=\\s*(\\{.*\\});?", RegexOption.DOT_MATCHES_ALL).find(script.data())?.groupValues?.get(1)
        } ?: return null
        val node = runCatching { json.readTree(config) }.getOrNull() ?: return null
        val id = node.path("vdoId").asText()
        val host = node.path("node").path("playlist").asText()
        if (!id.matches(Regex("[A-Za-z0-9_-]+")) || !host.matches(Regex("[A-Za-z0-9.-]+"))) return null
        return "https://$host/$id/playlist.m3u8"
    }

    fun pageUrl(url: String, page: Int): String = if (page <= 1) url else CatalogPagination.path(url, page)

    fun pageUrl(doc: Document, page: Int): String {
        if (page <= 1) return doc.baseUri()
        val next = listing(doc).selectFirst("[data-next-page]")?.attr("data-next-page")?.let { httpUrl(doc.baseUri(), it) }
        if (next != null && Regex("[?&]e-page-[^=]+=\\d+").containsMatchIn(next)) {
            return next.replace(Regex("([?&]e-page-[^=]+=)\\d+")) { it.groupValues[1] + page }
        }
        return pageUrl(doc.baseUri(), page)
    }

    fun hasNext(doc: Document): Boolean = listing(doc).select("a.next[href], a[rel=next][href], [data-next-page]").any { e ->
        val value = if (e.hasAttr("data-next-page")) e.attr("data-next-page") else e.attr("href")
        CatalogPagination.isNext(doc.baseUri(), value)
    }
}
