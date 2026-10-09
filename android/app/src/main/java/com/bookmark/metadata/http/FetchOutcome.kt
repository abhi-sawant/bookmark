package com.bookmark.metadata.http

import com.bookmark.metadata.FailureCause
import org.jsoup.nodes.Document

/**
 * What one URL turned out to be. The content-type gate in spec 7.2 splits the
 * three shapes spec 7.5 handles differently, so the distinction is made once
 * here rather than re-sniffed downstream.
 */
sealed interface FetchOutcome {

    /** `text/html` or `application/xhtml+xml`, parsed through `</head>`. */
    data class Html(val document: Document, val finalUrl: String) : FetchOutcome

    /** The URL *is* the image (spec 7.5). */
    data class Image(val finalUrl: String) : FetchOutcome

    /** Title comes from the filename; no rendering in v1 (spec 7.5). */
    data class Pdf(val finalUrl: String) : FetchOutcome

    data class Failure(val cause: FailureCause) : FetchOutcome
}
