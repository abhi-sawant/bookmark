package com.bookmark.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.bookmarks.data.BookmarkFtsEntity
import com.bookmark.categories.data.CategoryDao
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.Category

@Database(
    entities = [BookmarkEntity::class, BookmarkFtsEntity::class, CategoryEntity::class],
    version = 2,
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
 * v1 to v2: everything the metadata engine needs to record (M3).
 *
 * `failureCause` lets the detail sheet show the right spec 8.6 message rather
 * than assuming "couldn't reach this site"; the thumbnail dimensions let the
 * staggered grid size a card from the database instead of decoding the file
 * during composition; `imageCandidates` feeds the thumbnail picker. The
 * `metadataState` index is here too -- the retry queue and "Refresh all" both
 * scan by it, and adding it now avoids a v3 that does nothing else.
 *
 * All additive and all nullable, so no data migration is required and the FTS
 * table is untouched.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN failureCause TEXT")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN thumbnailWidth INTEGER")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN thumbnailHeight INTEGER")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN imageCandidates TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_metadataState ON bookmarks(metadataState)")
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
