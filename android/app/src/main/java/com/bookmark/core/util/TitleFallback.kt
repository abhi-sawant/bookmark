package com.bookmark.core.util

import java.net.URLDecoder
import java.util.Locale

/**
 * The title fallback chain (spec 8.2). Applied in order until one produces a
 * non-empty string; the last step is the raw URL, so the chain always
 * terminates and a saved bookmark can never show "couldn't load".
 */
object TitleFallback {

    /** File extensions dropped before a path segment becomes a title. */
    private val EXTENSIONS = setOf(
        "html", "htm", "php", "asp", "aspx", "jsp", "md", "txt", "pdf", "amp",
    )

    /** Path segments that carry no meaning as a title. */
    private val NOISE_SEGMENTS = setOf(
        "index", "default", "home", "post", "posts", "article", "articles",
        "blog", "p", "s", "a", "en", "amp", "view", "watch", "story",
        // Aggregators whose entire path is a router, with the identity in the
        // query string: news.ycombinator.com/item?id=... should title itself
        // "Hacker News", not "Item".
        "item", "items", "comments", "status", "video", "photo", "thread",
    )

    /** Trailing `-1234` / `_1234` style ids and bare hashes. */
    private val TRAILING_ID = Regex("[-_][0-9a-f]{4,}$", RegexOption.IGNORE_CASE)

    fun resolve(
        fetchedTitle: String? = null,
        sharedSubject: String? = null,
        url: String,
    ): String {
        fetchedTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        sharedSubject?.trim()
            ?.takeIf { it.isNotEmpty() && it != url && !it.equals(url.trim(), ignoreCase = true) }
            ?.let { return it }

        fromPath(url)?.let { return it }

        fromDomain(url)?.let { return it }

        return url
    }

    /**
     * `example.com/blog/how-to-build-an-app-1234` becomes "How To Build An App".
     */
    fun fromPath(url: String): String? {
        val path = try {
            val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(url)) {
                url
            } else {
                "https://$url"
            }
            java.net.URI(withScheme).rawPath
        } catch (e: java.net.URISyntaxException) {
            null
        } ?: return null

        val segments = path.split('/').filter { it.isNotBlank() }
        // Walk backwards: the last segment is often an id or a noise word.
        val candidate = segments.asReversed().firstNotNullOfOrNull { raw ->
            val decoded = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (e: IllegalArgumentException) {
                raw
            }
            val withoutExtension = decoded.substringBeforeLast('.', decoded).let { stem ->
                if (decoded.substringAfterLast('.', "").lowercase(Locale.ROOT) in EXTENSIONS) {
                    stem
                } else {
                    decoded
                }
            }
            val withoutId = withoutExtension.replace(TRAILING_ID, "")
            val words = withoutId.split('-', '_', '+')
                .filter { it.isNotBlank() }
                .filterNot { it.all(Char::isDigit) }
            when {
                words.isEmpty() -> null
                words.size == 1 && words.single().lowercase(Locale.ROOT) in NOISE_SEGMENTS -> null
                words.size == 1 && words.single().length <= 2 -> null
                else -> words
            }
        } ?: return null

        return candidate.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }.takeIf { it.isNotBlank() }
    }

    /** `news.ycombinator.com` becomes "Hacker News", `example.com` becomes "Example". */
    fun fromDomain(url: String): String? {
        val host = UrlNormalizer.host(url) ?: return null
        KnownHosts.displayName(host)?.let { return it }

        val registrable = UrlNormalizer.registrableDomain(url) ?: host
        val name = registrable.substringBefore('.')
        if (name.isBlank()) return null
        return name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }

    /**
     * The one or two letters shown on a generated monogram tile (spec 8.3).
     */
    fun monogram(url: String): String {
        val host = UrlNormalizer.host(url)
        val source = KnownHosts.displayName(host)
            ?: UrlNormalizer.registrableDomain(url)?.substringBefore('.')
            ?: host
            ?: return "?"

        val words = source.split(' ', '-', '.').filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.ROOT)
            words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase(Locale.ROOT)
            words.size == 1 -> words[0].take(1).uppercase(Locale.ROOT)
            else -> "?"
        }
    }
}
