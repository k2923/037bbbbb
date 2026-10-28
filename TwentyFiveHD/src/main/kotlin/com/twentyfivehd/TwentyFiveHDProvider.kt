package com.twentyfivehd

import android.content.Context
import com.providers.network.withNativePlayback
import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

@CloudstreamPlugin
class TwentyFiveHDPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = TwentyFiveHDProvider()
        registerMainAPI(provider)
    }
}

class TwentyFiveHDProvider : MainAPI() {
    override var mainUrl = "https://25-hd.com"
    override var name = "25-HD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }

    private val browserSession by lazy { BrowserSession() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    private fun MovieCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val url = TwentyFiveParser.pageUrl(request.data, page)
        val doc = document(url)
        newHomePageResponse(listOf(HomePageList(request.name, TwentyFiveParser.cards(doc).map { it.response() })), TwentyFiveParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        return TwentyFiveParser.cards(document(url)).map { it.response() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = TwentyFiveParser.detail(document(url))
        val catalog = resolver().catalog(detail.embed, url)
        if (catalog.series) {
            val episodes = catalog.episodes.map {
                newEpisode(PlaybackContext(it.url, url).encode()) { name = it.name; season = it.season; episode = it.number }
            }
            return newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, episodes) {
                posterUrl = detail.poster; plot = detail.plot; tags = detail.tags
            }
        }
        return newMovieLoadResponse(detail.title, url, TvType.Movie, PlaybackContext(detail.embed, url).encode()) {
            posterUrl = detail.poster; plot = detail.plot; tags = detail.tags
        }
    }

    private fun resolver() = ZmdbResolver { url, referer, headers ->
        val response = app.get(url, referer = referer, headers = headers, interceptor = browserSession, cacheTime = 0)
        WebPage(response.url, response.text, response.code)
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? =
        HlsRouting.decode(extractorLink.extractorData)?.let { HlsRoutingInterceptor(it) }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val resolver = resolver()
        var found = false
        val emit: (ExtractorLink) -> Unit = { found = true; callback(it) }
        val playback = PlaybackContext.decode(data)
        for (stream in resolver.resolve(playback.player, "$mainUrl/")) {
            try {
                if (stream.hls) {
                    // Steering is optional. A failed steering request must not discard a playable master.
                    val routing = try {
                        val master = app.get(stream.url, referer = stream.referer, cacheTime = 0)
                        HlsRouting.steeringUrl(stream.url, master.text)?.let { routingUrl ->
                            HlsRouting.fromManifest(stream.url, app.get(routingUrl, referer = stream.referer, cacheTime = 0).text)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                    emit(newExtractorLink(name, "$name - ${stream.label}", stream.url, ExtractorLinkType.M3U8) {
                        referer = stream.referer
                        extractorData = routing?.encode()
                        quality = Qualities.Unknown.value
                    })
                } else loadExtractor(stream.url, stream.referer, subtitleCallback, emit)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
        return found
    }
}
