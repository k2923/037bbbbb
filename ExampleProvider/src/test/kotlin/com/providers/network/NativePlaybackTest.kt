package com.providers.network

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
class NativePlaybackTest {
    private fun media() = ExtractorLink("fixture", "primary", "https://cdn.example/movie.mp4", "https://catalog.example/", 720, ExtractorLinkType.VIDEO)

    @Test fun `playable primary survives a later broken backup`() = runBlocking {
        val links = mutableListOf<ExtractorLink>()
        val result = withNativePlayback(links::add) { emit ->
            emit(media())
            throw IOException("Backup unavailable")
        }
        assertTrue(result)
        assertEquals("https://cdn.example/movie.mp4", links.single().url)
    }

    @Test fun `real media callback determines success even when extractor returns false`() = runBlocking {
        val links = mutableListOf<ExtractorLink>()
        assertTrue(withNativePlayback(links::add) { emit -> emit(media()); false })
        assertEquals(1, links.size)
    }

    @Test fun `empty extraction fails in app even when extractor reports success`() = runBlocking {
        val links = mutableListOf<ExtractorLink>()
        val error = runCatching { withNativePlayback(links::add) { true } }.exceptionOrNull()
        assertTrue(error is ErrorLoadingException)
        assertTrue(error!!.message.orEmpty().contains("หน้าเว็บไม่ส่งลิงก์วิดีโอ"))
        assertFalse(error.message.orEmpty().contains("เบราว์เซอร์"))
        assertTrue(links.isEmpty())
    }

    @Test fun `extraction failure keeps the cause in app without a browser window`() = runBlocking {
        val error = runCatching { withNativePlayback({}) { throw IOException("Player HTTP 403") } }.exceptionOrNull()
        assertTrue(error is ErrorLoadingException)
        assertTrue(error!!.message.orEmpty().contains("Player HTTP 403"))
        assertFalse(error.message.orEmpty().contains("เปิดเว็บสำรอง"))
    }

    @Test fun `cancellation propagates before and after emitting media`() = runBlocking {
        for (emitted in listOf(false, true)) {
            val expected = CancellationException("User left playback")
            val error = runCatching {
                withNativePlayback({}) { emit ->
                    if (emitted) emit(media())
                    throw expected
                }
            }.exceptionOrNull()
            assertSame(expected, error)
        }
    }
}
