package com.bookmark.bookmarks.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import com.bookmark.core.model.SortOrder
import com.bookmark.core.util.TitleFallback
import com.bookmark.core.util.UrlNormalizer
import com.bookmark.metadata.FailureCause
import com.bookmark.metadata.PageMetadata
import com.bookmark.metadata.image.StoredThumbnail
import com.bookmark.metadata.work.MetadataEnqueuer
import com.bookmark.sync.data.SyncTombstoneDao
import com.bookmark.sync.data.SyncTombstoneEntity
import com.bookmark.sync.data.TombstoneType
import com.bookmark.sync.work.SyncEnqueuer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Outcome of a save attempt. A duplicate surfaces the existing row (spec 14 Q1). */
sealed interface SaveResult {
    data class Saved(val bookmark: Bookmark) : SaveResult
    data class Duplicate(val existing: Bookmark) : SaveResult
}

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val metadataEnqueuer: MetadataEnqueuer,
    private val syncEnqueuer: SyncEnqueuer,
) {

    fun observe(categoryId: String?, sort: SortOrder): Flow<List<Bookmark>> {
        val source = when (sort) {
            SortOrder.NEWEST -> dao.observeByNewest(categoryId)
            SortOrder.OLDEST -> dao.observeByOldest(categoryId)
            SortOrder.TITLE_AZ -> dao.observeByTitle(categoryId)
            SortOrder.CATEGORY -> dao.observeByCategory(categoryId)
        }
        return source.map { list -> list.map(BookmarkEntity::toDomain) }
    }

    fun observeById(id: String): Flow<Bookmark?> =
        dao.observeById(id).map { it?.toDomain() }

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

    suspend fun findById(id: String): Bookmark? = withContext(io) {
        dao.findById(id)?.toDomain()
    }

    suspend fun findByNormalizedUrl(url: String): Bookmark? = withContext(io) {
        dao.findByUrl(UrlNormalizer.normalize(url))?.toDomain()
    }

    /**
     * Persists immediately, whatever the fetch state (design principle 1:
     * saving never blocks). Metadata catches up later, or never.
     */
    suspend fun save(
        rawUrl: String,
        categoryId: String,
        title: String? = null,
        description: String? = null,
        sharedSubject: String? = null,
        manualFields: Int = ManualField.NONE,
    ): SaveResult = withContext(io) {
        val normalized = UrlNormalizer.normalize(rawUrl)

        dao.findByUrl(normalized)?.let { return@withContext SaveResult.Duplicate(it.toDomain()) }

        val now = System.currentTimeMillis()
        val entity = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            url = normalized,
            originalUrl = rawUrl,
            title = TitleFallback.resolve(
                fetchedTitle = title,
                sharedSubject = sharedSubject,
                url = normalized,
            ).take(BookmarkLimits.TITLE_MAX),
            description = description?.trim()?.take(BookmarkLimits.DESCRIPTION_MAX)?.ifBlank { null },
            siteName = TitleFallback.fromDomain(normalized)?.take(BookmarkLimits.SITE_NAME_MAX),
            thumbnailPath = null,
            faviconPath = null,
            accentColor = null,
            thumbnailWidth = null,
            thumbnailHeight = null,
            imageCandidates = null,
            categoryId = categoryId,
            metadataState = MetadataState.PENDING,
            failureCause = null,
            fetchAttempts = 0,
            lastFetchAt = null,
            manualFields = manualFields,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
        )

        try {
            dao.insert(entity)
            // Every save path funnels through here, and this is where the row is
            // stamped PENDING, so it is also where the fetch that resolves it is
            // scheduled. The enqueue is a no-op when previews are off (spec 11),
            // and the row is already durable either way (design principle 1).
            metadataEnqueuer.enqueueAutomatic(entity.id)
            syncEnqueuer.scheduleDebounced()
            SaveResult.Saved(entity.toDomain())
        } catch (e: SQLiteConstraintException) {
            // Lost a race against another save of the same URL.
            val existing = dao.findByUrl(normalized)
            if (existing != null) SaveResult.Duplicate(existing.toDomain()) else throw e
        }
    }

    suspend fun findByStates(states: List<MetadataState>): List<Bookmark> = withContext(io) {
        dao.findByStates(states).map(BookmarkEntity::toDomain)
    }

    suspend fun countByStates(states: List<MetadataState>): Int = withContext(io) {
        dao.countByStates(states)
    }

    suspend fun markFetching(id: String) = withContext(io) {
        dao.setMetadataState(id, MetadataState.FETCHING)
    }

    /**
     * Writes a completed fetch. The manual-field locks are applied inside the
     * UPDATE (see [BookmarkDao.applyMetadata]) so a concurrent edit in the sheet
     * cannot be clobbered.
     */
    suspend fun applyMetadata(
        bookmarkId: String,
        metadata: PageMetadata,
        thumbnail: StoredThumbnail?,
        state: MetadataState,
        cause: FailureCause?,
        attempts: Int,
    ) = withContext(io) {
        dao.applyMetadata(
            id = bookmarkId,
            title = metadata.title,
            description = metadata.description,
            siteName = metadata.siteName,
            thumbnailPath = thumbnail?.relativePath,
            thumbnailWidth = thumbnail?.width,
            thumbnailHeight = thumbnail?.height,
            accentColor = thumbnail?.accentColor,
            imageCandidates = metadata.imageCandidates.joinToString("\n").ifBlank { null },
            state = state,
            failureCause = cause?.name,
            attempts = attempts,
            now = System.currentTimeMillis(),
        )
    }

    /** Records an attempt that yielded nothing, leaving any existing preview intact. */
    suspend fun recordFetchOutcome(
        bookmarkId: String,
        state: MetadataState,
        cause: FailureCause?,
        attempts: Int,
    ) = withContext(io) {
        dao.applyFetchFailure(
            id = bookmarkId,
            state = state,
            failureCause = cause?.name,
            attempts = attempts,
            now = System.currentTimeMillis(),
        )
    }

    /** "Retry fetch" / "Refresh preview" -- explicit, so it ignores the toggle. */
    fun requestManualFetch(bookmarkId: String) = metadataEnqueuer.enqueueManual(bookmarkId)

    /**
     * The user's own Thumbnail-picker choice (a chosen candidate, a local pick,
     * or "Remove" when [thumbnail] is null). Always sets the manual-field lock,
     * unconditionally -- this *is* the manual override, so there is nothing to
     * gate against, unlike [applyMetadata]'s CASE-guarded columns.
     */
    suspend fun applyManualThumbnail(bookmarkId: String, thumbnail: StoredThumbnail?) = withContext(io) {
        dao.setManualThumbnail(
            id = bookmarkId,
            thumbnailPath = thumbnail?.relativePath,
            thumbnailWidth = thumbnail?.width,
            thumbnailHeight = thumbnail?.height,
            accentColor = thumbnail?.accentColor,
            now = System.currentTimeMillis(),
        )
        syncEnqueuer.scheduleDebounced()
    }

    /**
     * [remoteThumbnailUrl] is preserved when [Bookmark.thumbnailPath] hasn't
     * changed -- otherwise every plain title/description edit would wipe a
     * perfectly valid uploaded thumbnail URL and force a pointless re-upload
     * on the next sync.
     */
    suspend fun update(bookmark: Bookmark) = withContext(io) {
        val current = dao.findById(bookmark.id)
        val remoteThumbnailUrl = current?.remoteThumbnailUrl?.takeIf { current.thumbnailPath == bookmark.thumbnailPath }
        dao.update(
            bookmark.copy(updatedAt = System.currentTimeMillis())
                .toEntity(remoteThumbnailUrl = remoteThumbnailUrl, syncedUpdatedAt = current?.syncedUpdatedAt),
        )
        syncEnqueuer.scheduleDebounced()
    }

    /**
     * Removes the row but *keeps* the thumbnail file, because the delete is
     * undoable (spec 14 Q2) and [restore] puts the row back with the same
     * `thumbnailPath`. Deleting the file here would make undo silently
     * downgrade the bookmark to a monogram tile. The file is reclaimed by
     * [discardDeleted] when the Snackbar resolves, and by
     * [sweepOrphanThumbnails] on next launch if the process dies first.
     */
    suspend fun delete(bookmark: Bookmark) = withContext(io) {
        dao.delete(bookmark.toEntity())
    }

    /** Re-inserts a deleted bookmark verbatim, for Snackbar undo (spec 14 Q2). */
    suspend fun restore(bookmark: Bookmark) = withContext(io) {
        dao.insert(bookmark.toEntity())
        syncEnqueuer.scheduleDebounced()
    }

    /**
     * The undo window has closed: the files can go now, and the delete is
     * irreversible -- exactly the moment a sync tombstone is recorded (nothing
     * changes if the user hit Undo first: no tombstone is ever created).
     */
    suspend fun discardDeleted(bookmark: Bookmark) = withContext(io) {
        bookmark.thumbnailPath?.let { deleteThumbnail(it) }
        bookmark.faviconPath?.let { deleteFavicon(it) }
        syncTombstoneDao.insert(
            SyncTombstoneEntity(bookmark.id, TombstoneType.BOOKMARK.name, System.currentTimeMillis()),
        )
        syncEnqueuer.scheduleDebounced()
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(io) {
        dao.setPinned(id, pinned, System.currentTimeMillis())
        syncEnqueuer.scheduleDebounced()
    }

    suspend fun setCategory(id: String, categoryId: String) = withContext(io) {
        dao.setCategory(id, categoryId, System.currentTimeMillis())
        syncEnqueuer.scheduleDebounced()
    }

    suspend fun search(query: String, categoryId: String?): List<Bookmark> = withContext(io) {
        if (query.isBlank()) return@withContext emptyList()
        // FTS4 prefix match on every term, so results appear while still typing.
        val match = query.trim().split(Regex("\\s+")).joinToString(" ") { term ->
            "${term.replace("\"", "")}*"
        }
        runCatching { dao.search(match, categoryId).map(BookmarkEntity::toDomain) }
            .getOrDefault(emptyList())
    }

    /**
     * Deletes thumbnail files with no owning row. Cheap: list the directory and
     * diff against the ids in the database (spec 7.4). Catches files left behind
     * by a crash mid-write.
     */
    suspend fun sweepOrphanThumbnails() = withContext(io) {
        val directory = thumbnailDir()
        if (!directory.isDirectory) return@withContext
        // Compares bare filenames, so thumbnailPath must stay a bare filename --
        // ThumbnailPipeline writes "{bookmarkId}.webp" flat for exactly this
        // reason. Any nested scheme would make every file look orphaned.
        val known = dao.allThumbnailPaths().toSet()
        directory.listFiles()?.forEach { file ->
            if (file.name !in known) file.delete()
        }
    }

    /**
     * Settings, "Clear thumbnails" (spec 5.6): re-fetchable, and the bookmarks
     * themselves are untouched. Returns the number of bytes reclaimed.
     */
    suspend fun clearThumbnails(): Long = withContext(io) {
        val directory = thumbnailDir()
        val freed = thumbnailBytes()
        dao.clearAllThumbnails()
        directory.listFiles()?.forEach { it.delete() }
        freed
    }

    suspend fun thumbnailBytes(): Long = withContext(io) {
        thumbnailDir().listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun thumbnailDir(): File = File(context.filesDir, THUMBNAIL_DIR)

    fun thumbnailFile(relativePath: String): File = File(thumbnailDir(), relativePath)

    private fun deleteThumbnail(relativePath: String) {
        runCatching { thumbnailFile(relativePath).delete() }
    }

    private fun deleteFavicon(relativePath: String) {
        runCatching { File(File(context.filesDir, FAVICON_DIR), relativePath).delete() }
    }

    private companion object {
        const val THUMBNAIL_DIR = "thumbnails"
        const val FAVICON_DIR = "favicons"
    }
}
