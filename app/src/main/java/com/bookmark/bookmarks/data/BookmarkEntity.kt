package com.bookmark.bookmarks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.FailureCauseName
import com.bookmark.core.model.MetadataState

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // Falls back to the seeded Unsorted row. Only reachable if a category
            // is deleted without going through CategoryRepository, which always
            // reassigns explicitly -- this is the safety net, not the mechanism.
            onDelete = ForeignKey.SET_DEFAULT,
        ),
    ],
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["createdAt"]),
        // The retry queue and Settings' "Refresh all" both scan by state.
        Index(value = ["metadataState"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    /** Normalised (spec 7.1). This is what the unique index dedupes on. */
    val url: String,
    /** Exactly as shared, before normalisation. */
    val originalUrl: String,
    /** Never null -- falls back per spec 8.2. */
    val title: String,
    val description: String?,
    val siteName: String?,
    /** Relative path inside `filesDir/thumbnails/`. */
    val thumbnailPath: String?,
    /** Relative path inside `filesDir/favicons/`. */
    val faviconPath: String?,
    val accentColor: Int?,
    /**
     * Pixel size of the stored thumbnail. Lets the staggered grid lay a card out
     * at the image's real aspect ratio without decoding the file during
     * composition -- see BookmarkGridCard.
     */
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    /**
     * Newline-separated image URLs the parser found, up to five, feeding the
     * "choose another image" picker (spec 5.2, 14 Q5). Newline-separated rather
     * than JSON because the values are URLs, which cannot contain one.
     */
    val imageCandidates: String?,
    @ColumnInfo(defaultValue = Category.UNSORTED_ID)
    val categoryId: String,
    val metadataState: MetadataState,
    /**
     * Name of the [com.bookmark.metadata.FailureCause] behind the current state,
     * so the detail sheet can show the right spec 8.6 message instead of
     * guessing. Stored as a name rather than an enum so the metadata package
     * stays out of the persistence layer.
     */
    val failureCause: FailureCauseName?,
    @ColumnInfo(defaultValue = "0") val fetchAttempts: Int,
    val lastFetchAt: Long?,
    /** Bitmask, see [com.bookmark.core.model.ManualField]. */
    @ColumnInfo(defaultValue = "0") val manualFields: Int,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain() = Bookmark(
        id = id,
        url = url,
        originalUrl = originalUrl,
        title = title,
        description = description,
        siteName = siteName,
        thumbnailPath = thumbnailPath,
        faviconPath = faviconPath,
        accentColor = accentColor,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        imageCandidates = imageCandidates?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty(),
        categoryId = categoryId,
        metadataState = metadataState,
        failureCause = failureCause,
        fetchAttempts = fetchAttempts,
        lastFetchAt = lastFetchAt,
        manualFields = manualFields,
        isPinned = isPinned,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Bookmark.toEntity() = BookmarkEntity(
    id = id,
    url = url,
    originalUrl = originalUrl,
    title = title,
    description = description,
    siteName = siteName,
    thumbnailPath = thumbnailPath,
    faviconPath = faviconPath,
    accentColor = accentColor,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    imageCandidates = imageCandidates.joinToString("\n").ifBlank { null },
    categoryId = categoryId,
    metadataState = metadataState,
    failureCause = failureCause,
    fetchAttempts = fetchAttempts,
    lastFetchAt = lastFetchAt,
    manualFields = manualFields,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * Content-backed FTS4 mirror of [BookmarkEntity] (spec 4.3). Created in schema
 * version 1 even though search itself lands in M5, so no migration is needed
 * later. Room generates the sync triggers.
 */
@Fts4(contentEntity = BookmarkEntity::class)
@Entity(tableName = "bookmarks_fts")
data class BookmarkFtsEntity(
    val title: String,
    val description: String?,
    val siteName: String?,
    val url: String,
)
