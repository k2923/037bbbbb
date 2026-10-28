package com.twentyfourhd

import com.providers.samples.bundledSamples
import org.junit.Assert.*
import org.junit.Test

class BundledSamplesTest {
    @Test fun `bundled catalog includes recorded details and preserves real episode players`() {
        val listings = bundledSamples.documents("https://www.24hd.today", "listing")
        assertTrue(listings.flatMap(TwentyFourHDParser::cards).isNotEmpty())
        val details = bundledSamples.documents("https://www.24hd.today", "detail")
        assertEquals(2, details.size)
        val series = details.map(TwentyFourHDParser::detail).single { it.episodes.isNotEmpty() }
        assertEquals((1..19).toList(), series.episodes.map { it.number })
        assertTrue(series.episodes.all { ep -> ep.players.all { it.startsWith("https://") && !it.contains("_files/") } })
        details.forEach { assertNotNull(bundledSamples.document(it.baseUri())) }
        assertNull(bundledSamples.document(series.episodes.first().players.first()))
    }
}
