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
    val categoryId: String,
    val metadataState: MetadataState,
    val fetchAttempts: Int,
    val lastFetchAt: Long?,
    val manualFields: Int,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

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
