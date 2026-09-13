package com.bookmark.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.bookmarks.data.BookmarkFtsEntity
import com.bookmark.categories.data.CategoryDao
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.Category

@Database(
    entities = [BookmarkEntity::class, BookmarkFtsEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val NAME = "bookmarks.db"
    }
}

/**
 * Seeds the fallback "Unsorted" category on first create (spec 4.2).
 *
 * Written as raw SQL rather than through the DAO because the callback fires
 * while the database is still being opened, before Room will hand out DAOs.
 */
class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            """
            INSERT INTO categories (id, name, colorHex, iconKey, sortOrder, isDefault, createdAt)
            VALUES (?, ?, ?, NULL, 0, 1, ?)
            """.trimIndent(),
            arrayOf(
                Category.UNSORTED_ID,
                Category.UNSORTED_NAME,
                NEUTRAL_SWATCH,
                System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        /** Matches CategorySwatchHex[5], the neutral grey the design gives Unsorted. */
        const val NEUTRAL_SWATCH = "#7D918D"
    }
}
