package com.bookmark.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleFallbackTest {

    @Test
    fun `a fetched title wins outright`() {
        assertEquals(
            "The design of everyday APIs",
            TitleFallback.resolve(
                fetchedTitle = "The design of everyday APIs",
                sharedSubject = "Something else",
                url = "https://increment.com/apis/design",
            ),
        )
    }

    @Test
    fun `falls back to the share subject`() {
        assertEquals(
            "Shared page title",
            TitleFallback.resolve(
                sharedSubject = "Shared page title",
                url = "https://example.com/x",
            ),
        )
    }

    @Test
    fun `ignores a subject that is just the url again`() {
        val url = "https://example.com/some-article"
        assertEquals("Some Article", TitleFallback.resolve(sharedSubject = url, url = url))
    }

    @Test
    fun `derives a title from the path, dropping a trailing id`() {
        // The worked example from the spec.
        assertEquals(
            "How To Build An App",
            TitleFallback.resolve(url = "https://example.com/blog/how-to-build-an-app-1234"),
        )
    }

    @Test
    fun `skips noise segments when walking the path backwards`() {
        assertEquals(
            "Offline First Compose",
            TitleFallback.resolve(url = "https://example.com/offline-first-compose/index.html"),
        )
    }

    @Test
    fun `falls back to a curated host name`() {
        // The other worked example from the spec.
        assertEquals("Hacker News", TitleFallback.resolve(url = "https://news.ycombinator.com"))
    }

    @Test
    fun `falls back to the title-cased domain when the host is unknown`() {
        assertEquals("Kevincox", TitleFallback.resolve(url = "https://kevincox.ca"))
    }

    @Test
    fun `the chain always terminates in something non-empty`() {
        val weird = "https://a.io"
        assert(TitleFallback.resolve(url = weird).isNotEmpty())
    }

    @Test
    fun `monogram takes initials from a curated name`() {
        assertEquals("HN", TitleFallback.monogram("https://news.ycombinator.com"))
        assertEquals("KE", TitleFallback.monogram("https://kevincox.ca").take(2))
    }
}
