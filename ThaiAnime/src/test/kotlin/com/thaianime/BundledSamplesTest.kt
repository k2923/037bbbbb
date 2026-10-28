package com.thaianime

import com.providers.samples.bundledSamples
import org.junit.Assert.*
import org.junit.Test

class BundledSamplesTest {
    @Test fun `every enabled legacy site has a usable saved catalog details and episode source pages`() {
        for (site in Site.enabled) {
            val listings = bundledSamples.documents(site.base, "listing")
            assertTrue(site.label, listings.flatMap { AnimeParser.cards(site, it) }.isNotEmpty())
            val details = bundledSamples.documents(site.base, "detail")
            assertEquals(site.label, 2, details.size)
            details.forEach { doc ->
                assertTrue(AnimeParser.detail(site, doc).title.isNotBlank())
                assertNotNull(bundledSamples.document(doc.baseUri()))
            }
            val episodes = bundledSamples.documents(site.base, "episode")
            assertEquals(site.label, 2, episodes.size)
            assertTrue(site.label, episodes.flatMap { AnimePlayer.sources(site, it) }.isNotEmpty())
            episodes.forEach { assertTrue(it.select("script:not([type=application/ld+json])").isEmpty()) }
        }
        assertTrue(bundledSamples.documents(Site.WAKU.base, "listing").isEmpty())
        assertTrue(bundledSamples.documents(Site.SEVEN.base, "listing").isEmpty())
    }
}
