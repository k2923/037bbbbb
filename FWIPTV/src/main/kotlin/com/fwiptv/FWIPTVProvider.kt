package com.fwiptv

import android.content.Context
import com.providers.network.withNativePlayback
import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

@CloudstreamPlugin
class FWIPTVPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = FWIPTVProvider()
        registerMainAPI(provider)
    }
}

class FWIPTVProvider : MainAPI() {
    override var mainUrl = "https://fwip-tv.com"
    override var name = "FWIP TV"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override val mainPage = SiteCategories.entries.map { (path, title) -> mainPage(mainUrl + path, title) }
    private val browserSession by lazy { BrowserSession() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("FWIP TV ไม่พร้อมใช้งาน (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun MovieCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = document(FWIPParser.pageUrl(request.data, page))
        return newHomePageResponse(listOf(HomePageList(request.name, FWIPParser.cards(doc).map { it.response() })), FWIPParser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> = FWIPParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = FWIPParser.detail(document(url))
        return if (detail.series) newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, detail.episodes.map { ep ->
            newEpisode(ep.url) { name = ep.title; episode = ep.number; season = ep.season; posterUrl = ep.poster }
        }) { posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags }
        else newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags
        }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val embed = FWIPParser.embed(document(data, fresh = true))
            ?: throw ErrorLoadingException("FWIP TV ไม่พบตัวเล่นของเรื่องนี้")
        var found = false
        val player = Player123.request(embed, data)
        if (player != null) {
            suspend fun play(id: String, label: String) {
                val response = app.get("${player.api}/videos/$id/play", referer = embed,
                    headers = player.headers, interceptor = browserSession, cacheTime = 0)
                if (response.code !in 200..299) throw ErrorLoadingException("FWIP TV ตัวเล่นไม่พร้อมใช้งาน (HTTP ${response.code})")
                val result = Player123.result(response.text, player.api)
                result.source?.let { source ->
                    callback(newExtractorLink(name, "$name $label", source.url,
                        if (source.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                        referer = embed
                        headers = mapOf("Origin" to player.headers.getValue("x-player-origin"))
                        quality = Qualities.Unknown.value
                    })
                    found = true
                }
                result.backup?.let { backup ->
                    try { loadExtractor(backup, "", subtitleCallback) { callback(it); found = true } }
                    catch (error: Exception) { if (error is CancellationException) throw error }
                }
            }
            var failure: Exception? = null
            try { play(player.id, "ตัวเล่นหลัก") }
            catch (error: Exception) { if (error is CancellationException) throw error; failure = error }
            try {
                val info = app.get("${player.api}/videos/${player.id}/thai-version/info", referer = embed,
                    headers = player.headers, interceptor = browserSession, cacheTime = 0)
                if (info.code in 200..299) Player123.thaiVersion(info.text)?.let { (id, label) ->
                    if (id != player.id) play(id, label)
                }
            } catch (error: Exception) { if (error is CancellationException) throw error }
            if (found) return true
            if (failure != null) throw ErrorLoadingException("FWIP TV โหลด API ตัวเล่นไม่ได้ (${failure.message ?: failure.javaClass.simpleName})")
            throw ErrorLoadingException("FWIP TV ยังไม่รองรับรูปแบบสตรีมนี้ หรือลิงก์สำรองไม่พร้อมใช้งาน")
        }
        // Other hosts remain delegated to the app's registered extractors.
        try {
            loadExtractor(embed, data, subtitleCallback) { found = true; callback(it) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!found) throw ErrorLoadingException("FWIP TV โหลดโฮสต์วิดีโอไม่ได้ ลองเปิดเรื่องนี้ในเว็บเพื่อตรวจสอบตัวเล่น")
        }
        if (!found) throw ErrorLoadingException("FWIP TV ยังไม่มีตัวอ่านที่เล่นโฮสต์วิดีโอนี้ได้ (${java.net.URI(embed).host})")
        return true
    }
}
