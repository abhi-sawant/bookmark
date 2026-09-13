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

    /** Feeds the M3 retry queue and Settings' "Refresh all metadata". */
    @Query("SELECT * FROM bookmarks WHERE metadataState IN (:states)")
    suspend fun findByStates(states: List<MetadataState>): List<BookmarkEntity>

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
