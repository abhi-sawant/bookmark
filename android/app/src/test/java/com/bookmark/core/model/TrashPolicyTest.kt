package com.bookmark.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashPolicyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `cutoff is thirty days back`() {
        assertEquals(100 * day - 30 * day, TrashPolicy.cutoff(100 * day))
    }

    @Test
    fun `days left rounds up and never goes negative`() {
        val now = 100 * day
        assertEquals(30, TrashPolicy.daysLeft(trashedAt = now, now = now))
        assertEquals(30, TrashPolicy.daysLeft(trashedAt = now - 1, now = now))
        assertEquals(1, TrashPolicy.daysLeft(trashedAt = now - 29 * day - 1, now = now))
        assertEquals(0, TrashPolicy.daysLeft(trashedAt = now - 30 * day, now = now))
        assertEquals(0, TrashPolicy.daysLeft(trashedAt = now - 90 * day, now = now))
    }
}
