package com.bookmark.metadata.image

/**
 * The decisions in the thumbnail pipeline (spec 7.4), separated from the Android
 * bitmap calls that carry them out.
 *
 * Robolectric's Bitmap shadows are fakes and cannot validate real decoding or
 * WebP encoding, so the arithmetic and the accept/reject rules live here where
 * plain JUnit can cover them, and [ThumbnailPipeline] is left with only the
 * platform calls -- which an instrumented test exercises on a real device.
 */
object ThumbnailPolicy {

    /**
     * Below this on either edge the "image" is almost always a tracking pixel or
     * a logo masquerading as an og:image (spec 7.4 step 2).
     */
    const val MIN_DIMENSION = 64

    /** Longest edge after downscaling (spec 7.4 step 4). */
    const val MAX_EDGE = 1080

    /** Hard cap on a candidate download (spec 7.4 step 1). */
    const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024

    /** WebP encode quality (spec 7.4 step 5) -- typically 40-90KB per thumbnail. */
    const val WEBP_QUALITY = 80

    fun isAcceptable(width: Int, height: Int): Boolean =
        width >= MIN_DIMENSION && height >= MIN_DIMENSION

    /**
     * Target dimensions preserving aspect ratio. Images already within [MAX_EDGE]
     * are returned untouched rather than re-encoded up.
     */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "dimensions must be positive: ${width}x$height" }

        val longest = maxOf(width, height)
        if (longest <= MAX_EDGE) return width to height

        val scale = MAX_EDGE.toDouble() / longest
        // Round rather than truncate, and never let the short edge collapse to 0
        // on an extreme aspect ratio (a 4000x30 banner would otherwise floor out).
        return maxOf(1, Math.round(width * scale).toInt()) to
            maxOf(1, Math.round(height * scale).toInt())
    }

    /**
     * `BitmapFactory.Options.inSampleSize` for the initial decode: the largest
     * power of two that still leaves the image at or above the target, so a
     * 4000px source never has to be fully materialised in memory first.
     */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_EDGE && height / (sample * 2) >= 1) {
            sample *= 2
        }
        return sample
    }
}
