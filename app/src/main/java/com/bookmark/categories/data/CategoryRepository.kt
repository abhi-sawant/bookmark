package com.bookmark.categories.data

import androidx.room.withTransaction
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.bookmarks.data.toEntity
import com.bookmark.core.data.AppDatabase
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What happens to the bookmarks when a category is deleted (spec 5.4). */
sealed interface DeleteStrategy {
    data class MoveTo(val categoryId: String) : DeleteStrategy
    data object DeleteBookmarks : DeleteStrategy
}

/** Everything needed to put a deleted category back, for Snackbar undo. */
data class DeletedCategory(
    val category: Category,
    val movedBookmarkIds: List<String>,
    val movedFrom: String?,
    val deletedBookmarks: List<Bookmark>,
)

sealed interface CategoryError {
    data object NameTaken : CategoryError
    data object NameEmpty : CategoryError
    data object CannotDeleteFallback : CategoryError
    data object CannotDeleteDefault : CategoryError
}

class CategoryException(val error: CategoryError) : Exception(error.toString())

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val bookmarkDao: BookmarkDao,
    private val database: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map(CategoryEntity::toDomain) }

    fun observeAllWithCounts(): Flow<List<CategoryWithCount>> =
        dao.observeAllWithCounts().map { list -> list.map(CategoryWithCountEntity::toDomain) }

    suspend fun findById(id: String): Category? = withContext(io) { dao.findById(id)?.toDomain() }

    /** The category new saves land in, falling back to the seeded Unsorted row. */
    suspend fun defaultCategoryId(): String = withContext(io) {
        dao.findDefault()?.id ?: Category.UNSORTED_ID
    }

    suspend fun create(name: String, colorHex: String, iconKey: String? = null): Category =
        withContext(io) {
            val trimmed = name.trim().take(Category.NAME_MAX_LENGTH)
            if (trimmed.isEmpty()) throw CategoryException(CategoryError.NameEmpty)
            if (dao.findByName(trimmed) != null) throw CategoryException(CategoryError.NameTaken)

            val entity = CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                colorHex = colorHex,
                iconKey = iconKey,
                sortOrder = dao.nextSortOrder(),
                isDefault = false,
                createdAt = System.currentTimeMillis(),
            )
            dao.insert(entity)
            entity.toDomain()
        }

    suspend fun rename(category: Category, name: String, colorHex: String, iconKey: String?) =
        withContext(io) {
            val trimmed = name.trim().take(Category.NAME_MAX_LENGTH)
            if (trimmed.isEmpty()) throw CategoryException(CategoryError.NameEmpty)
            val clash = dao.findByName(trimmed)
            if (clash != null && clash.id != category.id) {
                throw CategoryException(CategoryError.NameTaken)
            }
            dao.update(
                category.copy(name = trimmed, colorHex = colorHex, iconKey = iconKey).toEntity(),
            )
        }

    /** Persists a drag-reorder as a single transaction. */
    suspend fun reorder(orderedIds: List<String>) = withContext(io) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
        }
    }

    suspend fun setDefault(id: String) = withContext(io) {
        database.withTransaction {
            dao.clearDefaultFlag()
            dao.setDefaultFlag(id)
        }
    }

    /**
     * Deletes a category, applying the strategy the user picked in the dialog.
     *
     * Reassignment is explicit and transactional rather than leaning on the
     * foreign key's ON DELETE SET DEFAULT -- that clause is the safety net for
     * writes that bypass this method, not the mechanism.
     */
    suspend fun delete(category: Category, strategy: DeleteStrategy): DeletedCategory =
        withContext(io) {
            if (category.id == Category.UNSORTED_ID) {
                throw CategoryException(CategoryError.CannotDeleteFallback)
            }
            if (category.isDefault) {
                throw CategoryException(CategoryError.CannotDeleteDefault)
            }

            database.withTransaction {
                when (strategy) {
                    is DeleteStrategy.MoveTo -> {
                        val movedIds = bookmarkDao.findAllInCategory(category.id).map { it.id }
                        bookmarkDao.moveAll(
                            fromCategoryId = category.id,
                            toCategoryId = strategy.categoryId,
                            now = System.currentTimeMillis(),
                        )
                        dao.delete(category.toEntity())
                        DeletedCategory(
                            category = category,
                            movedBookmarkIds = movedIds,
                            movedFrom = strategy.categoryId,
                            deletedBookmarks = emptyList(),
                        )
                    }

                    DeleteStrategy.DeleteBookmarks -> {
                        val doomed = bookmarkDao.findAllInCategory(category.id)
                            .map(BookmarkEntity::toDomain)
                        bookmarkDao.deleteAllInCategory(category.id)
                        dao.delete(category.toEntity())
                        DeletedCategory(
                            category = category,
                            movedBookmarkIds = emptyList(),
                            movedFrom = null,
                            deletedBookmarks = doomed,
                        )
                    }
                }
            }
        }

    /** Reverses [delete], for the Snackbar undo. */
    suspend fun restore(deleted: DeletedCategory) = withContext(io) {
        database.withTransaction {
            dao.insert(deleted.category.toEntity())
            val now = System.currentTimeMillis()
            deleted.movedBookmarkIds.forEach { id ->
                bookmarkDao.setCategory(id, deleted.category.id, now)
            }
            deleted.deletedBookmarks.forEach { bookmark ->
                bookmarkDao.insert(bookmark.toEntity())
            }
        }
    }

    suspend fun countIn(categoryId: String): Int = withContext(io) {
        bookmarkDao.countInCategory(categoryId)
    }

    suspend fun mostUsed(limit: Int): List<CategoryWithCount> = withContext(io) {
        dao.mostUsed(limit).map(CategoryWithCountEntity::toDomain)
    }

}
