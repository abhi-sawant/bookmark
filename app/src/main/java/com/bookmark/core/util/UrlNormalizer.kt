package com.bookmark.core.util

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * URL normalisation, spec 7.1.
 *
 * The normalised form is what gets stored and what the unique index dedupes on;
 * the original is kept alongside it for the rare case where normalising breaks
 * a link. Pure Kotlin, no Android dependencies, so it unit-tests directly.
 */
object UrlNormalizer {

    /**
     * Exact parameter names stripped from every URL. Deliberately conservative:
     * dropping `?v=` on YouTube or `?p=` on WordPress would break the link, so
     * anything not listed here (or matching [TRACKING_PREFIXES]) is preserved.
     */
    private val TRACKING_PARAMS = setOf(
        "fbclid", "gclid", "msclkid", "igshid", "mc_eid", "mc_cid",
        "ref_src", "ref_url", "si", "_ga", "yclid", "dclid", "twclid",
    )

    private val TRACKING_PREFIXES = listOf("utm_")

    /**
     * Hosts that route on the fragment, where stripping `#...` would lose the
     * actual destination.
     */
    private val HASH_ROUTING_HOSTS = setOf(
        "groups.google.com",
        "mail.google.com",
        "drive.google.com",
        "docs.google.com",
        "web.archive.org",
    )

    private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)

    /**
     * Returns the normalised URL, or the trimmed input unchanged if it cannot be
     * parsed. Never throws -- a bookmark is always saveable (design principle 1).
     */
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed

        val withScheme = if (SCHEME_REGEX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            return trimmed
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return trimmed
        val host = uri.host?.lowercase(Locale.ROOT) ?: return trimmed

        val port = if (uri.port == -1 || DEFAULT_PORTS[scheme] == uri.port) -1 else uri.port
        val query = stripTracking(uri.rawQuery)
        val path = uri.rawPath.orEmpty()
        val fragment = if (host in HASH_ROUTING_HOSTS) uri.rawFragment else null

        // Trailing slash is only noise on a bare host; elsewhere it can be meaningful.
        // A URL carrying a query is not bare -- dropping the slash there would
        // produce https://host?q=1, which is legal but not what anyone wrote.
        val normalizedPath = when {
            path.isEmpty() -> if (query.isNullOrEmpty()) "" else "/"
            path == "/" -> if (query.isNullOrEmpty()) "" else "/"
            else -> path.trimEnd('/').ifEmpty { path }
        }

        return buildString {
            append(scheme).append("://").append(host)
            if (port != -1) append(':').append(port)
            append(normalizedPath)
            if (!query.isNullOrEmpty()) append('?').append(query)
            if (!fragment.isNullOrEmpty()) append('#').append(fragment)
        }
    }

    /** Syntactic validity only -- enough to enable Save (spec 5.2). */
    fun isValid(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val withScheme = if (SCHEME_REGEX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return try {
            val uri = URI(withScheme)
            val host = uri.host
            uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !host.isNullOrBlank() &&
                host.contains('.') &&
                !host.startsWith('.') &&
                !host.endsWith('.')
        } catch (e: URISyntaxException) {
            false
        }
    }

    /** Host with any `www.` prefix removed, or null when the URL will not parse. */
    fun host(url: String): String? = try {
        val withScheme = if (SCHEME_REGEX.containsMatchIn(url)) url else "https://$url"
        URI(withScheme).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    } catch (e: URISyntaxException) {
        null
    }

    /**
     * Registrable domain -- `news.ycombinator.com` to `ycombinator.com`.
     *
     * Uses a bundled list of two-part public suffixes rather than a full Public
     * Suffix List: the PSL is ~230KB and the only consumers here are a display
     * label and a colour hash, where an occasional wrong split is harmless.
     */
    fun registrableDomain(url: String): String? {
        val host = host(url) ?: return null
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in TWO_PART_SUFFIXES && labels.size >= 3) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    private fun stripTracking(rawQuery: String?): String? {
        if (rawQuery.isNullOrEmpty()) return rawQuery
        val kept = rawQuery.split('&').filter { pair ->
            if (pair.isEmpty()) return@filter false
            val key = pair.substringBefore('=').lowercase(Locale.ROOT)
            key !in TRACKING_PARAMS && TRACKING_PREFIXES.none { key.startsWith(it) }
        }
        return kept.joinToString("&").ifEmpty { null }
    }

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    private val TWO_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "co.nz", "net.nz", "org.nz", "govt.nz",
        "co.in", "net.in", "org.in", "gov.in", "ac.in",
        "com.br", "net.br", "org.br", "gov.br",
        "co.za", "org.za", "net.za",
        "com.cn", "net.cn", "org.cn", "gov.cn",
        "com.sg", "com.hk", "com.tw", "com.mx", "com.ar", "com.tr",
        "co.kr", "or.kr", "ne.kr",
        "github.io", "gitlab.io", "pages.dev", "vercel.app", "netlify.app",
    )
}
