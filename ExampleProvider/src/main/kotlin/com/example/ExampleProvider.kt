package com.example

import com.lagradost.cloudstream3.*
import com.providers.network.BrowserSession
import com.providers.network.withNativePlayback
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://www.037-hdd.com"
    override var name = "037-HDD"
    override val hasMainPage = true
    override var lang = "th"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override var sequentialMainPage = false
    private val listingRequests = Semaphore(3)
    override val mainPage = SiteCategories.homeEntries.map { (path, title) -> mainPage(mainUrl + path, title) }

    private val browserSession by lazy { BrowserSession() }
    private suspend fun document(url: String, fresh: Boolean = false): org.jsoup.nodes.Document {
        val response = if (fresh) app.get(url, interceptor = browserSession, cacheTime = 0) else app.get(url, interceptor = browserSession)
        if (response.code !in 200..299) throw ErrorLoadingException("$name โหลดหน้าเว็บไม่ได้ (HTTP ${response.code})")
        return response.document.apply { setBaseUri(response.url) }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse = listingRequests.withPermit {
        val url = MovieListingParser.pageUrl(request.data, page)
        val document = document(url)
        val home = MovieListingParser.parse(document).map { it.toSearchResult() }

        newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = MovieListingParser.hasNextPage(document)
        )
    }

    private fun MovieListing.toSearchResult(): SearchResponse {
        return if (isSeries) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = this@toSearchResult.posterUrl
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = this@toSearchResult.posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        val document = document(url)
        return MovieListingParser.parse(document).map { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = document(url)

        val details = MovieDetailsParser.parse(document)
        val title = details.title
        val poster = details.posterUrl
        val plot = details.plot
        val tags = document.select("a[rel=category tag], .genre a").map { it.text().trim() }

        val episodes = SeriesEpisodeParser.parse(document, title).map { episode ->
            newEpisode(episode.url) {
                this.name = episode.name
                this.episode = episode.number
                this.season = episode.season
            }
        }
        if (episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = plot
                this.tags = tags
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        withNativePlayback(callback) { emit -> loadVideoLinks(data, isCasting, subtitleCallback, emit) }

    private suspend fun loadVideoLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Tickets and player contexts expire, so request a fresh detail page for each play.
        val doc = document(data, fresh = true)
        val playerUrls = MoviePlayerParser.parse(doc).toMutableList()
        Regex("""(https?://[a-zA-Z0-9.-]*streamhls[a-zA-Z0-9.-]*/[^\s"'<>]+)""")
            .findAll(doc.html()).forEach {
                playerUrls.add(MoviePlayer("StreamHLS", it.groupValues[1].replace("\\/", "/")))
            }

        val resolver = StreamHlsResolver(
            get = { url, referer ->
                val response = app.get(url, referer = referer, interceptor = browserSession, cacheTime = 0)
                PlayerPage(response.url, response.text)
            },
            post = { url, referer, payload ->
                val response = app.post(
                    url,
                    referer = referer,
                    headers = mapOf(
                        "Origin" to StreamHlsResolver.origin(referer),
                        "X-Requested-With" to "XMLHttpRequest"
                    ),
                    json = payload,
                    interceptor = browserSession,
                    cacheTime = 0
                )
                PlayerPage(response.url, response.text)
            }
        )
        var found = false
        val emitted = mutableSetOf<String>()
        val emit: (ExtractorLink) -> Unit = { link ->
            if (emitted.add(StreamHlsResolver.streamKey(link.url))) {
                found = true
                callback(link)
            }
        }
        for (player in playerUrls.distinctBy { it.url }) {
            val iframeUrl = player.url
            val streams = try {
                resolver.resolve(iframeUrl, data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emptyList()
            }
            for (stream in streams) {
                emit(newExtractorLink(
                    source = name,
                    name = "${MoviePlayerParser.sourceLabel(player, stream.label)} - $name",
                    url = stream.url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = stream.referer
                    this.headers = mapOf(
                        "Origin" to StreamHlsResolver.origin(stream.referer),
                        "Referer" to stream.referer
                    )
                    this.quality = Qualities.Unknown.value
                })
            }
            if (streams.isEmpty()) {
                try {
                    loadExtractor(iframeUrl, data, subtitleCallback, emit)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                }
            }
        }
        return found
    }
}
