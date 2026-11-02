package com.bookmark.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryNamesTest {
    @Test
    fun `a free name is kept`() {
        assertEquals("Reels", CategoryNames.unique("Reels", CategoryNames.takenIn(listOf("Books"))))
    }

    @Test
    fun `a taken name gets the next free suffix, ignoring case`() {
        assertEquals("Reels (2)", CategoryNames.unique("Reels", CategoryNames.takenIn(listOf("reels"))))
        assertEquals("Reels (3)", CategoryNames.unique("Reels", CategoryNames.takenIn(listOf("Reels", "REELS (2)"))))
    }

    @Test
    fun `the suffix never pushes a name past the limit`() {
        val long = "x".repeat(40)
        val result = CategoryNames.unique(long, CategoryNames.takenIn(listOf(long)))
        assertEquals(40, result.length)
        assertEquals(true, result.endsWith(" (2)"))
    }
}
