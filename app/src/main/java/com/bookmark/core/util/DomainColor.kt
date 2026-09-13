package com.bookmark.core.util

/**
 * Deterministic colour assignment for generated monogram tiles (spec 8.3).
 *
 * The same site always lands on the same swatch, which is what makes a grid
 * full of fallback tiles scannable rather than noise. Returns an index into the
 * curated palette so this stays free of any UI dependency.
 */
object DomainColor {

    /** Number of entries in the palette this indexes into. */
    const val PALETTE_SIZE = 8

    fun indexFor(url: String): Int {
        val key = UrlNormalizer.registrableDomain(url)
            ?: UrlNormalizer.host(url)
            ?: url
        return indexForKey(key)
    }

    fun indexForKey(key: String): Int {
        // FNV-1a: cheap, well-distributed over short strings, and stable across
        // platforms and releases in a way String.hashCode() is not guaranteed to be.
        // Kept in signed Int (which wraps on overflow, as FNV wants) rather than
        // UInt -- the Kotlin 2.2 const evaluator crashes folding UInt conversions.
        var hash = FNV_OFFSET_BASIS
        for (char in key) {
            hash = hash xor char.code
            hash *= FNV_PRIME
        }
        return ((hash % PALETTE_SIZE) + PALETTE_SIZE) % PALETTE_SIZE
    }

    private const val FNV_OFFSET_BASIS: Int = -2128831035 // 2166136261 as a signed Int
    private const val FNV_PRIME: Int = 16777619
}
