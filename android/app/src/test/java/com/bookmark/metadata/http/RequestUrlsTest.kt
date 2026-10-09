package com.bookmark.metadata.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestUrlsTest {

    @Test
    fun `http is upgraded to https when cleartext is blocked`() {
        assertEquals(
            "https://example.com/a?b=1",
            RequestUrls.normalizeForRequest("http://example.com/a?b=1", cleartextBlocked = true),
        )
    }

    @Test
    fun `the upgrade preserves a non-default port`() {
        assertEquals(
            "https://example.com:8080/a",
            RequestUrls.normalizeForRequest("http://example.com:8080/a", cleartextBlocked = true),
        )
    }

    @Test
    fun `http is left alone when cleartext is permitted`() {
        assertEquals(
            "http://example.com/a",
            RequestUrls.normalizeForRequest("http://example.com/a", cleartextBlocked = false),
        )
    }

    @Test
    fun `https is untouched`() {
        assertEquals(
            "https://example.com/a",
            RequestUrls.normalizeForRequest("https://example.com/a", cleartextBlocked = true),
        )
    }

    @Test
    fun `a schemeless url becomes https`() {
        assertEquals(
            "https://example.com/a",
            RequestUrls.normalizeForRequest("example.com/a", cleartextBlocked = true),
        )
    }

    @Test
    fun `other schemes are refused`() {
        assertNull(RequestUrls.normalizeForRequest("ftp://example.com/f", cleartextBlocked = true))
        assertNull(RequestUrls.normalizeForRequest("javascript:alert(1)", cleartextBlocked = true))
        assertNull(RequestUrls.normalizeForRequest("", cleartextBlocked = true))
    }

    @Test
    fun `a relative redirect resolves against the current url`() {
        assertEquals(
            "https://example.com/b/c",
            RequestUrls.resolveRedirect("https://example.com/a/page", "/b/c", cleartextBlocked = true),
        )
        assertEquals(
            "https://example.com/a/sibling",
            RequestUrls.resolveRedirect("https://example.com/a/page", "sibling", cleartextBlocked = true),
        )
    }

    @Test
    fun `an absolute redirect replaces the url and is itself upgraded`() {
        assertEquals(
            "https://other.example/x",
            RequestUrls.resolveRedirect("https://example.com/a", "http://other.example/x", cleartextBlocked = true),
        )
    }

    @Test
    fun `a protocol-relative redirect inherits the scheme`() {
        assertEquals(
            "https://cdn.example/x",
            RequestUrls.resolveRedirect("https://example.com/a", "//cdn.example/x", cleartextBlocked = true),
        )
    }

    @Test
    fun `a redirect to an unsupported scheme is refused`() {
        assertNull(
            RequestUrls.resolveRedirect("https://example.com/a", "mailto:x@example.com", cleartextBlocked = true),
        )
    }
}
