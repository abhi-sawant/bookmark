package com.bookmark.search

import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFiltersTest {
    private val now = 10L * SearchFilters.WEEK_MS

    private fun bookmark(id: String, pinned: Boolean = false, category: String = "work", createdAt: Long = now, unread: Boolean = false) = Bookmark(
        id = id, url = "https://example.com/$id", originalUrl = "https://example.com/$id", title = id,
        description = null, siteName = null, thumbnailPath = null, faviconPath = null, accentColor = null,
        thumbnailWidth = null, thumbnailHeight = null, imageCandidates = emptyList(), categoryId = category,
        metadataState = MetadataState.SUCCESS, failureCause = null, fetchAttempts = 0, lastFetchAt = null,
        manualFields = ManualField.NONE, isPinned = pinned, createdAt = createdAt, updatedAt = createdAt, isUnread = unread,
    )

    private val list = listOf(
        bookmark("pinned", pinned = true),
        bookmark("unsorted", category = Category.UNSORTED_ID),
        bookmark("old", createdAt = now - 8 * 24 * 60 * 60 * 1000L, unread = true),
    )

    @Test
    fun `inactive filters keep everything`() {
        assertEquals(3, SearchFilters().apply(list, now).size)
    }

    @Test
    fun `each chip narrows on its own`() {
        assertEquals(listOf("pinned"), SearchFilters(pinnedOnly = true).apply(list, now).map { it.id })
        assertEquals(listOf("unsorted"), SearchFilters(unsortedOnly = true).apply(list, now).map { it.id })
        assertEquals(listOf("pinned", "unsorted"), SearchFilters(thisWeek = true).apply(list, now).map { it.id })
        assertEquals(listOf("old"), SearchFilters(unreadOnly = true).apply(list, now).map { it.id })
        assertEquals(listOf("unsorted"), SearchFilters(brokenOnly = true).apply(list, now, brokenIds = setOf("unsorted")).map { it.id })
    }

    @Test
    fun `chips combine`() {
        assertEquals(emptyList<String>(), SearchFilters(pinnedOnly = true, unsortedOnly = true).apply(list, now).map { it.id })
    }

    @Test
    fun `recent searches are newest first, deduped and capped`() {
        var recents = emptyList<String>()
        for (q in listOf("a", "b", "A ")) recents = RecentSearches.add(recents, q)
        assertEquals(listOf("A", "b"), recents)
        for (i in 1..20) recents = RecentSearches.add(recents, "q$i")
        assertEquals(RecentSearches.MAX, recents.size)
        assertEquals("q20", recents.first())
        assertEquals(recents, RecentSearches.add(recents, "   "))
    }
}
