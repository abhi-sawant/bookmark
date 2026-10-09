package com.bookmark.metadata.special

import com.bookmark.core.util.UrlNormalizer
import java.net.URI
import java.net.URISyntaxException

/**
 * YouTube thumbnails without trusting the page (spec 7.5).
 *
 * `i.ytimg.com/vi/{id}/…` is derivable from the URL alone, so the preview image
 * is guaranteed even when the watch page changes shape or blocks us. `hqdefault`
 * always exists; `maxresdefault` does not exist for every video, which is why
 * both are offered and the pipeline falls through on a 404.
 *
 * Spec 7.5 says to skip the watch page entirely. We still read its head, because
 * skipping it costs the title -- `/watch` is a noise segment, so the fallback
 * chain would name every video "YouTube". The head read is cheap: section 7.2
 * aborts at `</head>`, so this is tens of KB, not the multi-megabyte page.
 */
object YouTube {

    /** YouTube ids are exactly 11 characters of URL-safe base64. */
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private val HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com",
        "youtu.be", "www.youtu.be",
    )

    /** Path prefixes that carry the id as the next segment. */
    private val ID_BEARING_SEGMENTS = setOf("shorts", "embed", "live", "v")

    fun videoId(url: String): String? {
        val host = UrlNormalizer.host(url) ?: return null
        if (host !in HOSTS && "youtube.com" != UrlNormalizer.registrableDomain(url) &&
            "youtu.be" != UrlNormalizer.registrableDomain(url)
        ) {
            return null
        }

        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }

        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

        // youtu.be/{id}
        if (host.removePrefix("www.") == "youtu.be") {
            return segments.firstOrNull()?.takeIf { VIDEO_ID.matches(it) }
        }

        // youtube.com/watch?v={id}
        if (segments.firstOrNull() == "watch") {
            return queryParam(uri.rawQuery, "v")?.takeIf { VIDEO_ID.matches(it) }
        }

        // youtube.com/shorts/{id}, /embed/{id}, /live/{id}, /v/{id}
        if (segments.size >= 2 && segments[0] in ID_BEARING_SEGMENTS) {
            return segments[1].takeIf { VIDEO_ID.matches(it) }
        }

        return null
    }

    /** Best first. Both are tried in order by the thumbnail pipeline. */
    fun thumbnailCandidates(videoId: String): List<String> = listOf(
        "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
    )

    private fun queryParam(rawQuery: String?, key: String): String? =
        rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=')
            if (name == key) pair.substringAfter('=', "").ifBlank { null } else null
        }
}
