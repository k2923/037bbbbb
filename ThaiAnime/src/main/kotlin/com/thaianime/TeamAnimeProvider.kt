package com.thaianime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.providers.network.withNativePlayback
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document
import kotlin.coroutines.cancellation.CancellationException

internal class TeamAnimeProvider(private val site: TeamAnimeSite) : MainAPI() {
    private val parser = TeamAnimeParser(site)
    override var mainUrl = parser.base
    override var name = site.label
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = parser.categories.map { (path, title) -> mainPage(mainUrl + path, title) }
    private suspend fun document(url: String, referer: String = "$mainUrl/", fresh: Boolean = false): Document {
        val response = if (fresh) app.get(url, referer = referer, interceptor = AnimeNetwork.browserSession, cacheTime = 0)
            else app.get(url, referer = referer, interceptor = AnimeNetwork.browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        if (fresh) site.validatePlaybackPage(url, response.url)
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun AnimeCard.response(): SearchResponse = newAnimeSearchResponse(title, url, if (movie) TvType.AnimeMovie else TvType.Anime) {
        posterUrl = poster
        posterHeaders = mapOf("Referer" to "$mainUrl/")
        addDubStatus(if (title.contains("พากย์ไทย")) DubStatus.Dubbed else DubStatus.Subbed)
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val doc = document(parser.pageUrl(request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, parser.cards(doc).map { it.response() })), parser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> = parser.cards(document(parser.searchUrl(query))).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = parser.detail(document(url))
        if (detail.movie) return newMovieLoadResponse(detail.title, url, TvType.AnimeMovie, url) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
        }
        return newAnimeLoadResponse(detail.title, url, TvType.Anime) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
            detail.episodes.groupBy { if (it.dubbed) DubStatus.Dubbed else DubStatus.Subbed }.forEach { (dub, episodes) ->
                addEpisodes(dub, episodes.map { ep -> newEpisode(ep.url) { name = ep.title; episode = ep.number; season = ep.season } })
            }
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return withNativePlayback(callback) { emit ->
            val emitted = mutableSetOf<String>()
            val unique: (ExtractorLink) -> Unit = { if (emitted.add(it.url)) emit(it) }
            suspend fun resolve(doc: Document) {
                TeamAnimePlayer.resolve(doc,
                get = { url, referer -> document(url, referer, fresh = true) },
                extract = { source, referer -> loadExtractor(source.url, referer, subtitleCallback, unique); Unit },
                emit = { stream -> unique(newExtractorLink(name, "$name - ${stream.label}", stream.url, if (stream.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                    referer = stream.referer; quality = Qualities.Unknown.value
                }) },
            )
            }
            val doc = document(data, fresh = true)
            if (TeamAnimePlayer.sources(doc).isNotEmpty()) resolve(doc)
            else {
                // Movies have a separate /watch/ page; never invent the episode URL from a title.
                val detail = parser.detail(doc)
                if (detail.movie) detail.episodes.forEach { episode ->
                    try { resolve(document(episode.url, data, fresh = true)) } catch (error: Exception) {
                        if (error is CancellationException) throw error
                    }
                }
            }
            if (emitted.isEmpty()) throw ErrorLoadingException("$name ตัวเล่นที่เว็บระบุใช้ไม่ได้หรือยังไม่รองรับ ลองเลือกตัวเล่นสำรองอื่น")
            true
        }
    }
}
