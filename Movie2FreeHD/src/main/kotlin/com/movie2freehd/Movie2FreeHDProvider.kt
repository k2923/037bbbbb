package com.movie2freehd

import android.content.Context
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
import kotlin.coroutines.cancellation.CancellationException

@CloudstreamPlugin
class Movie2FreeHDPlugin : Plugin() {
    override fun load(context: Context) {
        val provider = Movie2FreeHDProvider()
        registerMainAPI(provider)
    }
}

class Movie2FreeHDProvider : MainAPI() {
    override var mainUrl = "https://movie2freehd.com"
    override var name = "Movie2FreeHD"
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
        if (response.code !in 200..299) throw ErrorLoadingException("Movie2FreeHD ไม่พร้อมใช้งาน (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun MovieCard.response(): SearchResponse = if (series) {
        newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    } else {
        newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/") }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        val doc = document(Movie2FreeHDParser.pageUrl(request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, Movie2FreeHDParser.cards(doc).map { it.response() })), Movie2FreeHDParser.hasNext(doc))
    }
    override suspend fun search(query: String): List<SearchResponse> =
        Movie2FreeHDParser.cards(document("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}")).map { it.response() }

    override suspend fun load(url: String): LoadResponse {
        val doc = document(url)
        val detail = Movie2FreeHDParser.detail(doc)
        val related = relatedTitles(doc, url, detail.title, category = { categoryUrl ->
            Movie2FreeHDParser.cards(document(categoryUrl)).map { it.response() }
        })
        return if (detail.series) {
            newTvSeriesLoadResponse(detail.title, url, TvType.TvSeries, detail.episodes.map { ep ->
                newEpisode("$url#season=${ep.season}&episode=${ep.number}") { name = ep.title; season = ep.season; episode = ep.number }
            }) { recommendations = related; posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags }
        } else newMovieLoadResponse(detail.title, url, TvType.Movie, url) {
            recommendations = related; posterUrl = detail.poster; posterHeaders = mapOf("Referer" to url); plot = detail.plot; tags = detail.tags
        }
    }
    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? =
        MeeRouting.decode(extractorLink.extractorData)?.let { MeeRoutingInterceptor(it) }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val pageUrl = data.substringBefore('#')
        val doc = document(pageUrl, fresh = true)
        val episode = Regex("^season=(\\d+)&episode=(\\d+)$").matchEntire(data.substringAfter('#', ""))
        val players = mutableListOf<PlayerLink>()
        if (episode != null) {
            players += Movie2FreeHDParser.detail(doc).episodes.firstOrNull {
                it.season == episode.groupValues[1].toInt() && it.number == episode.groupValues[2].toInt()
            }?.players.orEmpty()
        } else for (option in Movie2FreeHDParser.options(doc)) {
            try {
                val response = app.post("$mainUrl/wp-admin/admin-ajax.php", referer = pageUrl, interceptor = browserSession, cacheTime = 0,
                    data = mapOf("action" to "doo_player_ajax", "post" to option.post, "nume" to option.number, "type" to option.type))
                if (response.code == 200) Movie2FreeHDParser.embed(response.text, pageUrl)?.let { players += PlayerLink(it, option.label) }
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
        if (players.isEmpty()) throw ErrorLoadingException(
            if (episode == null && Movie2FreeHDParser.options(doc).isEmpty())
                "Movie2FreeHD หน้าเว็บเรื่องนี้ยังไม่มีตัวเล่นวิดีโอ"
            else "Movie2FreeHD ตัวเล่นไม่คืนลิงก์ หรือยังไม่มีลิงก์สำหรับตอนนี้"
        )
        val resolver = MeePlayerResolver(
            get = { url, ref -> app.get(url, referer = ref, interceptor = browserSession, cacheTime = 0).let { PlayerPage(it.url, it.text, it.code) } },
            post = { url, ref, payload -> app.post(url, referer = ref, json = payload, interceptor = browserSession, cacheTime = 0).let { PlayerPage(it.url, it.text, it.code) } }
        )
        val emitted = mutableSetOf<String>()
        val emit: (ExtractorLink) -> Unit = { if (emitted.add(it.url)) callback(it) }
        for (player in Movie2FreeHDParser.mergePlayers(players)) {
            try {
                if (MeePlayerResolver.isMeePlayer(player.url)) {
                    val stream = resolver.resolve(player.url, pageUrl) ?: continue
                    stream.subtitle?.let { subtitleCallback(newSubtitleFile("คำบรรยาย", it)) }
                    emit(newExtractorLink(name, "$name - ${player.label}", stream.url, ExtractorLinkType.M3U8) {
                        referer = stream.referer; extractorData = stream.routing; quality = Qualities.Unknown.value
                    })
                } else loadExtractor(player.url, pageUrl, subtitleCallback, emit)
            } catch (error: Exception) { if (error is CancellationException) throw error }
        }
        return emitted.isNotEmpty()
    }
}
