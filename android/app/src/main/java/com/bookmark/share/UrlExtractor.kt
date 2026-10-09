package com.bookmark.share

/**
 * Pulls a URL out of shared text (spec 6.2).
 *
 * Shared text is rarely a bare URL: WhatsApp sends "Check this out https://…",
 * YouTube sends a title plus the link plus a promo line.
 *
 * The spec names `android.util.Patterns.WEB_URL`; this uses an equivalent
 * pattern in pure Kotlin instead, so the extraction rules -- which are where the
 * real-world surprises live -- are unit-testable on the JVM without Robolectric.
 */
object UrlExtractor {

    private val WEB_URL = Regex(
        """(?:(?:https?|ftp)://)?""" +
            """(?:[\w-]+(?::[^\s@]*)?@)?""" +
            """(?:[\w-]+\.)+[a-zA-Z]{2,}""" +
            """(?::\d{1,5})?""" +
            """(?:/[^\s<>"'\)\]]*)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Trailing punctuation that is almost always sentence, not URL. */
    private const val TRAILING_JUNK = ".,;:!?)]}”\"'"

    data class ExtractedShare(
        /** First match, or null when the text held no URL at all. */
        val primaryUrl: String?,
        /** Any further matches, shown under "Other links in this share". */
        val otherUrls: List<String>,
        /** EXTRA_SUBJECT, held as a fallback title candidate (spec 8.2 step 2). */
        val subjectTitle: String?,
        /** The text as shared, so a zero-match share can still populate the field. */
        val rawText: String,
    ) {
        val hasUrl: Boolean get() = primaryUrl != null
    }

    fun extract(text: String?, subject: String? = null): ExtractedShare {
        val raw = text.orEmpty().trim()
        val matches = WEB_URL.findAll(raw)
            .map { it.value.trimEnd { char -> char in TRAILING_JUNK } }
            .filter { it.isNotBlank() && looksLikeHost(it) }
            .distinct()
            .toList()

        val cleanedSubject = subject?.trim()
            ?.takeIf { it.isNotEmpty() && matches.none { url -> it.equals(url, ignoreCase = true) } }

        return ExtractedShare(
            primaryUrl = matches.firstOrNull(),
            otherUrls = matches.drop(1),
            subjectTitle = cleanedSubject,
            rawText = raw,
        )
    }

    /**
     * Guards against decimals and version strings ("v1.2", "3.14") matching as
     * bare hostnames, which the permissive pattern would otherwise accept.
     */
    private fun looksLikeHost(candidate: String): Boolean {
        val withoutScheme = candidate.substringAfter("://", candidate)
        val host = withoutScheme.substringBefore('/').substringBefore(':').substringAfter('@')
        val labels = host.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        return tld.length >= 2 && tld.all(Char::isLetter) && labels.none { it.isEmpty() }
    }
}
