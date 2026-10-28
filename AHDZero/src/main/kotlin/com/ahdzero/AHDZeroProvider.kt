package com.ahdzero

import android.content.Context
import com.providers.network.SampleArchive
import com.providers.samples.bundledSamples
import com.providers.network.withNativePlayback
import com.providers.network.relatedTitles
import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@CloudstreamPlugin
class AHDZeroPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = AHDZeroProvider()
        registerMainAPI(provider)
        registerMainAPI(AHDZeroProvider(sampleMode = true))
    }
}

class AHDZeroProvider(private val sampleMode: Boolean = false) : MainAPI() {
    override var mainUrl = "https://ahdzero.com"
    override var name = "AHDZero" + if (sampleMode) " (${SampleArchive.LABEL})" else ""
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = if (sampleMode) listOf(mainPage(mainUrl, SampleArchive.NOTICE)) else SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }
    private val browserSession by lazy { BrowserSession() }
    private val sampleCards: List<AnimeCard> by lazy {
        val details = bundledSamples.documents(mainUrl, "detail").map { doc ->
            val detail = AHDZeroParser.detail(doc)
            AnimeCard(detail.title, doc.baseUri(), detail.poster, detail.movie)
        }
        (details + bundledSamples.documents(mainUrl, "listing").flatMap { AHDZeroParser.cards(it) }).distinctBy { it.url }
    }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        if (sampleMode) bundledSamples.document(url)?.let { return it }
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("เว็บปฏิเสธการเข้าถึง (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    private suspend fun AnimeCard.response(): SearchResponse = newAnimeSearchResponse(title, url, if (movie) TvType.AnimeMovie else TvType.Anime) {
        posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/")
        addDubStatus(title.contains("พากย์ไทย"))
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        if (sampleMode) return@withPermit newHomePageResponse(
            listOf(HomePageList(SampleArchive.NOTICE, if (page == 1) sampleCards.map { it.response() } else emptyList())), false
        )
        val doc = document(AHDZeroParser.pageUrl(request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, AHDZeroParser.cards(doc).map { it.response() })), AHDZeroParser.hasNext(doc, page))
    }

    override suspend fun search(query: String): List<SearchResponse> = if (sampleMode) sampleCards.filter { it.title.contains(query.trim(), ignoreCase = true) }.map { it.response() } else AHDZeroParser.cards(document("$mainUrl/search/results?q=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }

    override suspend fun load(url: String): LoadResponse {
        val doc = document(url)
        val detail = AHDZeroParser.detail(doc)
        val related = relatedTitles(doc, url, detail.title, anime = true, category = { categoryUrl ->
            (if (sampleMode) bundledSamples.document(categoryUrl) else document(categoryUrl))?.let { AHDZeroParser.cards(it).map { card -> card.response() } }.orEmpty()
        })
        if (detail.movie) {
            val episode = detail.episodes.firstOrNull() ?: throw ErrorLoadingException("ยังไม่มีตอนให้รับชม")
            return newMovieLoadResponse(detail.title, url, TvType.AnimeMovie, episode.url) {
                recommendations = related; posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot
            }
        }
        return newAnimeLoadResponse(detail.title, url, TvType.Anime) {
            recommendations = related; posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot
            addEpisodes(if (detail.title.contains("พากย์ไทย")) DubStatus.Dubbed else DubStatus.Subbed,
                detail.episodes.map { ep -> newEpisode(ep.url) { name = ep.title; episode = ep.number } })
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val embed = AHDZeroParser.embed(document(data, fresh = true)) ?: return false
        val player = app.get(embed, referer = data, interceptor = browserSession, cacheTime = 0)
        val stream = AHDZeroParser.stream(player.document.apply { setBaseUri(player.url) }) ?: return false
        callback(newExtractorLink(name, name, stream, ExtractorLinkType.M3U8) { referer = player.url; quality = Qualities.Unknown.value })
        return true
    }
}
