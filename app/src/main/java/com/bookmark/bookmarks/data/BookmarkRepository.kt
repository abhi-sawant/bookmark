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
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
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
            categoryId = categoryId,
            metadataState = MetadataState.PENDING,
            fetchAttempts = 0,
            lastFetchAt = null,
            manualFields = manualFields,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
        )

        try {
            dao.insert(entity)
            SaveResult.Saved(entity.toDomain())
        } catch (e: SQLiteConstraintException) {
            // Lost a race against another save of the same URL.
            val existing = dao.findByUrl(normalized)
            if (existing != null) SaveResult.Duplicate(existing.toDomain()) else throw e
        }
    }

    suspend fun update(bookmark: Bookmark) = withContext(io) {
        dao.update(bookmark.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(bookmark: Bookmark) = withContext(io) {
        dao.delete(bookmark.toEntity())
        bookmark.thumbnailPath?.let { deleteThumbnail(it) }
        bookmark.faviconPath?.let { deleteFavicon(it) }
    }

    /** Re-inserts a deleted bookmark verbatim, for Snackbar undo (spec 14 Q2). */
    suspend fun restore(bookmark: Bookmark) = withContext(io) {
        dao.insert(bookmark.toEntity())
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(io) {
        dao.setPinned(id, pinned, System.currentTimeMillis())
    }

    suspend fun setCategory(id: String, categoryId: String) = withContext(io) {
        dao.setCategory(id, categoryId, System.currentTimeMillis())
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
        val known = dao.allThumbnailPaths().toSet()
        directory.listFiles()?.forEach { file ->
            if (file.name !in known) file.delete()
        }
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
