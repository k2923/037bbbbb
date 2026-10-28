package com.ahdzero

import com.providers.samples.bundledSamples
import org.junit.Assert.*
import org.junit.Test

class BundledSamplesTest {
    @Test fun `catalog and episode embed survive bundling without storing player responses`() {
        assertTrue(bundledSamples.documents("https://ahdzero.com", "listing").flatMap(AHDZeroParser::cards).isNotEmpty())
        val details = bundledSamples.documents("https://ahdzero.com", "detail").map(AHDZeroParser::detail)
        assertEquals(2, details.size)
        assertTrue(details.all { it.episodes.isNotEmpty() })
        val episode = bundledSamples.documents("https://ahdzero.com", "episode").single()
        val embed = AHDZeroParser.embed(episode)
        assertNotNull(embed)
        assertNull(bundledSamples.document(embed!!))
    }
}
