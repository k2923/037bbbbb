package com.moviedofree

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

@CloudstreamPlugin
class MovieDoFreePlugin : Plugin() {
    override fun load(context: Context) {
        val provider = MovieDoFreeProvider()
        registerMainAPI(provider)
    }
}

class MovieDoFreeProvider : MainAPI() {
    override var mainUrl = "https://moviedofree.com"
    override var name = "Moviedofree"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }

    private val browserSession by lazy { BrowserSession() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }


    private fun MovieCard.response(): SearchResponse = newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val url = MovieDoFreeParser.pageUrl(request.data, page)
        val doc = document(url)
        newHomePageResponse(listOf(HomePageList(request.name, MovieDoFreeParser.cards(doc).map { it.response() })), MovieDoFreeParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search_movie/?keyword=${URLEncoder.encode(query, "UTF-8")}"
        return MovieDoFreeParser.cards(document(url)).map { it.response() }
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = MovieDoFreeParser.detail(document(url))
        return newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val detail = MovieDoFreeParser.detail(document(data, fresh = true))
        val player = app.get(detail.player, referer = data, interceptor = browserSession, cacheTime = 0)
        val streams = MovieDoFreeParser.streams(player.text, player.url)
        for (url in streams) {
            callback(newExtractorLink(name, name, url, ExtractorLinkType.M3U8) {
                referer = player.url
                quality = Qualities.Unknown.value
            })
        }
        return streams.isNotEmpty()
    }
}
