package com.bookmark.metadata.http

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * The URL arithmetic behind the redirect loop, kept pure so it can be tested
 * without a socket.
 *
 * [cleartextBlocked] mirrors `usesCleartextTraffic="false"` in the manifest:
 * with cleartext blocked, an `http://` hop cannot connect at all, so it is
 * rewritten to `https://` and allowed to fail on its own merits (spec 7.2).
 */
object RequestUrls {

    /** Returns null for anything that is not, and cannot become, http(s). */
    fun normalizeForRequest(url: String, cleartextBlocked: Boolean): String? = try {
        val trimmed = url.trim()
        val uri = URI(trimmed)
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "https" -> trimmed
            "http" -> if (cleartextBlocked) upgrade(uri) else trimmed
            null -> if (trimmed.isEmpty()) null else "https://$trimmed"
            else -> null
        }
    } catch (e: URISyntaxException) {
        null
    }

    /** Resolves a `Location` header against the URL that produced it. */
    fun resolveRedirect(base: String, location: String, cleartextBlocked: Boolean): String? = try {
        normalizeForRequest(URI(base).resolve(location.trim()).toString(), cleartextBlocked)
    } catch (e: URISyntaxException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun upgrade(uri: URI): String =
        URI("https", uri.userInfo, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
}
