package com.bookmark.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagsTest {
    @Test
    fun `normalize trims lowercases strips hash and dedupes`() {
        assertEquals(listOf("read later", "kotlin"), Tags.normalize(listOf("  #Read   Later ", "KOTLIN", "kotlin", "#", " ")))
    }

    @Test
    fun `parse splits on commas and new lines`() {
        assertEquals(listOf("a", "b", "c"), Tags.parse("a, b\nc,,"))
    }

    @Test
    fun `normalize caps length and count`() {
        val long = "x".repeat(50)
        assertEquals(Tags.MAX_LENGTH, Tags.normalize(listOf(long)).single().length)
        assertEquals(Tags.MAX_COUNT, Tags.normalize((1..25).map { "t$it" }).size)
    }

    @Test
    fun `encode and decode round trip`() {
        val tags = listOf("one", "two words")
        assertEquals(tags, Tags.decode(Tags.encode(tags)))
        assertNull(Tags.encode(emptyList()))
        assertEquals(emptyList<String>(), Tags.decode(null))
    }
}
