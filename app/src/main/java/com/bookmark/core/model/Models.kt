package com.bookmark.core.model

import androidx.compose.runtime.Immutable

/**
 * Lifecycle of a bookmark's fetched metadata (spec 8.1).
 *
 * `PARTIAL` and `FALLBACK` are deliberately *not* error states: the card looks
 * complete and no retry affordance is shown. Only [FAILED] surfaces one.
 */
enum class MetadataState {
    PENDING,
    FETCHING,
    SUCCESS,
    PARTIAL,
    FALLBACK,
    FAILED,
}

/**
 * Bitmask of fields the user has edited by hand. Any bit set here locks that
 * field against automatic overwrite for the life of the bookmark (spec 4.1,
 * design principle 3: the user's edit always wins).
 */
object ManualField {
    const val NONE = 0
    const val TITLE = 1
    const val DESCRIPTION = 2
    const val THUMBNAIL = 4

    fun isSet(mask: Int, field: Int): Boolean = mask and field != 0
    fun set(mask: Int, field: Int): Int = mask or field
    fun clear(mask: Int, field: Int): Int = mask and field.inv()
}

/**
 * The name of a `com.bookmark.metadata.FailureCause`, as persisted.
 *
 * A typealias rather than the enum itself so the model and persistence layers
 * do not depend on the metadata package -- the engine is meant to be
 * self-contained (spec 7), and the UI only ever needs this to look up a message.
 */
typealias FailureCauseName = String

@Immutable
data class Bookmark(
    val id: String,
    val url: String,
    val originalUrl: String,
    val title: String,
    val description: String?,
    val siteName: String?,
    val thumbnailPath: String?,
    val faviconPath: String?,
    val accentColor: Int?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    /** Up to five image URLs the parser found, for the thumbnail picker. */
    val imageCandidates: List<String>,
    val categoryId: String,
    val metadataState: MetadataState,
    val failureCause: FailureCauseName?,
    val fetchAttempts: Int,
    val lastFetchAt: Long?,
    val manualFields: Int,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Width over height of the stored thumbnail, or null when there is no image
     * to size to. Lets the staggered grid lay the card out from the database
     * rather than by decoding the file during composition.
     */
    fun thumbnailAspectRatio(): Float? {
        val width = thumbnailWidth ?: return null
        val height = thumbnailHeight ?: return null
        if (width <= 0 || height <= 0 || thumbnailPath == null) return null
        // Clamped so a freak panorama or a sliver cannot blow the grid apart.
        return (width.toFloat() / height).coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
    }

    private companion object {
        const val MIN_ASPECT_RATIO = 0.5f
        const val MAX_ASPECT_RATIO = 2.0f
    }
}

@Immutable
data class Category(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
) {
    companion object {
        /**
         * The seeded fallback category. A *fixed* id rather than a UUID because
         * SQLite's `ON DELETE SET DEFAULT` can only fall back to a literal
         * declared on the column. This row can never be deleted.
         *
         * Distinct from [isDefault], which marks the default category for *new
         * saves* and which the user is free to move (spec 5.4, 5.6).
         */
        const val UNSORTED_ID = "unsorted"
        const val UNSORTED_NAME = "Unsorted"
        const val NAME_MAX_LENGTH = 40
    }
}

/** A category plus its live bookmark count, for the filter row and list. */
@Immutable
data class CategoryWithCount(
    val category: Category,
    val count: Int,
)

@Immutable
data class BookmarkWithCategory(
    val bookmark: Bookmark,
    val category: Category,
)

object BookmarkLimits {
    const val TITLE_MAX = 200
    const val DESCRIPTION_MAX = 500
    const val SITE_NAME_MAX = 60
}
