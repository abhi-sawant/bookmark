package com.bookmark.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `adds https when no scheme is present`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com"))
    }

    @Test
    fun `lowercases scheme and host but leaves the path alone`() {
        assertEquals(
            "https://example.com/Some/Path",
            UrlNormalizer.normalize("HTTPS://EXAMPLE.COM/Some/Path"),
        )
    }

    @Test
    fun `strips default ports only`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com:443"))
        assertEquals("http://example.com", UrlNormalizer.normalize("http://example.com:80"))
        assertEquals("https://example.com:8443", UrlNormalizer.normalize("https://example.com:8443"))
    }

    @Test
    fun `strips the fragment`() {
        assertEquals(
            "https://example.com/post",
            UrlNormalizer.normalize("https://example.com/post#section-2"),
        )
    }

    @Test
    fun `keeps the fragment on hosts that route on it`() {
        assertEquals(
            "https://groups.google.com/forum#!topic/abc",
            UrlNormalizer.normalize("https://groups.google.com/forum#!topic/abc"),
        )
    }

    @Test
    fun `strips tracking parameters`() {
        assertEquals(
            "https://example.com/a",
            UrlNormalizer.normalize(
                "https://example.com/a?utm_source=x&utm_medium=y&fbclid=z&gclid=q",
            ),
        )
    }

    @Test
    fun `preserves parameters that carry the content`() {
        // Stripping these would break the link -- called out explicitly in the spec.
        assertEquals(
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            UrlNormalizer.normalize("https://youtube.com/watch?v=dQw4w9WgXcQ&si=trackingtoken"),
        )
        assertEquals(
            "https://blog.example.com/?p=1234",
            UrlNormalizer.normalize("https://blog.example.com/?p=1234&utm_campaign=spring"),
        )
    }

    @Test
    fun `strips a trailing slash only on a bare host`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com/"))
        assertEquals("https://example.com/blog", UrlNormalizer.normalize("https://example.com/blog/"))
    }

    @Test
    fun `never throws on input it cannot parse`() {
        assertEquals("not a url at all", UrlNormalizer.normalize("not a url at all"))
        assertEquals("", UrlNormalizer.normalize("   "))
    }

    @Test
    fun `validity is syntactic only`() {
        assertTrue(UrlNormalizer.isValid("example.com"))
        assertTrue(UrlNormalizer.isValid("https://example.com/a/b?c=d"))
        assertFalse(UrlNormalizer.isValid("hello world"))
        assertFalse(UrlNormalizer.isValid(""))
        assertFalse(UrlNormalizer.isValid("ftp://example.com"))
    }

    @Test
    fun `registrable domain handles multi-part suffixes`() {
        assertEquals("ycombinator.com", UrlNormalizer.registrableDomain("https://news.ycombinator.com"))
        assertEquals("bbc.co.uk", UrlNormalizer.registrableDomain("https://www.bbc.co.uk/news"))
        assertEquals("example.com", UrlNormalizer.registrableDomain("https://example.com"))
    }

    @Test
    fun `host drops the www prefix`() {
        assertEquals("example.com", UrlNormalizer.host("https://www.example.com/x"))
    }
}
