package com.bookmark.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room validates the resulting schema against the committed `2.json`, so this
 * catches a migration that drifts from the entity. It also checks that existing
 * rows survive -- v1 to v2 is purely additive and must not disturb data or the
 * content-backed FTS table.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsExistingRowsAndAddsTheMetadataColumns() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO categories (id, name, colorHex, iconKey, sortOrder, isDefault, createdAt)
                VALUES ('unsorted', 'Unsorted', '#7D918D', NULL, 0, 1, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO bookmarks
                    (id, url, originalUrl, title, description, siteName, thumbnailPath,
                     faviconPath, accentColor, categoryId, metadataState, fetchAttempts,
                     lastFetchAt, manualFields, isPinned, createdAt, updatedAt)
                VALUES ('b1', 'https://example.com/a', 'https://example.com/a', 'Kept',
                        'desc', 'Example', NULL, NULL, NULL, 'unsorted', 'PENDING', 0,
                        NULL, 1, 0, 100, 100)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query(
            """
            SELECT title, manualFields, failureCause, thumbnailWidth,
                   thumbnailHeight, imageCandidates
            FROM bookmarks WHERE id = 'b1'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue("the pre-migration row is gone", cursor.moveToFirst())
            assertEquals("Kept", cursor.getString(0))
            // The manual-title lock must survive: it is what stops the first
            // post-upgrade fetch overwriting an edit the user already made.
            assertEquals(1, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_bookmarks_metadataState'")
            .use { cursor ->
                assertTrue("the metadataState index was not created", cursor.moveToFirst())
            }

        // The content-backed FTS mirror must still resolve the migrated row.
        db.query("SELECT b.id FROM bookmarks b JOIN bookmarks_fts f ON b.rowid = f.rowid WHERE bookmarks_fts MATCH 'Kept'")
            .use { cursor ->
                assertTrue("FTS lost the row across the migration", cursor.moveToFirst())
                assertEquals("b1", cursor.getString(0))
            }
        db.close()
    }

    @Test
    fun migrate1To2LeavesAnEmptyDatabaseValid() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM bookmarks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
