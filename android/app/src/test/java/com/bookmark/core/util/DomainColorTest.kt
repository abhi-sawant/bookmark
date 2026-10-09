package com.bookmark.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainColorTest {

    @Test
    fun `the same domain always gets the same swatch`() {
        val first = DomainColor.indexFor("https://example.com/a")
        val second = DomainColor.indexFor("https://example.com/completely/different/path")
        assertEquals(first, second)
    }

    @Test
    fun `subdomains collapse onto the registrable domain`() {
        assertEquals(
            DomainColor.indexFor("https://ycombinator.com"),
            DomainColor.indexFor("https://news.ycombinator.com/item?id=1"),
        )
    }

    @Test
    fun `always lands inside the palette`() {
        val hosts = listOf(
            "https://a.com", "https://b.org", "https://ogp.me", "https://medium.com",
            "https://developer.android.com", "https://seriouseats.com", "https://are.na",
            "not a url", "",
        )
        hosts.forEach { host ->
            val index = DomainColor.indexFor(host)
            assertTrue("index $index out of range for $host", index in 0 until DomainColor.PALETTE_SIZE)
        }
    }

    @Test
    fun `spreads across the palette rather than clustering`() {
        val domains = (1..200).map { "https://site$it.example" }
        val used = domains.map(DomainColor::indexFor).toSet()
        assertEquals(DomainColor.PALETTE_SIZE, used.size)
    }
}
