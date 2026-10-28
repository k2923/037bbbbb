package com.twentyfourhd

import android.content.Context
import com.providers.network.SampleArchive
import com.providers.samples.bundledSamples
import com.providers.network.withNativePlayback
import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CancellationException

@CloudstreamPlugin
class TwentyFourHDPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = TwentyFourHDProvider()
        registerMainAPI(provider)
        registerMainAPI(TwentyFourHDProvider(sampleMode = true))
    }
}

class TwentyFourHDProvider(private val sampleMode: Boolean = false) : MainAPI() {
    override var mainUrl = "https://www.24hd.today"
    override var name = "24HD" + if (sampleMode) " (${SampleArchive.LABEL})" else ""
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = if (sampleMode) listOf(mainPage(mainUrl, SampleArchive.NOTICE)) else SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }
    private val browserSession by lazy { BrowserSession() }
    private val catalog by lazy { TwentyFourCatalog(mainUrl) { url, fresh ->
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0)
            else app.get(url, interceptor = browserSession)
        CatalogPage(response.text, response.code, response.headers["X-WP-TotalPages"]?.toIntOrNull())
    } }
    private suspend fun <T> apiOrHtml(api: suspend () -> T, html: suspend () -> T): T {
        try { return api() } catch (error: Exception) {
            if (error is CancellationException) throw error
            try { return html() } catch (fallback: Exception) {
                if (fallback is CancellationException) throw fallback
                throw ErrorLoadingException("${error.message}; ${fallback.message}")
            }
        }
    }
    private suspend fun detail(url: String, fresh: Boolean = false): SiteDetail =
        if (sampleMode) TwentyFourHDParser.detail(document(url, fresh))
        else apiOrHtml({ catalog.detail(url, fresh) }, { TwentyFourHDParser.detail(document(url, fresh)) })
    private val sampleCards: List<SiteCard> by lazy {
        val details = bundledSamples.documents(mainUrl, "detail").map { doc ->
            val detail = TwentyFourHDParser.detail(doc)
            SiteCard(detail.title, doc.baseUri(), detail.poster, detail.episodes.isNotEmpty())
        }
        (details + bundledSamples.documents(mainUrl, "listing").flatMap { TwentyFourHDParser.cards(it) }).distinctBy { it.url }
    }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        if (sampleMode) bundledSamples.document(url)?.let { return it }
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("เว็บปฏิเสธการเข้าถึง (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    private fun SiteCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        if (sampleMode) return@withPermit newHomePageResponse(
            listOf(HomePageList(SampleArchive.NOTICE, if (page == 1) sampleCards.map { it.response() } else emptyList())), false
        )
        val listing = apiOrHtml({ catalog.listing(request.data, page) }, {
            val first = document(request.data)
            val doc = if (page <= 1) first else document(TwentyFourHDParser.pageUrl(first, page))
            CatalogListing(TwentyFourHDParser.cards(doc), TwentyFourHDParser.hasNext(doc))
        })
        newHomePageResponse(listOf(HomePageList(request.name, listing.cards.map { it.response() })), listing.hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> = if (sampleMode)
        sampleCards.filter { it.title.contains(query.trim(), ignoreCase = true) }.map { it.response() }
    else apiOrHtml({ catalog.listing(mainUrl, 1, query).cards }, {
        TwentyFourHDParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"))
    }).map { it.response() }

    override suspend fun load(url: String): LoadResponse {
        val detail = detail(url)
        if (detail.episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, detail.episodes.map { ep ->
                // Store the page and episode number, then refresh its players when playback starts.
                newEpisode("$url#episode=${ep.number}") { name = ep.title; episode = ep.number; season = 1 }
            }) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot }
        }
        return newMovieLoadResponse(detail.title, url, TvType.Movie, url) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val page = data.substringBefore('#')
        val detail = detail(page, fresh = true)
        val episode = data.substringAfter("#episode=", "").toIntOrNull()
        val players = if (episode != null) detail.episodes.firstOrNull { it.number == episode }?.players.orEmpty() else detail.players
        var found = false
        for ((index, url) in players.withIndex()) {
            // A broken backup must not discard links already found from another player.
            val result = runCatching {
                val response = app.get(url, referer = page, interceptor = browserSession, cacheTime = 0)
                TwentyFourHDParser.stream(response.document.apply { setBaseUri(response.url) })?.let { stream ->
                    callback(newExtractorLink(name, listOfNotNull(detail.audio, if (index == 0) "ตัวเล่นหลัก" else "สำรอง $index").joinToString(" - "), stream, ExtractorLinkType.M3U8) {
                        referer = response.url; quality = Qualities.Unknown.value
                    })
                    true
                } ?: false
            }.getOrElse { error -> if (error is CancellationException) throw error; false }
            found = found || result
        }
        return found
    }
}
