package com.twentyfourhd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal data class CatalogPage(val text: String, val status: Int, val totalPages: Int? = null)
internal data class CatalogListing(val cards: List<SiteCard>, val hasNext: Boolean)

/** Public WordPress data from the original site; playback fields are requested fresh on play. */
internal class TwentyFourCatalog(
    private val base: String,
    private val get: suspend (url: String, fresh: Boolean) -> CatalogPage,
) {
    private val json = ObjectMapper()
    private val terms = ConcurrentHashMap<String, Int>()
    private val episodeNumber = Regex("(?:EP\\.?|ตอนที่)\\s*(\\d+)", RegexOption.IGNORE_CASE)

    private suspend fun read(path: String, params: Map<String, String>, fresh: Boolean = false): Pair<JsonNode, CatalogPage> {
        val query = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val page = get("${base.trimEnd('/')}/wp-json/wp/v2/$path?$query", fresh)
        check(page.status in 200..299) { "24HD API ตอบ HTTP ${page.status}" }
        val node = json.readTree(page.text)
        check(node != null && node.isArray) { "24HD API ไม่ส่งรายการที่รองรับ" }
        return node to page
    }

    private fun url(value: String): String? = TwentyFourHDParser.httpUrl(base, value)
    private fun text(value: String): String = Jsoup.parse(value).text().trim()
    private fun poster(post: JsonNode): String? = url(post.path("_embedded").path("wp:featuredmedia").path(0).path("source_url").asText())

    suspend fun listing(source: String, page: Int, search: String? = null): CatalogListing {
        val params = linkedMapOf("per_page" to "24", "page" to page.toString(), "_embed" to "wp:featuredmedia")
        if (search != null) params["search"] = search
        val path = URI(source).path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        if (path.isNotEmpty()) {
            check(path.size == 2 && path[0] in listOf("category", "tag")) { "24HD API ไม่รองรับหมวดนี้" }
            val taxonomy = if (path[0] == "category") "categories" else "tags"
            val key = "$taxonomy/${path[1]}"
            val id = terms[key] ?: read(taxonomy, mapOf("slug" to path[1])).first
                .firstOrNull { it.path("id").asInt() > 0 }?.path("id")?.asInt()
                ?.also { terms[key] = it } ?: error("24HD API ไม่พบหมวดที่เลือก")
            params[taxonomy] = id.toString()
        }
        val (posts, response) = read("posts", params)
        val cards = posts.mapNotNull { post ->
            val link = url(post.path("link").asText()) ?: return@mapNotNull null
            val title = text(post.path("title").path("rendered").asText())
            if (title.isBlank()) return@mapNotNull null
            SiteCard(title, link, poster(post), post.path("acf").path("ep_list").size() > 0)
        }.distinctBy { it.url }
        return CatalogListing(cards, response.totalPages?.let { page < it } ?: (posts.size() == 24))
    }

    suspend fun detail(source: String, fresh: Boolean = false): SiteDetail {
        val slug = URI(source).path.trim('/').substringAfterLast('/')
        check(slug.isNotBlank()) { "ไม่พบชื่อหน้า 24HD" }
        val posts = read("posts", mapOf("slug" to slug, "_embed" to "wp:featuredmedia"), fresh).first
        val post = posts.firstOrNull {
            runCatching { URI(it.path("link").asText()).path.trim('/') == URI(source).path.trim('/') }.getOrDefault(false)
        } ?: error("24HD API ไม่พบเรื่องที่เลือก")
        val acf = post.path("acf")
        val title = text(post.path("title").path("rendered").asText())
        check(title.isNotBlank()) { "24HD API ไม่ส่งชื่อเรื่อง" }
        val episodes = acf.path("ep_list").mapNotNull { ep ->
            val label = text(ep.path("title").asText())
            val n = episodeNumber.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val players = listOf("link", "link_2").mapNotNull { url(ep.path(it).asText()) }.distinct()
            if (players.isEmpty()) null else SiteEpisode(n, label, players)
        }.groupBy { it.number }.values.map { rows ->
            rows.first().copy(players = rows.flatMap { it.players }.distinct())
        }.sortedBy { it.number }
        check(acf.path("ep_list").size() == 0 || episodes.isNotEmpty()) { "24HD ยังไม่มีลิงก์ตอนที่รองรับ" }
        val players = listOf("file", "file_2").mapNotNull { url(acf.path(it).asText()) }.distinct()
        check(episodes.isNotEmpty() || players.isNotEmpty()) { "24HD API ไม่ส่งลิงก์ที่รองรับ" }
        return SiteDetail(title, poster(post), text(post.path("content").path("rendered").asText()).ifBlank {
            text(acf.path("brief").asText())
        }.takeIf { it.isNotBlank() }, episodes,
            players,
            text(acf.path("sound").asText()).takeIf { it.isNotBlank() })
    }
}
