package com.bookmark.bookmarks.home

import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurpriseTest {
    private fun bookmark(id: String, createdAt: Long) = Bookmark(
        id = id, url = "https://example.com/$id", originalUrl = "https://example.com/$id", title = id,
        description = null, siteName = null, thumbnailPath = null, faviconPath = null, accentColor = null,
        thumbnailWidth = null, thumbnailHeight = null, imageCandidates = emptyList(), categoryId = "unsorted",
        metadataState = MetadataState.SUCCESS, failureCause = null, fetchAttempts = 0, lastFetchAt = null,
        manualFields = ManualField.NONE, isPinned = false, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun `empty list gives nothing and a single bookmark is returned`() {
        assertNull(pickSurprise(emptyList()))
        assertEquals("a", pickSurprise(listOf(bookmark("a", 1)))?.id)
    }

    @Test
    fun `only picks from the older half`() {
        val list = listOf(bookmark("d", 4), bookmark("a", 1), bookmark("c", 3), bookmark("b", 2))
        repeat(50) { seed ->
            assertTrue(pickSurprise(list, Random(seed))?.id in setOf("a", "b"))
        }
    }
}
