package com.bookmark.sync.data

import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.MetadataState
import kotlinx.serialization.Serializable

@Serializable
data class BookmarkSyncDto(
    val id: String,
    val url: String,
    val originalUrl: String,
    val title: String,
    val description: String? = null,
    val siteName: String? = null,
    val thumbnailUrl: String? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val accentColor: Int? = null,
    val imageCandidates: List<String> = emptyList(),
    val categoryId: String,
    val manualFields: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CategorySyncDto(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class DeletedIdDto(
    val id: String,
    val deletedAt: Long,
)

@Serializable
data class CategoryDeltaDto(
    val upserts: List<CategorySyncDto> = emptyList(),
    val deletes: List<DeletedIdDto> = emptyList(),
)

@Serializable
data class BookmarkDeltaDto(
    val upserts: List<BookmarkSyncDto> = emptyList(),
    val deletes: List<DeletedIdDto> = emptyList(),
)

@Serializable
data class SyncPullResponse(
    val hasMore: Boolean,
    val nextSince: Long,
    val categories: CategoryDeltaDto,
    val bookmarks: BookmarkDeltaDto,
)

@Serializable
data class SyncPushRequest(
    val categories: List<CategorySyncDto> = emptyList(),
    val bookmarks: List<BookmarkSyncDto> = emptyList(),
    val deletedCategoryIds: List<DeletedIdDto> = emptyList(),
    val deletedBookmarkIds: List<DeletedIdDto> = emptyList(),
)

@Serializable
data class RejectedRowDto(
    val id: String,
    val reason: String? = null,
)

@Serializable
data class PushResultDto(
    val applied: List<String> = emptyList(),
    val rejected: List<RejectedRowDto> = emptyList(),
)

@Serializable
data class SyncPushResponse(
    val categories: PushResultDto,
    val bookmarks: PushResultDto,
)

/**
 * [thumbnailUrl] is this row's already-uploaded [BookmarkEntity.remoteThumbnailUrl]
 * -- the caller is responsible for uploading a not-yet-uploaded local thumbnail
 * first (see `SyncRepository.push`) and passing the resulting URL in.
 */
fun BookmarkEntity.toSyncDto(thumbnailUrl: String?) = BookmarkSyncDto(
    id = id,
    url = url,
    originalUrl = originalUrl,
    title = title,
    description = description,
    siteName = siteName,
    thumbnailUrl = thumbnailUrl,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    accentColor = accentColor,
    imageCandidates = imageCandidates?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty(),
    categoryId = categoryId,
    manualFields = manualFields,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * [thumbnailPath] follows the existing on-device naming convention (`{id}.webp`)
 * the instant a thumbnail URL is known -- the actual file is downloaded
 * separately by `SyncRepository.pull` before this entity is inserted, so the
 * path always points at a real file by the time it's written.
 *
 * Fetch-state bookkeeping (`failureCause`/`fetchAttempts`/`lastFetchAt`) is
 * device-local and absent from the wire format, so it gets sane defaults here:
 * a pulled row with an image is `SUCCESS`, one without is `PENDING` so this
 * device's own metadata worker can pick it up (see `SyncRepository.pull`).
 */
fun BookmarkSyncDto.toEntity(syncedUpdatedAt: Long?) = BookmarkEntity(
    id = id,
    url = url,
    originalUrl = originalUrl,
    title = title,
    description = description,
    siteName = siteName,
    thumbnailPath = if (thumbnailUrl == null) null else "$id.webp",
    faviconPath = null,
    accentColor = accentColor,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    imageCandidates = imageCandidates.joinToString("\n").ifBlank { null },
    categoryId = categoryId,
    metadataState = if (thumbnailUrl != null) MetadataState.SUCCESS else MetadataState.PENDING,
    failureCause = null,
    fetchAttempts = 0,
    lastFetchAt = null,
    manualFields = manualFields,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteThumbnailUrl = thumbnailUrl,
    syncedUpdatedAt = syncedUpdatedAt,
)

fun CategoryEntity.toSyncDto() = CategorySyncDto(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * [sortOrder] is device-local (never synced) -- the caller passes the existing
 * local value for a row it already has, or a fresh one (e.g. `nextSortOrder()`)
 * for a category arriving on this device for the first time.
 */
fun CategorySyncDto.toEntity(sortOrder: Int, syncedUpdatedAt: Long?) = CategoryEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncedUpdatedAt = syncedUpdatedAt,
)
