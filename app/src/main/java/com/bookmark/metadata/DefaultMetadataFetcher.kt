package com.bookmark.metadata

import com.bookmark.core.util.UrlNormalizer
import com.bookmark.metadata.http.FetchOutcome
import com.bookmark.metadata.http.HtmlFetcher
import com.bookmark.metadata.parse.MetadataParser
import com.bookmark.metadata.special.YouTube
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetch, parse and classify -- the whole of spec 7, with no Android UI types.
 *
 * This reports only what the *network* established. Whether a candidate image
 * actually downloads and decodes is the thumbnail pipeline's business, so a
 * [MetadataResult.Success] here means "a title and at least one image candidate
 * were found", and the worker may still demote it to PARTIAL afterwards (spec
 * 8.6, "image download failed only").
 */
@Singleton
class DefaultMetadataFetcher @Inject constructor(
    private val htmlFetcher: HtmlFetcher,
) : MetadataFetcher {

    override suspend fun fetch(url: String): MetadataResult {
        // Derivable from the URL alone, so it survives the page failing entirely.
        val videoId = YouTube.videoId(url)

        return when (val outcome = htmlFetcher.fetch(url)) {
            is FetchOutcome.Html -> classify(
                MetadataParser.parse(outcome.document, outcome.finalUrl)
                    .withYouTubeThumbnails(videoId),
            )

            // The URL is the image (spec 7.5).
            is FetchOutcome.Image -> classify(
                PageMetadata(
                    title = filenameTitle(outcome.finalUrl),
                    siteName = UrlNormalizer.registrableDomain(outcome.finalUrl),
                    imageCandidates = listOf(outcome.finalUrl),
                    finalUrl = outcome.finalUrl,
                ),
            )

            // No PDF rendering in v1: title from the filename, monogram tile for
            // the image. That is PARTIAL, not an error (spec 8.1).
            is FetchOutcome.Pdf -> classify(
                PageMetadata(
                    title = filenameTitle(outcome.finalUrl),
                    siteName = UrlNormalizer.registrableDomain(outcome.finalUrl),
                    finalUrl = outcome.finalUrl,
                ),
            )

            is FetchOutcome.Failure -> if (videoId != null) {
                // The watch page is unreachable but the thumbnail never was.
                MetadataResult.Partial(
                    PageMetadata(
                        siteName = "YouTube",
                        imageCandidates = YouTube.thumbnailCandidates(videoId),
                        finalUrl = url,
                    ),
                    outcome.cause,
                )
            } else {
                outcome.cause.toResult()
            }
        }
    }

    /**
     * Puts the deterministic `i.ytimg.com` URLs ahead of whatever the page
     * advertised, so the preview does not depend on YouTube's markup holding
     * still (spec 7.5).
     */
    private fun PageMetadata.withYouTubeThumbnails(videoId: String?): PageMetadata {
        if (videoId == null) return this
        return copy(
            imageCandidates = (YouTube.thumbnailCandidates(videoId) + imageCandidates)
                .distinct()
                .take(MetadataParser.MAX_IMAGE_CANDIDATES),
        )
    }

    private fun classify(metadata: PageMetadata): MetadataResult {
        val hasTitle = !metadata.title.isNullOrBlank()
        val hasImage = metadata.imageCandidates.isNotEmpty()

        return when {
            hasTitle && hasImage -> MetadataResult.Success(metadata)
            // Resolved fields show, the rest fall back, and no error is surfaced
            // -- PARTIAL is not an error state (spec 8.1).
            hasTitle || hasImage -> MetadataResult.Partial(metadata, null)
            // Reachable, but nothing usable on it. Silent (spec 8.6).
            else -> MetadataResult.Fallback(FailureCause.NOTHING_USABLE)
        }
    }

    /** Spec 8.6, verbatim: which causes are failures and which are just fallbacks. */
    private fun FailureCause.toResult(): MetadataResult = when (this) {
        FailureCause.OFFLINE -> MetadataResult.Pending
        // "This site doesn't share previews" -- expected for X, Instagram and
        // paywalled news, and explicitly not a bug to chase (spec 7.5).
        FailureCause.BLOCKED,
        FailureCause.NOTHING_USABLE,
        FailureCause.IMAGE_ONLY,
        -> MetadataResult.Fallback(this)

        FailureCause.UNREACHABLE,
        FailureCause.TIMEOUT,
        FailureCause.NOT_FOUND,
        FailureCause.SERVER_ERROR,
        -> MetadataResult.Failed(this)
    }

    /** `…/reports/Q3-summary.pdf` becomes "Q3 summary". */
    private fun filenameTitle(url: String): String? {
        val path = try {
            URI(url).path
        } catch (e: URISyntaxException) {
            null
        } ?: return null

        val segment = path.split('/').lastOrNull { it.isNotBlank() } ?: return null
        val decoded = try {
            URLDecoder.decode(segment, "UTF-8")
        } catch (e: IllegalArgumentException) {
            segment
        }
        return decoded.substringBeforeLast('.', decoded)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .ifBlank { null }
    }
}
