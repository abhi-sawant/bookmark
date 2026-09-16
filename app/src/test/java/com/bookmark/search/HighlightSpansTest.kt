package com.bookmark.search

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightSpansTest {

    @Test
    fun `single term highlights every matching word`() {
        val ranges = findHighlightRanges("Everything about HTML metadata", listOf("metadata"))
        assertEquals(listOf(22..29), ranges)
    }

    @Test
    fun `matching is case-insensitive`() {
        val ranges = findHighlightRanges("HTML Metadata Guide", listOf("html"))
        assertEquals(listOf(0..3), ranges)
    }

    @Test
    fun `prefix matches like FTS4's term star syntax`() {
        val ranges = findHighlightRanges("Everything about HTML metadata", listOf("meta"))
        assertEquals(listOf(22..29), ranges)
    }

    @Test
    fun `multiple terms all highlight`() {
        val ranges = findHighlightRanges("Room FTS4 and metadata in practice", listOf("fts4", "metadata"))
        assertEquals(listOf(5..8, 14..21), ranges)
    }

    @Test
    fun `no match returns no ranges`() {
        val ranges = findHighlightRanges("Nothing relevant here", listOf("zzz"))
        assertEquals(emptyList<IntRange>(), ranges)
    }

    @Test
    fun `empty terms returns no ranges`() {
        assertEquals(emptyList<IntRange>(), findHighlightRanges("Some title", emptyList()))
    }

    @Test
    fun `term does not highlight mid-word`() {
        // "html" should not light up inside "mathHTMLish" beyond its own token.
        val ranges = findHighlightRanges("mathHTMLish is one word", listOf("html"))
        assertEquals(emptyList<IntRange>(), ranges)
    }

    @Test
    fun `queryTermsFor strips quotes and splits on whitespace`() {
        assertEquals(listOf("meta", "data"), queryTermsFor("\"meta\"  data"))
    }

    @Test
    fun `queryTermsFor drops blank terms from extra whitespace`() {
        assertEquals(listOf("html"), queryTermsFor("  html   "))
    }
}
