package com.bookmark.metadata.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPolicyTest {

    @Test
    fun `rejects anything under 64px on either edge`() {
        assertFalse(ThumbnailPolicy.isAcceptable(1, 1))          // tracking pixel
        assertFalse(ThumbnailPolicy.isAcceptable(1200, 40))      // a thin banner
        assertFalse(ThumbnailPolicy.isAcceptable(48, 48))        // a favicon
        assertTrue(ThumbnailPolicy.isAcceptable(64, 64))
        assertTrue(ThumbnailPolicy.isAcceptable(1200, 630))
    }

    @Test
    fun `leaves images already within the long edge untouched`() {
        assertEquals(800 to 600, ThumbnailPolicy.targetSize(800, 600))
        assertEquals(1080 to 1080, ThumbnailPolicy.targetSize(1080, 1080))
    }

    @Test
    fun `scales the longest edge to 1080 preserving aspect ratio`() {
        assertEquals(1080 to 567, ThumbnailPolicy.targetSize(2400, 1260))
        assertEquals(540 to 1080, ThumbnailPolicy.targetSize(1000, 2000))
    }

    @Test
    fun `never collapses the short edge to zero on an extreme ratio`() {
        val (width, height) = ThumbnailPolicy.targetSize(8000, 70)
        assertEquals(1080, width)
        assertTrue("short edge collapsed to $height", height >= 1)
    }

    @Test
    fun `sample size stays at 1 for images near the target`() {
        assertEquals(1, ThumbnailPolicy.sampleSize(1200, 630))
        assertEquals(1, ThumbnailPolicy.sampleSize(1080, 1080))
    }

    @Test
    fun `sample size halves while both edges stay above the target`() {
        assertEquals(2, ThumbnailPolicy.sampleSize(4000, 3000))
        assertEquals(4, ThumbnailPolicy.sampleSize(8000, 6000))
    }

    @Test
    fun `target size rejects non-positive dimensions`() {
        runCatching { ThumbnailPolicy.targetSize(0, 100) }
            .onSuccess { error("expected a failure for a zero dimension") }
    }
}
