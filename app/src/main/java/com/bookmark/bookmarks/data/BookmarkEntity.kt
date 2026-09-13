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
    @ColumnInfo(defaultValue = Category.UNSORTED_ID)
    val categoryId: String,
    val metadataState: MetadataState,
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
        categoryId = categoryId,
        metadataState = metadataState,
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
    categoryId = categoryId,
    metadataState = metadataState,
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
