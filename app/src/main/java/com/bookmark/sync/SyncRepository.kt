package com.bookmark.sync

import com.bookmark.account.data.AuthTokenStore
import com.bookmark.account.di.ApiHttpClient
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.categories.data.CategoryDao
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.model.MetadataState
import com.bookmark.metadata.work.MetadataEnqueuer
import com.bookmark.sync.data.DeletedIdDto
import com.bookmark.sync.data.SyncApi
import com.bookmark.sync.data.SyncPushRequest
import com.bookmark.sync.data.SyncStateStore
import com.bookmark.sync.data.SyncTombstoneDao
import com.bookmark.sync.data.TombstoneType
import com.bookmark.sync.data.toEntity
import com.bookmark.sync.data.toSyncDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface SyncResult {
    data object NotSignedIn : SyncResult
    data object Success : SyncResult
    data class Failed(val error: Throwable) : SyncResult
}

/**
 * Injects DAOs directly rather than the domain `BookmarkRepository`/
 * `CategoryRepository`, mirroring `BackupRepository`'s existing precedent for
 * full entity-level access. `BookmarkRepository` itself is still injected,
 * but only for its `thumbnailDir()`/`thumbnailFile()` helpers, so the
 * `filesDir/thumbnails/{id}.webp` path convention lives in one place.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val categoryDao: CategoryDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val syncApi: SyncApi,
    private val syncStateStore: SyncStateStore,
    private val authTokenStore: AuthTokenStore,
    private val metadataEnqueuer: MetadataEnqueuer,
    private val bookmarkRepository: BookmarkRepository,
    @ApiHttpClient private val downloadClient: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun sync(): SyncResult {
        if (authTokenStore.currentTokenOrNull() == null) return SyncResult.NotSignedIn
        return withContext(io) {
            runCatching {
                push()
                pull()
                syncStateStore.recordSuccess(System.currentTimeMillis())
            }.fold(
                onSuccess = { SyncResult.Success },
                onFailure = { SyncResult.Failed(it) },
            )
        }
    }

    private suspend fun push() {
        val dirtyBookmarks = bookmarkDao.findDirty()
        val dirtyCategories = categoryDao.findDirty()
        val tombstones = syncTombstoneDao.getAll()

        val bookmarkDtos = dirtyBookmarks.map { bookmark ->
            val uploadedUrl = ensureThumbnailUploaded(bookmark.id, bookmark.thumbnailPath, bookmark.remoteThumbnailUrl)
            bookmark.toSyncDto(uploadedUrl)
        }
        val categoryDtos = dirtyCategories.map { it.toSyncDto() }
        val deletedBookmarkIds = tombstones
            .filter { it.entityType == TombstoneType.BOOKMARK.name }
            .map { DeletedIdDto(it.id, it.deletedAt) }
        val deletedCategoryIds = tombstones
            .filter { it.entityType == TombstoneType.CATEGORY.name }
            .map { DeletedIdDto(it.id, it.deletedAt) }

        if (bookmarkDtos.isEmpty() && categoryDtos.isEmpty() && deletedBookmarkIds.isEmpty() && deletedCategoryIds.isEmpty()) {
            return
        }

        val response = syncApi.push(
            SyncPushRequest(
                categories = categoryDtos,
                bookmarks = bookmarkDtos,
                deletedCategoryIds = deletedCategoryIds,
                deletedBookmarkIds = deletedBookmarkIds,
            ),
        )

        val rejectedBookmarkIds = response.bookmarks.rejected.map { it.id }.toSet()
        val rejectedCategoryIds = response.categories.rejected.map { it.id }.toSet()

        dirtyBookmarks.forEach { bookmark ->
            if (bookmark.id !in rejectedBookmarkIds) bookmarkDao.markSynced(bookmark.id, bookmark.updatedAt)
        }
        dirtyCategories.forEach { category ->
            if (category.id !in rejectedCategoryIds) categoryDao.markSynced(category.id, category.updatedAt)
        }
        tombstones.forEach { tombstone ->
            val rejected = when (tombstone.entityType) {
                TombstoneType.BOOKMARK.name -> tombstone.id in rejectedBookmarkIds
                TombstoneType.CATEGORY.name -> tombstone.id in rejectedCategoryIds
                else -> false
            }
            if (!rejected) syncTombstoneDao.delete(tombstone.id, tombstone.entityType)
        }
    }

    /**
     * Uploads a not-yet-uploaded local thumbnail before its bookmark is pushed.
     * A failed upload is swallowed -- this push cycle simply sends
     * `thumbnailUrl = null` for the row and tries again next cycle; it must
     * never abort the rest of the push.
     */
    private suspend fun ensureThumbnailUploaded(
        bookmarkId: String,
        thumbnailPath: String?,
        remoteThumbnailUrl: String?,
    ): String? {
        if (remoteThumbnailUrl != null || thumbnailPath == null) return remoteThumbnailUrl
        val file = bookmarkRepository.thumbnailFile(thumbnailPath)
        if (!file.exists()) return null
        return runCatching { syncApi.uploadThumbnail(bookmarkId, file) }
            .onSuccess { url -> bookmarkDao.setRemoteThumbnailUrl(bookmarkId, url) }
            .getOrNull()
    }

    private suspend fun pull() {
        var cursor = syncStateStore.cursor()
        while (true) {
            val response = syncApi.pull(cursor)

            response.categories.upserts.forEach { dto ->
                if (dto.isDefault) categoryDao.clearDefaultFlag(System.currentTimeMillis())
                val existing = categoryDao.findById(dto.id)
                val sortOrder = existing?.sortOrder ?: categoryDao.nextSortOrder()
                categoryDao.upsertFromServer(dto.toEntity(sortOrder = sortOrder, syncedUpdatedAt = dto.updatedAt))
            }
            response.bookmarks.upserts.forEach { dto ->
                val entity = dto.toEntity(syncedUpdatedAt = dto.updatedAt)
                if (dto.thumbnailUrl != null && downloadThumbnail(dto.thumbnailUrl, dto.id)) {
                    bookmarkDao.upsertFromServer(entity)
                } else {
                    bookmarkDao.upsertFromServer(
                        entity.copy(thumbnailPath = null, metadataState = MetadataState.PENDING),
                    )
                    metadataEnqueuer.enqueueAutomatic(dto.id)
                }
            }
            response.categories.deletes.forEach { deleted ->
                categoryDao.findById(deleted.id)?.let { categoryDao.delete(it) }
            }
            response.bookmarks.deletes.forEach { deleted ->
                bookmarkDao.findById(deleted.id)?.let { entity ->
                    entity.thumbnailPath?.let { path ->
                        runCatching { bookmarkRepository.thumbnailFile(path).delete() }
                    }
                    bookmarkDao.delete(entity)
                }
            }

            cursor = response.nextSince
            syncStateStore.setCursor(cursor)
            if (!response.hasMore) break
        }
    }

    /** Plain GET, not through the sync API's JSON path -- reuses the same client. */
    private fun downloadThumbnail(url: String, bookmarkId: String): Boolean {
        return try {
            val request = Request.Builder().url(url).get().build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val target = bookmarkRepository.thumbnailFile("$bookmarkId.webp")
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> response.body.byteStream().copyTo(output) }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
