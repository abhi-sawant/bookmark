package com.bookmark.metadata.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleSuffixTest {

    @Test
    fun `strips a pipe suffix matching og site name`() {
        assertEquals(
            "How To Build An App",
            TitleSuffix.strip(
                "How To Build An App | Acme Publishing",
                siteName = "Acme Publishing",
                url = "https://acme.example/blog/x",
            ),
        )
    }

    @Test
    fun `strips an em dash suffix matching the host`() {
        assertEquals(
            "Some Article",
            TitleSuffix.strip("Some Article — example.com", siteName = null, url = "https://example.com/a"),
        )
    }

    @Test
    fun `strips a hyphen suffix matching the registrable domain stem`() {
        assertEquals(
            "Getting Started",
            TitleSuffix.strip("Getting Started - Jsoup", siteName = null, url = "https://jsoup.org/cookbook"),
        )
    }

    @Test
    fun `does not strip a suffix that is merely a prefix of the domain stem`() {
        // Otherwise "Face" would be stripped from anything on facebook.com.
        val title = "Photo Albums - Face"
        assertEquals(
            title,
            TitleSuffix.strip(title, siteName = null, url = "https://facebrick.example/albums"),
        )
    }

    @Test
    fun `strips a known-host display name`() {
        assertEquals(
            "Y Combinator",
            TitleSuffix.strip(
                "Y Combinator | Hacker News",
                siteName = null,
                url = "https://news.ycombinator.com/item?id=1",
            ),
        )
    }

    @Test
    fun `strips a suffix abbreviating a longer og site name`() {
        assertEquals(
            "HTML",
            TitleSuffix.strip("HTML | MDN", siteName = "MDN Web Docs", url = "https://developer.mozilla.org/x"),
        )
    }

    @Test
    fun `leaves a suffix that does not name the site`() {
        val title = "Kotlin - The Programming Language"
        assertEquals(
            title,
            TitleSuffix.strip(title, siteName = "JetBrains", url = "https://kotlinlang.org/"),
        )
    }

    @Test
    fun `splits at the last separator only`() {
        assertEquals(
            "Foo - Bar",
            TitleSuffix.strip("Foo - Bar | Acme", siteName = "Acme", url = "https://acme.example/"),
        )
    }

    @Test
    fun `leaves a title that is nothing but the site name`() {
        // Stripping would leave an empty head, which is worse than the suffix.
        assertEquals("Acme", TitleSuffix.strip("Acme", siteName = "Acme", url = "https://acme.example/"))
    }

    @Test
    fun `matches case insensitively`() {
        assertEquals(
            "Docs",
            TitleSuffix.strip("Docs | ACME PUBLISHING", siteName = "Acme Publishing", url = "https://acme.example/"),
        )
    }

    @Test
    fun `ignores a hyphen with no surrounding spaces`() {
        val title = "Well-Known URIs"
        assertEquals(title, TitleSuffix.strip(title, siteName = "Known", url = "https://known.example/"))
    }
}
