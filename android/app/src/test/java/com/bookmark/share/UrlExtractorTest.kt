package com.bookmark.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun `takes the first url out of chatty shared text`() {
        val result = UrlExtractor.extract("Check this out https://example.com/x")
        assertEquals("https://example.com/x", result.primaryUrl)
        assertTrue(result.otherUrls.isEmpty())
    }

    @Test
    fun `handles the YouTube shape of title plus link plus promo`() {
        val result = UrlExtractor.extract(
            "Rust on Android: what changed\nhttps://youtu.be/abc123\n\nWatch more at https://youtube.com",
        )
        assertEquals("https://youtu.be/abc123", result.primaryUrl)
        assertEquals(listOf("https://youtube.com"), result.otherUrls)
    }

    @Test
    fun `exposes the remaining links rather than dropping them`() {
        val result = UrlExtractor.extract("one https://a.com two https://b.com three https://c.com")
        assertEquals("https://a.com", result.primaryUrl)
        assertEquals(listOf("https://b.com", "https://c.com"), result.otherUrls)
    }

    @Test
    fun `keeps the raw text when there is no url, rather than failing`() {
        val result = UrlExtractor.extract("just some words here")
        assertNull(result.primaryUrl)
        assertFalse(result.hasUrl)
        assertEquals("just some words here", result.rawText)
    }

    @Test
    fun `holds the subject as a title candidate`() {
        val result = UrlExtractor.extract(
            text = "https://example.com/x",
            subject = "The design of everyday APIs",
        )
        assertEquals("The design of everyday APIs", result.subjectTitle)
    }

    @Test
    fun `drops a subject that is just the url`() {
        val result = UrlExtractor.extract(
            text = "https://example.com/x",
            subject = "https://example.com/x",
        )
        assertNull(result.subjectTitle)
    }

    @Test
    fun `trims sentence punctuation off the end of a url`() {
        assertEquals("https://example.com/x", UrlExtractor.extract("See https://example.com/x.").primaryUrl)
        assertEquals("https://example.com", UrlExtractor.extract("(https://example.com)").primaryUrl)
    }

    @Test
    fun `does not mistake version numbers for hosts`() {
        assertNull(UrlExtractor.extract("upgraded to v2.14 today").primaryUrl)
        assertNull(UrlExtractor.extract("pi is roughly 3.14159").primaryUrl)
    }

    @Test
    fun `finds a bare domain with no scheme`() {
        assertEquals("example.com/page", UrlExtractor.extract("go to example.com/page").primaryUrl)
    }

    @Test
    fun `deduplicates a link repeated in the same share`() {
        val result = UrlExtractor.extract("https://a.com and again https://a.com")
        assertEquals("https://a.com", result.primaryUrl)
        assertTrue(result.otherUrls.isEmpty())
    }
}
