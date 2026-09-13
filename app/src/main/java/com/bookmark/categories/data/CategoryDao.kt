package com.bookmark.categories.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE categories SET isDefault = 0")
    suspend fun clearDefaultFlag()

    @Query("UPDATE categories SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultFlag(id: String)

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
}
