package com.bookmark.widget

import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRowsTest {
    private fun bookmark(id: String, pinned: Boolean, createdAt: Long) = Bookmark(
        id = id, url = "https://example.com/$id", originalUrl = "https://example.com/$id", title = id,
        description = null, siteName = null, thumbnailPath = null, faviconPath = null, accentColor = null,
        thumbnailWidth = null, thumbnailHeight = null, imageCandidates = emptyList(), categoryId = "unsorted",
        metadataState = MetadataState.SUCCESS, failureCause = null, fetchAttempts = 0, lastFetchAt = null,
        manualFields = ManualField.NONE, isPinned = pinned, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun `pinned first then newest, capped at five`() {
        val list = listOf(
            bookmark("old", false, 1), bookmark("pinnedOld", true, 2), bookmark("new", false, 9),
            bookmark("b", false, 5), bookmark("c", false, 6), bookmark("d", false, 7), bookmark("e", false, 8),
        )
        assertEquals(listOf("pinnedOld", "new", "e", "d", "c"), WidgetRows.pick(list).map { it.id })
    }

    @Test
    fun `fewer than five returns them all`() {
        assertEquals(1, WidgetRows.pick(listOf(bookmark("a", false, 1))).size)
        assertEquals(0, WidgetRows.pick(emptyList()).size)
    }
}
