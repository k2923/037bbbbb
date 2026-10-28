package com.providers.network

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

internal data class RelatedTitle(val title: String, val url: String, val poster: String?)

/** Optional detail enrichment. Never turn missing recommendations into a failed detail page. */
internal object RelatedTitles {
    const val LIMIT = 12
    private const val RELATED = ".yarpp-related, .srelacionados, #single_relacionados"
    private const val METADATA = ".sgeneros, .m2f-genres, .movie-tags, .video-tags, .filmaltiaciklama, main .indy-entry-content .anisc-info, main .ez-entry-content"
    private val genreWords = Regex("action|adventure|animation|biography|comedy|crime|documentary|drama|family|fantasy|history|horror|mystery|romance|sci-fi|thriller|war|western|แอคชั่น|ผจญภัย|ตลก|ดราม่า|ครอบครัว|แฟนตาซี|สยองขวัญ|ระทึกขวัญ|โรแมนติก", RegexOption.IGNORE_CASE)

    private fun http(base: String, value: String): String? {
        if (value.isBlank() || value.contains("_files/") || value.startsWith('#')) return null
        return base.toHttpUrlOrNull()?.resolve(value)?.takeIf {
            it.username.isEmpty() && it.password.isEmpty()
        }?.newBuilder()?.fragment(null)?.build()?.toString()
    }
    private fun local(base: String, value: String): String? = http(base, value)?.takeIf {
        it.toHttpUrlOrNull()?.host?.removePrefix("www.") == base.toHttpUrlOrNull()?.host?.removePrefix("www.")
    }
    private fun key(url: String): String? = url.toHttpUrlOrNull()?.let {
        it.host.removePrefix("www.") + (if (it.port in listOf(80, 443)) "" else ":${it.port}") + it.encodedPath.trimEnd('/') + (it.encodedQuery?.let { q -> "?$q" } ?: "")
    }

    fun inline(doc: Document): List<RelatedTitle> = doc.select(RELATED).flatMap { it.select("a[href]:has(img)") }.mapNotNull { a ->
        val url = local(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
        val path = url.toHttpUrlOrNull()!!.encodedPath
        if (path == "/" || listOf("/genre/", "/genres/", "/category/", "/tag/", "/watch/", "/video/", "/ep/").any(path::startsWith)) return@mapNotNull null
        val image = a.selectFirst("img")
        val title = a.attr("title").ifBlank { a.text() }.ifBlank { image?.attr("alt").orEmpty() }.trim()
        if (title.isBlank()) return@mapNotNull null
        val poster = image?.let { img -> listOf("data-src", "data-lazy-src", "data-original", "src").firstNotNullOfOrNull { http(doc.baseUri(), img.attr(it)) } }
        RelatedTitle(title, url, poster)
    }.distinctBy { key(it.url) }

    fun genres(doc: Document): List<String> {
        // Neko's genre badges are in the detail section; its footer lists every genre.
        val nodes = doc.select(METADATA).flatMap { it.select("a[href]") } + doc.select("main section.relative a[href*=/genre/]")
        return nodes.filter { it.closest("nav, aside, footer, header, .sidebar, .tagcloud") == null }.mapNotNull { a ->
            val url = local(doc.baseUri(), a.attr("href")) ?: return@mapNotNull null
            val path = url.toHttpUrlOrNull()!!.pathSegments
            if (path.firstOrNull() in listOf("genre", "genres") ||
                (path.firstOrNull() == "category" && genreWords.containsMatchIn(path.getOrNull(1).orEmpty()))) url else null
        }.distinct()
    }

    fun searchQuery(title: String): String? {
        val cleaned = title.substringBefore('(').substringBefore('[')
            .replace(Regex("(?i)\\b(?:season|movie|ova|episode|ep)\\b.*|(?:ตอนที่|ภาค|พากย์ไทย|ซับไทย|เดอะมูฟวี่).*"), "")
            .replace(Regex("\\b(?:19|20)\\d{2}\\b"), "").trim()
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val count = if (words.firstOrNull()?.lowercase() in listOf("the", "a", "an")) 3 else 2
        return words.take(count).joinToString(" ").takeIf { it.length >= 4 }
    }

    suspend fun <T> load(
        currentUrl: String, title: String, direct: List<T>, genres: List<String>,
        urlOf: (T) -> String, titleOf: (T) -> String,
        byGenre: suspend (String) -> List<T>, bySearch: suspend (String) -> List<T>,
        timeoutMs: Long = 1500,
    ): List<T> {
        fun clean(items: List<T>) = items.filter {
            local(currentUrl, urlOf(it)) != null && key(urlOf(it)) != key(currentUrl) && titleOf(it).isNotBlank()
        }.distinctBy { key(urlOf(it)) }.take(LIMIT)
        clean(direct).takeIf { it.isNotEmpty() }?.let { return it }
        return try {
            withTimeoutOrNull(timeoutMs) {
                val genre = genres.firstOrNull { local(currentUrl, it) != null }
                if (genre != null) clean(byGenre(genre)) else {
                    val query = searchQuery(title) ?: return@withTimeoutOrNull emptyList()
                    clean(bySearch(query).filter { titleOf(it).contains(query, ignoreCase = true) })
                }
            }.orEmpty()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            emptyList()
        }
    }
}

internal suspend fun MainAPI.relatedTitles(
    doc: Document?, currentUrl: String, title: String, anime: Boolean = false,
    category: suspend (String) -> List<SearchResponse>,
    lookup: suspend (String) -> List<SearchResponse> = { search(it).orEmpty() },
): List<SearchResponse> {
    val direct = doc?.let(RelatedTitles::inline).orEmpty().map { card ->
        val movie = card.url.toHttpUrlOrNull()?.encodedPath?.startsWith("/movies/") == true ||
            Regex("(?i)\\bmovie\\b|เดอะมูฟวี่").containsMatchIn(card.title)
        val response = if (anime) newAnimeSearchResponse(card.title, card.url, if (movie) TvType.AnimeMovie else TvType.Anime) {}
        else if (card.url.contains("/tvshows/")) newTvSeriesSearchResponse(card.title, card.url, TvType.TvSeries) {}
        else newMovieSearchResponse(card.title, card.url, TvType.Movie) {}
        response.apply { posterUrl = card.poster; posterHeaders = mapOf("Referer" to currentUrl) }
    }
    return RelatedTitles.load(currentUrl, title, direct, doc?.let(RelatedTitles::genres).orEmpty(),
        { it.url }, { it.name }, category, lookup)
}
