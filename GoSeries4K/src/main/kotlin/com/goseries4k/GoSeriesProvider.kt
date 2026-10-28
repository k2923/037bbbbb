package com.goseries4k

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import com.providers.network.BrowserSession
import com.providers.network.withNativePlayback
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

@CloudstreamPlugin
class GoSeriesPlugin : Plugin() {
    override fun load(context: Context) { registerMainAPI(GoSeriesProvider()) }
}

class GoSeriesProvider : MainAPI() {
    override var mainUrl = GoSeriesParser.BASE
    override var name = "GoSeries4K"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    private val browserSession by lazy { BrowserSession() }
    override val mainPage = listOf(
        mainPage("$mainUrl/", "ซีรีส์ล่าสุด"),
        mainPage("$mainUrl/category/ดู-ซีรี่ย์จีน-ออนไลน์/", "ซีรีส์จีน"),
        mainPage("$mainUrl/category/ดูซีรี่ย์เกาหลี-ออนไลน์/", "ซีรีส์เกาหลี"),
        mainPage("$mainUrl/cat_category/ดูซีรี่ย์จีนพากย์ไทย/", "ซีรีส์จีนพากย์ไทย"),
    )

    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        require(GoSeriesParser.local(url) != null) { "URL ไม่ใช่ GoSeries4K" }
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0)
            else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    private fun SeriesCard.response() = newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
        posterUrl = poster
        posterHeaders = mapOf("Referer" to "$mainUrl/")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val doc = document(GoSeriesParser.pageUrl(request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, GoSeriesParser.cards(doc).map { it.response() })), GoSeriesParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> =
        GoSeriesParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }

    override suspend fun load(url: String): LoadResponse {
        val doc = document(url)
        val detail = GoSeriesParser.detail(doc)
        if (detail.episodes.isEmpty()) throw ErrorLoadingException("เว็บยังไม่มีปุ่มตอนที่รองรับสำหรับเรื่องนี้")
        val episodes = detail.episodes.map { ep ->
            newEpisode(SeriesPlayback(url, ep.ids).encode()) {
                episode = ep.number
                name = listOf("EP ${ep.number}", ep.audio).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }
        return newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, episodes) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url)
            plot = detail.plot; year = detail.year
            recommendations = GoSeriesParser.cards(doc).filter { it.url != GoSeriesParser.local(url) }.take(12).map { it.response() }
        }
    }

    private suspend fun player(page: String, id: String): String? {
        // Avoid another WebView for the default episode, but never reuse the iframe for a different episode.
        try {
            val doc = document(page, fresh = true)
            val available = GoSeriesParser.detail(doc).episodes.any { id in it.ids }
            if (!available) return null
            GoSeriesParser.initialPlayer(doc, id)?.let { return it }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // The device WebView may have a different session. It still must find the exact real button.
        }
        val selected = AtomicReference<String?>(null)
        WebViewResolver(
            interceptUrl = Regex("^$"),
            additionalUrls = listOf(Regex("^https://torbo007\\.com/")),
            userAgent = null, useOkhttp = false, timeout = 8_000L,
            script = EpisodeSelection.script(id),
            scriptCallback = { snapshot -> EpisodeSelection.selected(id, snapshot)?.let { selected.set(it) } },
        ).resolveUsingWebView(page, referer = "$mainUrl/", requestCallBack = { selected.get() != null })
        return selected.get()
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit ->
            val playback = SeriesPlayback.decode(data)
            val emitted = mutableSetOf<String>()
            for (id in playback.ids) {
                try {
                    val embed = player(playback.page, id) ?: continue
                    val request = WebViewResolver(
                        interceptUrl = TorboPlayer.capturePattern,
                        userAgent = null, useOkhttp = false, timeout = 8_000L,
                    ).resolveUsingWebView(embed, referer = playback.page).first ?: continue
                    val media = request.url.toString()
                    if (request.method == "GET" && TorboPlayer.media(media, embed) && emitted.add(media)) {
                        emit(newExtractorLink(name, "$name · Torbo", media, ExtractorLinkType.M3U8) {
                            referer = request.header("Referer") ?: embed
                            headers = request.headers.toMap()
                            quality = Qualities.Unknown.value
                        })
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                }
            }
            emitted.isNotEmpty()
        }
}
