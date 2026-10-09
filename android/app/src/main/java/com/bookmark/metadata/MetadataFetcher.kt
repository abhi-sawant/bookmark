package com.bookmark.metadata

import com.bookmark.core.model.MetadataState

/**
 * The seam for the metadata engine (spec 7).
 *
 * Deliberately free of Android UI types so the real implementation in M3 is a
 * self-contained, unit-testable module. M0-M2 binds [NoOpMetadataFetcher]; M3
 * swaps the Hilt binding and no screen changes.
 */
interface MetadataFetcher {
    suspend fun fetch(url: String): MetadataResult
}

/** What a page yielded. All strings arrive already decoded, collapsed and capped. */
data class PageMetadata(
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    /** Up to 5, best first -- feeds the "choose another image" picker (spec 7.3). */
    val imageCandidates: List<String> = emptyList(),
    val faviconUrl: String? = null,
    /** Post-redirect URL, used to resolve relative image paths. */
    val finalUrl: String? = null,
)

/**
 * Why a fetch ended where it did. Drives the messages in spec 8.6, which appear
 * only in the bookmark detail sheet -- never as a toast during the save flow.
 */
enum class FailureCause {
    OFFLINE,
    UNREACHABLE,
    TIMEOUT,
    BLOCKED,
    NOT_FOUND,
    SERVER_ERROR,
    NOTHING_USABLE,
    IMAGE_ONLY,
}

sealed interface MetadataResult {
    /** Title and image both resolved. */
    data class Success(val metadata: PageMetadata) : MetadataResult

    /** Some fields resolved. Not an error: the card shows resolved fields and falls back for the rest. */
    data class Partial(val metadata: PageMetadata, val cause: FailureCause?) : MetadataResult

    /** Page reachable but yielded nothing usable. Not an error either. */
    data class Fallback(val cause: FailureCause) : MetadataResult

    /** Hard failure. The only state that earns a retry affordance. */
    data class Failed(val cause: FailureCause) : MetadataResult

    /** Queued -- device offline, or automatic fetching is switched off. */
    data object Pending : MetadataResult

    val state: MetadataState
        get() = when (this) {
            is Success -> MetadataState.SUCCESS
            is Partial -> MetadataState.PARTIAL
            is Fallback -> MetadataState.FALLBACK
            is Failed -> MetadataState.FAILED
            Pending -> MetadataState.PENDING
        }
}

/** User-visible failure text (spec 8.6). Null means the state is shown silently. */
fun FailureCause?.userMessage(): String? = when (this) {
    null -> null
    FailureCause.OFFLINE -> "Preview pending"
    FailureCause.UNREACHABLE -> "Couldn't reach this site"
    FailureCause.TIMEOUT -> "This site took too long to respond"
    FailureCause.BLOCKED -> "This site doesn't share previews"
    FailureCause.NOT_FOUND -> "This page wasn't found"
    FailureCause.SERVER_ERROR -> "This site is having problems"
    FailureCause.NOTHING_USABLE -> null
    FailureCause.IMAGE_ONLY -> null
}
