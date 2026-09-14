package com.bookmark.metadata.parse

import com.bookmark.core.util.KnownHosts
import com.bookmark.core.util.UrlNormalizer
import java.util.Locale

/**
 * Strips the trailing site-name suffix from a `<title>` (spec 7.3 step 4).
 *
 * Only `<title>` needs this. `og:title` is authored for sharing and almost never
 * carries the suffix, which is exactly why it sits above `<title>` in the
 * precedence chain.
 *
 * The suffix is only removed when it demonstrably names the site -- matching
 * `og:site_name`, the host, the registrable domain, or that domain's stem.
 * Anything else is left alone: "Kotlin - The Programming Language" must not
 * become "Kotlin", and a headline that happens to contain a dash must survive
 * intact.
 */
object TitleSuffix {

    /** Separators the convention actually uses, longest-looking first. */
    private val SEPARATORS = listOf(" | ", " — ", " – ", " - ", " · ", " :: ")

    /** Anything that is not a letter or digit separates words in a site name. */
    private val WORD_BOUNDARY = Regex("[^\\p{L}\\p{N}]+")

    fun strip(title: String, siteName: String?, url: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return trimmed

        val candidates = siteCandidates(siteName, url)
        if (candidates.isEmpty()) return trimmed

        // Split at the LAST separator: "Foo - Bar | Site" should lose only " | Site".
        for (separator in SEPARATORS) {
            val index = trimmed.lastIndexOf(separator)
            if (index <= 0) continue

            val head = trimmed.substring(0, index).trim()
            val tail = trimmed.substring(index + separator.length).trim()
            if (head.isEmpty() || tail.isEmpty()) continue

            if (tail.lowercase(Locale.ROOT) in candidates) return head
            if (matchesSiteNamePartially(tail, siteName)) return head
        }
        return trimmed
    }

    /**
     * `og:site_name` is often longer than the suffix that names it: MDN Web Docs
     * titles end in "| MDN". A whole-word prefix or suffix of the authored site
     * name is accepted, which is safe because `og:site_name` is deliberate --
     * unlike the host, where a partial match would strip "Face" off facebook.com.
     */
    private fun matchesSiteNamePartially(tail: String, siteName: String?): Boolean {
        if (siteName.isNullOrBlank()) return false
        val site = siteName.lowercase(Locale.ROOT).split(WORD_BOUNDARY).filter { it.isNotBlank() }
        val words = tail.lowercase(Locale.ROOT).split(WORD_BOUNDARY).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > site.size) return false
        return site.take(words.size) == words || site.takeLast(words.size) == words
    }

    /** Every spelling of "this site" worth matching a suffix against. */
    private fun siteCandidates(siteName: String?, url: String): Set<String> {
        val out = mutableSetOf<String>()

        siteName?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it.lowercase(Locale.ROOT) }

        UrlNormalizer.host(url)?.let { host ->
            out += host
            out += host.removePrefix("www.")
            // The bundled host map is the site's own name for itself, which is
            // exactly the spelling a title suffix uses: "... | Hacker News".
            KnownHosts.displayName(host)?.let { out += it.lowercase(Locale.ROOT) }
        }
        UrlNormalizer.registrableDomain(url)?.let { registrable ->
            out += registrable
            // "example.com" also legitimises a bare "Example" suffix.
            registrable.substringBefore('.').takeIf { it.isNotBlank() }?.let { out += it }
        }

        return out
    }
}
