package com.bookmark.bookmarks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bookmark.core.model.MetadataState
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    /*
     * Pinned rows always float to the top; the chosen sort orders what is left.
     * A null categoryId means "All". One query per sort keeps SQLite able to use
     * the indices, which a CASE-based dynamic ORDER BY would defeat.
     */

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY isPinned DESC, createdAt DESC
        """
    )
    fun observeByNewest(categoryId: String?): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY isPinned DESC, createdAt ASC
        """
    )
    fun observeByOldest(categoryId: String?): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY isPinned DESC, title COLLATE NOCASE ASC
        """
    )
    fun observeByTitle(categoryId: String?): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT b.* FROM bookmarks b
        JOIN categories c ON c.id = b.categoryId
        WHERE (:categoryId IS NULL OR b.categoryId = :categoryId)
        ORDER BY b.isPinned DESC, c.sortOrder ASC, b.createdAt DESC
        """
    )
    fun observeByCategory(categoryId: String?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    fun observeById(id: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun findById(id: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: String): Int

    @Query("SELECT * FROM bookmarks WHERE categoryId = :categoryId")
    suspend fun findAllInCategory(categoryId: String): List<BookmarkEntity>

    /** ABORT rather than REPLACE: a duplicate URL surfaces the existing bookmark (spec 14 Q1). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: BookmarkEntity)

    /** Room runs a list-parameter @Insert as one transaction -- for M6's scroll-benchmark seed. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE categoryId = :categoryId")
    suspend fun deleteAllInCategory(categoryId: String)

    @Query("UPDATE bookmarks SET categoryId = :toCategoryId, updatedAt = :now WHERE categoryId = :fromCategoryId")
    suspend fun moveAll(fromCategoryId: String, toCategoryId: String, now: Long)

    @Query("UPDATE bookmarks SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE bookmarks SET categoryId = :categoryId, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: String, now: Long)

    @Query("SELECT thumbnailPath FROM bookmarks WHERE thumbnailPath IS NOT NULL")
    suspend fun allThumbnailPaths(): List<String>

    @Query("SELECT id FROM bookmarks")
    suspend fun allIds(): List<String>

    /** Settings, "Export backup" -- the full table, for the backup zip's bookmarks.json. */
    @Query("SELECT * FROM bookmarks")
    suspend fun getAll(): List<BookmarkEntity>

    /** Import's merge-preview diff: which URLs in the file are already saved. */
    @Query("SELECT url FROM bookmarks")
    suspend fun allUrls(): List<String>

    /** Import, "Replace everything" -- wiped before the file's rows are restored. */
    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    /** Feeds the M3 retry queue and Settings' "Refresh all metadata". */
    @Query("SELECT * FROM bookmarks WHERE metadataState IN (:states)")
    suspend fun findByStates(states: List<MetadataState>): List<BookmarkEntity>

    /** The "N bookmarks eligible" line in Settings, without loading the rows. */
    @Query("SELECT COUNT(*) FROM bookmarks WHERE metadataState IN (:states)")
    suspend fun countByStates(states: List<MetadataState>): Int

    @Query("UPDATE bookmarks SET metadataState = :state WHERE id = :id")
    suspend fun setMetadataState(id: String, state: MetadataState)

    /**
     * Writes the outcome of a fetch, honouring the manual-field locks *in SQL*.
     *
     * The obvious alternative -- read the row, check [com.bookmark.core.model.ManualField],
     * write it back -- has a race: the user can edit the title in the sheet
     * between the read and the write, and the background fetch would silently
     * clobber it. Doing the check inside the UPDATE makes the lock atomic with
     * the write, so design principle 3 ("the user's edit always wins") holds
     * even under a concurrent edit.
     *
     * The bit values are [com.bookmark.core.model.ManualField]: TITLE=1,
     * DESCRIPTION=2, THUMBNAIL=4.
     *
     * `updatedAt` is deliberately left alone: a background fetch is not a user
     * edit, and bumping it would misreport when the bookmark last changed.
     */
    @Query(
        """
        UPDATE bookmarks SET
            title = CASE
                WHEN (manualFields & 1) = 0 AND :title IS NOT NULL THEN :title
                ELSE title END,
            description = CASE
                WHEN (manualFields & 2) = 0 AND :description IS NOT NULL THEN :description
                ELSE description END,
            thumbnailPath = CASE
                WHEN (manualFields & 4) = 0 THEN :thumbnailPath
                ELSE thumbnailPath END,
            thumbnailWidth = CASE
                WHEN (manualFields & 4) = 0 THEN :thumbnailWidth
                ELSE thumbnailWidth END,
            thumbnailHeight = CASE
                WHEN (manualFields & 4) = 0 THEN :thumbnailHeight
                ELSE thumbnailHeight END,
            accentColor = CASE
                WHEN (manualFields & 4) = 0 THEN COALESCE(:accentColor, accentColor)
                ELSE accentColor END,
            siteName = COALESCE(:siteName, siteName),
            imageCandidates = :imageCandidates,
            metadataState = :state,
            failureCause = :failureCause,
            fetchAttempts = :attempts,
            lastFetchAt = :now
        WHERE id = :id
        """
    )
    @Suppress("LongParameterList")
    suspend fun applyMetadata(
        id: String,
        title: String?,
        description: String?,
        siteName: String?,
        thumbnailPath: String?,
        thumbnailWidth: Int?,
        thumbnailHeight: Int?,
        accentColor: Int?,
        imageCandidates: String?,
        state: MetadataState,
        failureCause: String?,
        attempts: Int,
        now: Long,
    )

    /**
     * Records an attempt that produced no usable metadata. Separate from
     * [applyMetadata] so a failure can never null out a thumbnail a previous
     * successful fetch stored.
     */
    @Query(
        """
        UPDATE bookmarks SET
            metadataState = :state,
            failureCause = :failureCause,
            fetchAttempts = :attempts,
            lastFetchAt = :now
        WHERE id = :id
        """
    )
    suspend fun applyFetchFailure(
        id: String,
        state: MetadataState,
        failureCause: String?,
        attempts: Int,
        now: Long,
    )

    /**
     * The user's own Thumbnail-picker choice, unconditionally: a chosen
     * candidate, a local pick, or "Remove" (null [thumbnailPath]). Setting bit
     * 4 of [com.bookmark.core.model.ManualField] here is what makes
     * [applyMetadata]'s CASE guard leave this alone on every future fetch.
     */
    @Query(
        """
        UPDATE bookmarks SET
            thumbnailPath = :thumbnailPath,
            thumbnailWidth = :thumbnailWidth,
            thumbnailHeight = :thumbnailHeight,
            accentColor = :accentColor,
            manualFields = manualFields | 4,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setManualThumbnail(
        id: String,
        thumbnailPath: String?,
        thumbnailWidth: Int?,
        thumbnailHeight: Int?,
        accentColor: Int?,
        now: Long,
    )

    /** Settings "Clear thumbnails": the files go, the bookmarks stay (spec 5.6). */
    @Query(
        """
        UPDATE bookmarks SET
            thumbnailPath = NULL, thumbnailWidth = NULL,
            thumbnailHeight = NULL, accentColor = NULL
        WHERE thumbnailPath IS NOT NULL
        """
    )
    suspend fun clearAllThumbnails()

    /**
     * FTS-backed search (spec 5.5). The table is created in schema v1; the
     * search screen that uses this arrives in M5.
     */
    @Query(
        """
        SELECT b.* FROM bookmarks b
        JOIN bookmarks_fts f ON b.rowid = f.rowid
        WHERE bookmarks_fts MATCH :query
          AND (:categoryId IS NULL OR b.categoryId = :categoryId)
        ORDER BY b.isPinned DESC, b.createdAt DESC
        """
    )
    suspend fun search(query: String, categoryId: String?): List<BookmarkEntity>
}
