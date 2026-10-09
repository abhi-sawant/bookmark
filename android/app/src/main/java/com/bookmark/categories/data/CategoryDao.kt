package com.bookmark.categories.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT c.*, COUNT(b.id) AS bookmarkCount
        FROM categories c
        LEFT JOIN bookmarks b ON b.categoryId = c.id
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.createdAt ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<CategoryWithCountEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    /** Settings, "Export backup" -- the full table, for the backup zip's categories.json. */
    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    /**
     * Import, "Replace everything". Never blanket-deletes: the seeded
     * [com.bookmark.core.model.Category.UNSORTED_ID] row must always survive,
     * since `bookmarks.categoryId`'s `ON DELETE SET DEFAULT` falls back to it.
     */
    @Query("DELETE FROM categories WHERE id != :keepId")
    suspend fun deleteAllExcept(keepId: String)

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isDefault = 1 LIMIT 1")
    suspend fun findDefault(): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    /** Device-local drag-reorder position -- deliberately not synced, so never bumps updatedAt. */
    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    /** `isDefault` is synced, so both halves of this transactional pair bump `updatedAt`. */
    @Query("UPDATE categories SET isDefault = 0, updatedAt = :now WHERE isDefault = 1")
    suspend fun clearDefaultFlag(now: Long)

    @Query("UPDATE categories SET isDefault = 1, updatedAt = :now WHERE id = :id")
    suspend fun setDefaultFlag(id: String, now: Long)

    /** The four most-used categories, for Direct Share shortcuts (spec 6.3). */
    @Query(
        """
        SELECT c.*, COUNT(b.id) AS bookmarkCount
        FROM categories c
        LEFT JOIN bookmarks b ON b.categoryId = c.id
        GROUP BY c.id
        ORDER BY bookmarkCount DESC, c.sortOrder ASC
        LIMIT :limit
        """
    )
    suspend fun mostUsed(limit: Int): List<CategoryWithCountEntity>

    /** Rows with unpushed local changes -- never synced, or edited since the last sync. */
    @Query("SELECT * FROM categories WHERE syncedUpdatedAt IS NULL OR updatedAt > syncedUpdatedAt")
    suspend fun findDirty(): List<CategoryEntity>

    @Query("UPDATE categories SET syncedUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: String, updatedAt: Long)

    /**
     * Backing half of [upsertFromServer]: an update that touches every synced
     * column, returning the number of rows affected (0 if the id doesn't
     * exist locally yet).
     */
    @Update
    suspend fun updateExisting(category: CategoryEntity): Int

    /** The other half of [upsertFromServer], for a row that doesn't exist yet. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(category: CategoryEntity)

    /**
     * Full-row upsert for a row pulled from another device.
     *
     * Deliberately NOT `OnConflictStrategy.REPLACE`: on a primary-key
     * conflict, REPLACE resolves it as a real `DELETE` followed by an
     * `INSERT` -- and `bookmarks.categoryId`'s `ON DELETE SET DEFAULT`
     * foreign key fires on that transient delete, silently reassigning every
     * bookmark in this category to Unsorted before the row is reinserted.
     * Try-update-else-insert instead, so an update to an existing category
     * never deletes the row at all.
     */
    @Transaction
    suspend fun upsertFromServer(category: CategoryEntity) {
        if (updateExisting(category) == 0) insertIgnoringConflict(category)
    }
}
