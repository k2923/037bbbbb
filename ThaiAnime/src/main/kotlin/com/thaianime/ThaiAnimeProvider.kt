package com.thaianime

import android.content.Context
import com.providers.network.SampleArchive
import com.providers.samples.bundledSamples
import com.providers.network.withBrowserFallback
import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

internal object AnimeNetwork {
    val browserSession by lazy { BrowserSession() }
}

@CloudstreamPlugin
class ThaiAnimePlugin : Plugin() {
    override fun load(context: Context) {
        (Site.enabled.flatMap { listOf(ThaiAnimeProvider(it), ThaiAnimeProvider(it, sampleMode = true)) } + TeamAnimeSite.values().map { TeamAnimeProvider(it) })
            .sortedBy { it.name.lowercase() }.forEach { registerMainAPI(it) }
    }
}

internal class ThaiAnimeProvider(private val site: Site, private val sampleMode: Boolean = false) : MainAPI() {
    override var mainUrl = site.base
    override var name = site.label + if (sampleMode) " (${SampleArchive.LABEL})" else ""
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = if (sampleMode) listOf(mainPage(mainUrl, SampleArchive.NOTICE)) else AnimeCategories.homeEntries.getValue(site).map { (path, title) -> mainPage(mainUrl + path, title) }
    private val browserSession get() = AnimeNetwork.browserSession
    private val sampleCards: List<AnimeCard> by lazy {
        val details = bundledSamples.documents(mainUrl, "detail").map { doc ->
            val detail = AnimeParser.detail(site, doc)
            AnimeCard(detail.title, doc.baseUri(), detail.poster, detail.movie)
        }
        (details + bundledSamples.documents(mainUrl, "listing").flatMap { AnimeParser.cards(site, it) }).distinctBy { it.url }
    }
    private suspend fun document(url: String, fresh: Boolean = false, referer: String = mainUrl): org.jsoup.nodes.Document {
        if (sampleMode) bundledSamples.document(url)?.let { return it }
        val wakuPlayback = site == Site.WAKU && fresh
        val headers = when {
            wakuPlayback && WakuPlayer.playerPattern.matches(url) -> mapOf("User-Agent" to WakuPlayer.mediaUserAgent)
            wakuPlayback -> browserSession.getCookieHeaders(url).toMap()
            else -> emptyMap()
        }
        // Reuse existing website cookies without launching a verification flow.
        val interceptor = if (wakuPlayback) null else browserSession
        val response = if (fresh) app.get(url, referer = referer, headers = headers, interceptor = interceptor, cacheTime = 0) else app.get(url, referer = referer, headers = headers, interceptor = interceptor)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }
    private fun AnimeCard.response(): SearchResponse = newAnimeSearchResponse(title, url, if (movie) TvType.AnimeMovie else TvType.Anime) {
        posterUrl = poster; posterHeaders = mapOf("Referer" to "$mainUrl/")
        if (title.contains("พากย์ไทย")) addDubStatus(DubStatus.Dubbed)
        if (title.contains("ซับไทย") || !title.contains("พากย์ไทย")) addDubStatus(DubStatus.Subbed)
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = listingRequests.withPermit {
        if (sampleMode) return@withPermit newHomePageResponse(
            listOf(HomePageList(SampleArchive.NOTICE, if (page == 1) sampleCards.map { it.response() } else emptyList())), false
        )
        val doc = document(AnimeParser.pageUrl(site, request.data, page))
        newHomePageResponse(listOf(HomePageList(request.name, AnimeParser.cards(site, doc).map { it.response() })), AnimeParser.next(site, doc) != null)
    }
    override suspend fun search(query: String): List<SearchResponse> = if (sampleMode) sampleCards.filter { it.title.contains(query.trim(), ignoreCase = true) }.map { it.response() } else AnimeParser.cards(site, document(AnimeParser.searchUrl(site, query))).map { it.response() }
    override suspend fun load(url: String): LoadResponse {
        val detail = AnimeParser.detail(site, document(url))
        if (detail.movie) return newMovieLoadResponse(detail.title, url, TvType.AnimeMovie, url) {
            posterUrl = detail.poster; plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
        }
        return newAnimeLoadResponse(detail.title, url, TvType.Anime) {
            posterUrl = detail.poster; plot = if (sampleMode) "${SampleArchive.NOTICE}\n\n${detail.plot.orEmpty()}" else detail.plot; tags = detail.tags; posterHeaders = mapOf("Referer" to url)
            detail.episodes.groupBy { if (it.dubbed) DubStatus.Dubbed else DubStatus.Subbed }.forEach { (dub, episodes) ->
                addEpisodes(dub, episodes.map { ep -> newEpisode(ep.url) { name = ep.title; episode = ep.number; season = ep.season } })
            }
        }
    }
    override fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? {
        if (site == Site.WAKU && extractorLink.extractorData == "waku-nya") return NyaInterceptor()
        if (extractorLink.extractorData != "animemami-playlist") return null
        val host = runCatching { java.net.URI(extractorLink.url).host }.getOrNull() ?: return null
        return AnimeMamiInterceptor(host, browserSession)
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withBrowserFallback(data, callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val visited = mutableSetOf<String>()
        val emitted = mutableSetOf<String>()
        var playbackFailure: String? = null
        val emit: (ExtractorLink) -> Unit = { if (emitted.add(it.url)) callback(it) }
        suspend fun resolve(source: PlayerSource, referer: String, depth: Int) {
            if (depth > 5 || !visited.add(source.url) || visited.size > 20) return
            try {
                if (site == Site.RUKA && AnimeMami.isPlayer(source.url)) {
                    val doc = document(source.url, fresh = true, referer = referer)
                    AnimeMami.stream(doc)?.let { stream ->
                        stream.subtitle?.let { subtitleCallback(newSubtitleFile(stream.subtitleLanguage, it)) }
                        emit(newExtractorLink(name, "$name - AnimeMami", stream.url,
                            if (stream.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                            this.referer = doc.baseUri()
                            quality = Qualities.Unknown.value
                            if (stream.hls) extractorData = "animemami-playlist"
                        })
                    }
                } else if (java.net.URI(source.url).host == "app.akuma-stream.com") {
                    val doc = document(source.url, fresh = true, referer = referer)
                    AnimePlayer.akuma(doc)?.let { stream ->
                        emit(newExtractorLink(name, "$name - Akuma", stream, ExtractorLinkType.M3U8) {
                            this.referer = doc.baseUri()
                            quality = Qualities.Unknown.value
                        })
                    }
                } else if (site == Site.SUBA && SubaPlayer.isPlayer(source.url)) {
                    val resolver = SubaPlayer { url, ref ->
                        // Reuse existing website cookies; the resolver also has an observed content-origin fallback.
                        val response = app.get(url, referer = ref, headers = browserSession.getCookieHeaders(url), cacheTime = 0)
                        if (response.code !in 200..299) throw ErrorLoadingException("Suba player HTTP ${response.code}")
                        response.document.apply { setBaseUri(response.url) }
                    }
                    val streams = resolver.resolve(source.url, referer)
                    streams.forEach { stream -> emit(newExtractorLink(name, "$name - ${stream.label}", stream.url, ExtractorLinkType.VIDEO) {
                        this.referer = stream.referer
                        quality = Regex("\\d+").find(stream.label)?.value?.toIntOrNull() ?: Qualities.Unknown.value
                    }) }
                    if (streams.isEmpty()) loadExtractor(source.url, referer, subtitleCallback, emit)
                } else if (AnimePlayer.canUnwrap(source.url, site)) {
                    val doc = document(source.url, fresh = true, referer = referer)
                    val manifest = AnimePlayer.manifest(doc)
                    if (manifest != null) emit(newExtractorLink(name, "$name - ${source.label}", manifest, ExtractorLinkType.M3U8) {
                        this.referer = source.url; quality = Qualities.Unknown.value
                        if (site == Site.WAKU && WakuPlayer.manifestPattern.matches(manifest)) {
                            headers = mapOf("User-Agent" to WakuPlayer.mediaUserAgent)
                            extractorData = "waku-nya"
                        }
                    })
                    else AnimePlayer.unwrap(doc).forEach { resolve(it.copy(label = source.label), source.url, depth + 1) }
                } else loadExtractor(source.url, referer, subtitleCallback, emit)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                playbackFailure = error.message ?: error.javaClass.simpleName
            }
        }
        suspend fun page(url: String, depth: Int) {
            if (depth > 1) return
            val doc = document(url, fresh = true)
            val detail = runCatching { AnimeParser.detail(site, doc) }.getOrNull()
            if (depth == 0 && detail?.movie == true && detail.episodes.isNotEmpty()) {
                detail.episodes.forEach { ep ->
                    try { page(ep.url, depth + 1) } catch (error: Exception) { if (error is CancellationException) throw error }
                }
                return
            }
            if (site == Site.WAKU) {
                WakuPlayer.sources(doc,
                    getJson = { endpoint, referer ->
                        val response = app.get(endpoint, referer = referer,
                            headers = browserSession.getCookieHeaders(endpoint).toMap() + WakuPlayer.ajaxHeaders,
                            cacheTime = 0)
                        if (response.code == 200) response.text else null
                    },
                    resolve = { source, referer -> resolve(source, referer, 0) },
                    hasLinks = { emitted.isNotEmpty() },
                )
                return
            }
            AnimePlayer.sources(site, doc).forEach { resolve(it, url, 0) }
            AnimePlayer.options(doc).forEach { option ->
                try {
                    val response = app.get(option.url, referer = url, interceptor = browserSession, cacheTime = 0)
                    if (response.code == 200) AnimePlayer.ajaxEmbed(response.text, url)?.let { resolve(PlayerSource(it, option.label), url, 0) }
                } catch (error: Exception) { if (error is CancellationException) throw error }
            }
        }
        if (site == Site.WAKU) {
            try { page(data, 0) } catch (error: Exception) { if (error is CancellationException) throw error }
            if (emitted.isEmpty()) {
                // Run the real page when its player iframe is created only by JavaScript.
                // Capture the content iframe before it needs Play, or the observed HLS master.
                try {
                    val request = WebViewResolver(
                        interceptUrl = WakuPlayer.capturePattern,
                        userAgent = null,
                        useOkhttp = false,
                        timeout = 8_000L,
                    ).resolveUsingWebView(data, referer = "$mainUrl/").first
                    if (request != null && WakuPlayer.playerPattern.matches(request.url.toString())) {
                        // An iframe loads before Play; waiting for HLS alone can time out on gesture-gated players.
                        val target = request.url.toString()
                        visited.remove(target)
                        resolve(PlayerSource(target, "ตัวเล่นเว็บ"), request.header("Referer") ?: data, 0)
                    }
                    request?.let(WakuPlayer::browserStream)?.let { stream ->
                        emit(newExtractorLink(name, "$name - ตัวเล่นเว็บ", stream.url, ExtractorLinkType.M3U8) {
                            referer = stream.referer
                            headers = stream.headers + ("User-Agent" to WakuPlayer.mediaUserAgent)
                            extractorData = "waku-nya"
                            quality = Qualities.Unknown.value
                        })
                    }
                } catch (error: Exception) { if (error is CancellationException) throw error }
            }
        } else if (site == Site.SUBA) {
            try { page(data, 0) } catch (error: Exception) {
                if (error is CancellationException) throw error
                playbackFailure = error.message ?: error.javaClass.simpleName
            }
            if (emitted.isEmpty()) {
                try {
                    // Read the real iframe through the browser's native networking and JS.
                    // This is bounded player discovery; it does not solve or poll a challenge.
                    val request = WebViewResolver(
                        interceptUrl = SubaPlayer.capturePattern,
                        userAgent = null,
                        useOkhttp = false,
                        timeout = 8_000L,
                    ).resolveUsingWebView(data, referer = "$mainUrl/").first
                    if (request != null && request.method == "GET" && SubaPlayer.isPlayer(request.url.toString())) {
                        val target = request.url.toString()
                        visited.remove(target)
                        resolve(PlayerSource(target, "ตัวเล่นเว็บ"), request.header("Referer") ?: data, 0)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (playbackFailure == null) playbackFailure = error.message ?: error.javaClass.simpleName
                }
            }
        } else page(data, 0)
        if (emitted.isEmpty()) throw ErrorLoadingException("$name ไม่พบลิงก์ที่เล่นได้" +
            (playbackFailure?.let { " ($it)" } ?: " โฮสต์วิดีโออาจบล็อกหรือยังไม่รองรับ ลองตัวเล่นอื่นในเว็บ"))
        return true
    }
}
