package com.providers.network

import com.fasterxml.jackson.core.Base64Variants
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class SampleArchiveTest {
    private fun page(url: String, kind: String = "detail"): SamplePage {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write("<h1>Recorded</h1><a href='/episode/1'>Episode</a>".toByteArray()) }
        return SamplePage(url, kind, Base64Variants.getDefaultVariant().encode(bytes.toByteArray()))
    }
    @Test fun `exact source page restores base URL but never matches search player or another host`() {
        val archive = SampleArchive(listOf(page("https://source.example/show/")))
        val doc = archive.document("https://source.example/show")!!
        assertEquals("https://source.example/episode/1", doc.selectFirst("a")!!.absUrl("href"))
        assertNull(archive.document("https://source.example/show/?s=other"))
        assertNull(archive.document("https://player.example/show/"))
        assertNull(archive.document("http://source.example/show/"))
        assertNull(archive.document("https://source.example/show/#episode=2"))
    }
    @Test fun `each read gets independent DOM and kind filtering excludes episode pages`() {
        val archive = SampleArchive(listOf(page("https://source.example/show/"), page("https://source.example/ep/1", "episode")))
        archive.document("https://source.example/show/")!!.select("h1").remove()
        assertEquals("Recorded", archive.document("https://source.example/show/")!!.selectFirst("h1")!!.text())
        assertEquals(1, archive.documents("https://source.example", "detail").size)
        assertTrue(archive.documents("https://other.example", "detail").isEmpty())
    }
}
