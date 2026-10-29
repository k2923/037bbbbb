package com.twentyfourhd

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

internal data class PlayerDocument(val url: String, val document: Document, val status: Int)
internal data class PlayerStream(val url: String, val referer: String)

/** Resolve the observed ongetplay wrapper without following ad or unrelated iframe hosts. */
internal object TwentyFourHDPlayer {
    private fun wrapper(value: String) = value.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host == "ongetplay.xyz" && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath in listOf("/playhls/play.php", "/playproxy/play.php") &&
            it.queryParameter("id")?.matches(Regex("[0-9]+")) == true
    }

    private fun embed(value: String) = value.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host == "playermhd.p2phls.xyz" && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.matches(Regex("/embed/[A-Za-z0-9_-]+/"))
    }

    suspend fun resolve(url: String, referer: String, get: suspend (String, String, Boolean) -> PlayerDocument): PlayerStream? {
        val first = get(url, referer, true)
        if (first.status !in 200..299) return null
        val initial = wrapper(url)
        if (initial != null) {
            val redirected = wrapper(first.url) ?: return null
            if (initial.queryParameter("id") != redirected.queryParameter("id")) return null
        }
        TwentyFourHDParser.stream(first.document)?.let { return PlayerStream(it, first.url) }

        if (initial == null) return null
        val frame = first.document.selectFirst("iframe#player[src]")?.attr("src").orEmpty()
        val child = TwentyFourHDParser.httpUrl(first.url, frame)?.let(::embed) ?: return null
        val second = get(child.toString(), first.url, false)
        if (second.status !in 200..299) return null
        val final = embed(second.url) ?: return null
        if (child.encodedPath != final.encodedPath) return null
        return TwentyFourHDParser.stream(second.document)?.let { PlayerStream(it, second.url) }
    }
}
