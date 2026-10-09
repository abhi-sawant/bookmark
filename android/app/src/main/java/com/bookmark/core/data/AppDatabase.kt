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
import com.bookmark.sync.data.SyncTombstoneDao
import com.bookmark.sync.data.SyncTombstoneEntity

@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkFtsEntity::class,
        CategoryEntity::class,
        SyncTombstoneEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao

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
 * v2 to v3: multi-device sync bookkeeping (backend + sync feature).
 *
 * All additive and nullable except `categories.updatedAt`, which didn't exist
 * before this version -- backfilled from `createdAt` for every existing row,
 * following the same additive/no-data-migration philosophy as [MIGRATION_1_2].
 * `sync_tombstones` records a delete only once it becomes irreversible; see
 * `com.bookmark.sync.data.SyncTombstoneEntity`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN remoteThumbnailUrl TEXT")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN syncedUpdatedAt INTEGER")

        db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE categories SET updatedAt = createdAt")
        db.execSQL("ALTER TABLE categories ADD COLUMN syncedUpdatedAt INTEGER")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_tombstones (
                id TEXT NOT NULL,
                entityType TEXT NOT NULL,
                deletedAt INTEGER NOT NULL,
                PRIMARY KEY (id, entityType)
            )
            """.trimIndent(),
        )
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
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO categories (id, name, colorHex, iconKey, sortOrder, isDefault, createdAt, updatedAt)
            VALUES (?, ?, ?, NULL, 0, 1, ?, ?)
            """.trimIndent(),
            arrayOf(
                Category.UNSORTED_ID,
                Category.UNSORTED_NAME,
                NEUTRAL_SWATCH,
                now,
                now,
            ),
        )
    }

    private companion object {
        /** Matches CategorySwatchHex[5], the neutral grey the design gives Unsorted. */
        const val NEUTRAL_SWATCH = "#7D918D"
    }
}
