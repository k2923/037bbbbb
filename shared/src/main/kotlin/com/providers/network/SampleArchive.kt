package com.providers.network

import com.fasterxml.jackson.core.Base64Variants
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

internal data class SamplePage(val url: String, val kind: String, val compressed: String)

/** Read-only captured catalog pages, never a cache of player responses or media tokens. */
internal class SampleArchive(pages: List<SamplePage>) {
    private val pages = pages.associateBy { key(it.url) }

    fun document(url: String): Document? = pages[key(url)]?.let { page ->
        val bytes = Base64Variants.getDefaultVariant().decode(page.compressed)
        val html = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        Jsoup.parse(html, page.url)
    }

    fun documents(base: String, kind: String): List<Document> = pages.values
        .filter { URI(it.url).host.removePrefix("www.") == URI(base).host.removePrefix("www.") && it.kind == kind }
        .mapNotNull { document(it.url) }

    companion object {
        const val LABEL = "สำรองจาก samples"
        const val NOTICE = "ข้อมูลที่บันทึกไว้ อาจไม่ครบหรือไม่เป็นปัจจุบัน; ตัวเล่นต้องเชื่อมต่อเว็บจริง"
        // Keep queries and hosts distinct: a search or a player URL must never match a saved catalog.
        private fun key(url: String): String = runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null) return url
            "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.trimEnd('/')}" +
                (uri.rawQuery?.let { "?$it" } ?: "")
        }.getOrDefault(url)
    }
}
