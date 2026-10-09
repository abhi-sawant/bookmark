package com.bookmark.metadata.parse

import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.util.TitleFallback
import com.bookmark.core.util.UrlNormalizer
import com.bookmark.metadata.PageMetadata
import java.net.URI
import java.net.URISyntaxException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Turns a parsed `<head>` into a [PageMetadata] (spec 7.3).
 *
 * Pure JVM -- Jsoup has no Android dependencies -- so the whole precedence chain
 * is testable against the saved fixtures in `test/resources/fixtures/` without a
 * network or an emulator. That matters more here than anywhere else in the app:
 * spec 12 singles this out as the part where real-world surprises live.
 */
object MetadataParser {

    /** Spec 7.3: keep up to five for the "choose another image" picker. */
    const val MAX_IMAGE_CANDIDATES = 5

    /**
     * `\s` in Java does not cover U+00A0, and `&nbsp;` in a title is extremely
     * common, so it is spelled out.
     */
    private val WHITESPACE = Regex("[\\s\\u00A0]+")

    fun parse(document: Document, finalUrl: String): PageMetadata {
        // A <base href> retargets every relative URL on the page, including the
        // og:image ones, and it may itself be relative to the document.
        val baseUrl = document.selectFirst("base[href]")
            ?.attr("href")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { resolve(finalUrl, it) }
            ?: finalUrl

        val jsonLd = JsonLd.extract(document)
        val siteName = resolveSiteName(document, jsonLd, finalUrl)

        return PageMetadata(
            title = resolveTitle(document, jsonLd, siteName, finalUrl)
                ?.cap(BookmarkLimits.TITLE_MAX),
            description = resolveDescription(document, jsonLd)
                ?.cap(BookmarkLimits.DESCRIPTION_MAX),
            siteName = siteName?.cap(BookmarkLimits.SITE_NAME_MAX),
            imageCandidates = resolveImages(document, jsonLd, baseUrl),
            // Favicons are deliberately not extracted: the design uses a
            // category-colour dot everywhere and never a site icon, so fetching
            // one would cost a request per bookmark that nothing renders.
            faviconUrl = null,
            finalUrl = finalUrl,
        )
    }

    // -- title ---------------------------------------------------------------

    private fun resolveTitle(
        document: Document,
        jsonLd: JsonLd.Fields,
        siteName: String?,
        finalUrl: String,
    ): String? {
        val raw = meta(document, "og:title")
            ?: meta(document, "twitter:title")
            ?: jsonLd.headline?.clean()
            ?: document.title().clean()
            ?: return null

        // Spec 7.3 attaches suffix-stripping to <title> alone, on the assumption
        // that the social tags are authored for sharing and already omit it. The
        // fixtures say otherwise -- MDN and Wikipedia both ship the full
        // "Title | Site" string in og:title -- so it is applied to whichever
        // source won. TitleSuffix only strips a tail that demonstrably names the
        // site, so running it more often costs nothing.
        return TitleSuffix.strip(raw, siteName, finalUrl).clean()
    }

    // -- description ---------------------------------------------------------

    private fun resolveDescription(document: Document, jsonLd: JsonLd.Fields): String? {
        meta(document, "og:description")?.let { return it }
        meta(document, "twitter:description")?.let { return it }
        meta(document, "description")?.let { return it }
        // Absent is not an error -- the card collapses (spec 8.4).
        return jsonLd.description?.clean()
    }

    // -- site name -----------------------------------------------------------

    private fun resolveSiteName(
        document: Document,
        jsonLd: JsonLd.Fields,
        finalUrl: String,
    ): String? = meta(document, "og:site_name")
        ?: jsonLd.publisherName?.clean()
        // TitleFallback.fromDomain, not the bare registrable domain: it consults
        // the bundled host map first, so ogp.me stays "Open Graph Protocol"
        // rather than being overwritten with "ogp.me" by the first fetch. This
        // is also exactly what BookmarkRepository.save writes at save time, so
        // the two agree.
        ?: TitleFallback.fromDomain(finalUrl)

    // -- images --------------------------------------------------------------

    private fun resolveImages(
        document: Document,
        jsonLd: JsonLd.Fields,
        baseUrl: String,
    ): List<String> {
        val candidates = buildList {
            // Secure variant first: it is the same asset over https, and
            // usesCleartextTraffic=false means the http spelling cannot load.
            addAll(metaAll(document, "og:image:secure_url"))
            addAll(metaAll(document, "og:image"))
            addAll(metaAll(document, "og:image:url"))
            addAll(metaAll(document, "twitter:image"))
            addAll(metaAll(document, "twitter:image:src"))
            addAll(jsonLd.images.mapNotNull { it.clean() })
            addAll(iconsBySizeDescending(document, "apple-touch-icon"))
            addAll(iconsBySizeDescending(document, "icon"))
        }

        return candidates
            .mapNotNull { resolve(baseUrl, it) }
            .distinct()
            .take(MAX_IMAGE_CANDIDATES)
    }

    /**
     * `<link rel="apple-touch-icon" sizes="180x180">`, largest first. An icon
     * with no `sizes` sorts last rather than being dropped -- it is still better
     * than nothing, which is the next step down the chain.
     */
    private fun iconsBySizeDescending(document: Document, rel: String): List<String> =
        document.select("link[rel~=(?i)^$rel$][href]")
            .mapNotNull { element ->
                val href = element.attr("href").clean() ?: return@mapNotNull null
                href to element.longestEdge()
            }
            .sortedByDescending { it.second }
            .map { it.first }

    private fun Element.longestEdge(): Int {
        val sizes = attr("sizes").lowercase()
        if (sizes.isBlank() || sizes == "any") return 0
        return sizes.split(' ')
            .mapNotNull { token ->
                token.split('x').mapNotNull(String::toIntOrNull).maxOrNull()
            }
            .maxOrNull() ?: 0
    }

    // -- helpers -------------------------------------------------------------

    /**
     * Open Graph specifies `property`, Twitter specifies `name`, and a large
     * minority of real pages use the other one. Both are accepted for both.
     */
    private fun meta(document: Document, key: String): String? =
        metaAll(document, key).firstOrNull()

    private fun metaAll(document: Document, key: String): List<String> =
        document.select("meta[property=$key], meta[name=$key]")
            .mapNotNull { it.attr("content").clean() }

    /** Entity-decoded by Jsoup already; this collapses and trims what is left. */
    private fun String.clean(): String? =
        replace(WHITESPACE, " ").trim().ifBlank { null }

    private fun String.cap(max: Int): String = if (length <= max) this else take(max).trimEnd()

    /**
     * Resolves [reference] against [base]. Protocol-relative `//host/path` is
     * common in og:image and [URI.resolve] handles it, inheriting the scheme.
     */
    private fun resolve(base: String, reference: String): String? = try {
        val resolved = URI(base).resolve(reference)
        resolved.toString().takeIf { resolved.scheme != null && resolved.host != null }
    } catch (e: URISyntaxException) {
        null
    } catch (e: IllegalArgumentException) {
        // URI.resolve rejects some malformed references outright.
        null
    }
}
