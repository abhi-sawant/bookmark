package com.bookmark.backup

import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.MetadataState
import kotlinx.serialization.Serializable

/**
 * The backup file's own schema version, independent of the Room schema
 * (`AppDatabase.version`). Entity shapes are internal and will drift; a
 * backup is a durable external contract with its own stability guarantee.
 */
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifestDto(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Long,
    val bookmarkCount: Int,
    val categoryCount: Int,
)

@Serializable
data class BookmarkBackupDto(
    val id: String,
    val url: String,
    val originalUrl: String,
    val title: String,
    val description: String? = null,
    val siteName: String? = null,
    /** Filename inside the zip's `thumbnails/` folder, if any. */
    val thumbnailPath: String? = null,
    val accentColor: Int? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val imageCandidates: List<String> = emptyList(),
    val categoryId: String,
    val metadataState: String,
    val failureCause: String? = null,
    val fetchAttempts: Int = 0,
    val lastFetchAt: Long? = null,
    val manualFields: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CategoryBackupDto(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String? = null,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
)

fun BookmarkEntity.toBackupDto() = BookmarkBackupDto(
    id = id,
    url = url,
    originalUrl = originalUrl,
    title = title,
    description = description,
    siteName = siteName,
    thumbnailPath = thumbnailPath,
    accentColor = accentColor,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    imageCandidates = imageCandidates?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty(),
    categoryId = categoryId,
    metadataState = metadataState.name,
    failureCause = failureCause,
    fetchAttempts = fetchAttempts,
    lastFetchAt = lastFetchAt,
    manualFields = manualFields,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BookmarkBackupDto.toEntity() = BookmarkEntity(
    id = id,
    url = url,
    originalUrl = originalUrl,
    title = title,
    description = description,
    siteName = siteName,
    thumbnailPath = thumbnailPath,
    faviconPath = null,
    accentColor = accentColor,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    imageCandidates = imageCandidates.joinToString("\n").ifBlank { null },
    categoryId = categoryId,
    // An unrecognised state name (a future format's new state) falls back to
    // PENDING rather than throwing -- the row is still safe to restore, and
    // "Refresh all metadata" already knows how to pick PENDING back up.
    metadataState = runCatching { MetadataState.valueOf(metadataState) }.getOrDefault(MetadataState.PENDING),
    failureCause = failureCause,
    fetchAttempts = fetchAttempts,
    lastFetchAt = lastFetchAt,
    manualFields = manualFields,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CategoryEntity.toBackupDto() = CategoryBackupDto(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun CategoryBackupDto.toEntity() = CategoryEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
    // The backup format predates sync and carries no updatedAt of its own;
    // backfilling from createdAt matches MIGRATION_2_3's own backfill and
    // marks the restored row as needing a push, which is correct.
    updatedAt = createdAt,
)
