package com.bookmark.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.categories.data.CategoryDao
import com.bookmark.categories.data.CategoryEntity
import com.bookmark.core.model.Category
import com.bookmark.core.model.MetadataState
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In-memory, but still goes through SeedCallback and enforces foreign keys.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedCallback())
            .build()
        bookmarkDao = database.bookmarkDao()
        categoryDao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedsExactlyOneUnsortedCategory() = runBlocking {
        val unsorted = categoryDao.findById(Category.UNSORTED_ID)
        assertNotNull(unsorted)
        assertEquals(Category.UNSORTED_NAME, unsorted!!.name)
        assertTrue(unsorted.isDefault)
        assertEquals(Category.UNSORTED_ID, categoryDao.findDefault()?.id)
    }

    @Test(expected = Exception::class)
    fun rejectsADuplicateUrl() = runBlocking {
        bookmarkDao.insert(bookmark(url = "https://example.com/a"))
        bookmarkDao.insert(bookmark(url = "https://example.com/a"))
    }

    @Test(expected = Exception::class)
    fun rejectsACategoryNameThatDiffersOnlyByCase() = runBlocking {
        categoryDao.insert(category(name = "Reading"))
        categoryDao.insert(category(name = "reading"))
    }

    @Test
    fun movingBookmarksLeavesTheCategoryEmptyBeforeDeletion() = runBlocking {
        val reading = category(name = "Reading")
        categoryDao.insert(reading)
        bookmarkDao.insert(bookmark(url = "https://example.com/a", categoryId = reading.id))
        bookmarkDao.insert(bookmark(url = "https://example.com/b", categoryId = reading.id))

        bookmarkDao.moveAll(reading.id, Category.UNSORTED_ID, now = 1L)
        categoryDao.delete(reading)

        assertEquals(0, bookmarkDao.countInCategory(reading.id))
        assertEquals(2, bookmarkDao.countInCategory(Category.UNSORTED_ID))
        assertNull(categoryDao.findById(reading.id))
    }

    @Test
    fun deletingACategoryWithItsBookmarksRemovesBoth() = runBlocking {
        val watch = category(name = "Watch later")
        categoryDao.insert(watch)
        bookmarkDao.insert(bookmark(url = "https://example.com/v", categoryId = watch.id))

        bookmarkDao.deleteAllInCategory(watch.id)
        categoryDao.delete(watch)

        assertEquals(0, bookmarkDao.countInCategory(watch.id))
        assertNull(categoryDao.findById(watch.id))
    }

    @Test
    fun theForeignKeyFallbackCatchesADeleteThatSkipsTheRepository() = runBlocking {
        val orphaned = category(name = "Orphan maker")
        categoryDao.insert(orphaned)
        bookmarkDao.insert(bookmark(url = "https://example.com/x", categoryId = orphaned.id))

        // No reassignment first -- exactly the case ON DELETE SET DEFAULT exists for.
        categoryDao.delete(orphaned)

        val survivor = bookmarkDao.findByUrl("https://example.com/x")
        assertNotNull(survivor)
        assertEquals(Category.UNSORTED_ID, survivor!!.categoryId)
    }

    @Test
    fun ftsFindsBookmarksByTitleAndDescription() = runBlocking {
        bookmarkDao.insert(
            bookmark(
                url = "https://ogp.me",
                title = "Everything I know about HTML metadata",
                description = "Open Graph, Twitter cards and JSON-LD",
            ),
        )
        bookmarkDao.insert(bookmark(url = "https://example.com/other", title = "Braised short ribs"))

        assertEquals(1, bookmarkDao.search("metadata*", null).size)
        assertEquals(1, bookmarkDao.search("graph*", null).size)
        assertEquals(0, bookmarkDao.search("nonexistent*", null).size)
    }

    @Test
    fun ftsStaysInSyncWhenABookmarkIsDeleted() = runBlocking {
        val entity = bookmark(url = "https://ogp.me", title = "HTML metadata")
        bookmarkDao.insert(entity)
        assertEquals(1, bookmarkDao.search("metadata*", null).size)

        bookmarkDao.delete(entity)
        assertEquals(0, bookmarkDao.search("metadata*", null).size)
    }

    @Test
    fun defaultFlagMovesToExactlyOneCategory() = runBlocking {
        val reading = category(name = "Reading")
        categoryDao.insert(reading)

        categoryDao.clearDefaultFlag(System.currentTimeMillis())
        categoryDao.setDefaultFlag(reading.id, System.currentTimeMillis())

        assertEquals(reading.id, categoryDao.findDefault()?.id)
        assertTrue(categoryDao.findById(Category.UNSORTED_ID)!!.isDefault.not())
    }

    @Test
    fun findDirtyReturnsOnlyNeverSyncedOrEditedSinceRows() = runBlocking {
        val fresh = bookmark(url = "https://example.com/fresh") // syncedUpdatedAt null: never synced
        bookmarkDao.insert(fresh)
        val synced = bookmark(url = "https://example.com/synced").copy(updatedAt = 100L)
        bookmarkDao.insert(synced)
        bookmarkDao.markSynced(synced.id, 100L)

        val dirty = bookmarkDao.findDirty().map { it.id }
        assertTrue(fresh.id in dirty)
        assertTrue(synced.id !in dirty)

        // Editing the already-synced row past its syncedUpdatedAt makes it dirty again.
        bookmarkDao.update(synced.copy(title = "Edited", updatedAt = 200L))
        assertTrue(synced.id in bookmarkDao.findDirty().map { it.id })
    }

    @Test
    fun upsertFromServerBypassesTheManualFieldLock() = runBlocking {
        // Bit 1 = TITLE locked, as if the user had edited it on this device.
        val locked = bookmark(url = "https://example.com/locked").copy(manualFields = 1, title = "Local title")
        bookmarkDao.insert(locked)

        // applyMetadata must respect the lock...
        bookmarkDao.applyMetadata(
            id = locked.id, title = "Fetched title", description = null, siteName = null,
            thumbnailPath = null, thumbnailWidth = null, thumbnailHeight = null, accentColor = null,
            imageCandidates = null, state = MetadataState.SUCCESS, failureCause = null, attempts = 1,
            now = 1L,
        )
        assertEquals("Local title", bookmarkDao.findById(locked.id)?.title)

        // ...but a pulled row from another device is a full-row replace regardless.
        bookmarkDao.upsertFromServer(locked.copy(title = "Title from device B", updatedAt = 300L))
        assertEquals("Title from device B", bookmarkDao.findById(locked.id)?.title)
    }

    @Test
    fun categoryFindDirtyAndMarkSyncedRoundTrip() = runBlocking {
        val reading = category(name = "Reading")
        categoryDao.insert(reading)
        assertTrue(reading.id in categoryDao.findDirty().map { it.id })

        categoryDao.markSynced(reading.id, reading.updatedAt)
        assertTrue(reading.id !in categoryDao.findDirty().map { it.id })
    }

    private fun category(name: String) = CategoryEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        colorHex = "#0F7A6B",
        iconKey = null,
        sortOrder = 1,
        isDefault = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun bookmark(
        url: String,
        categoryId: String = Category.UNSORTED_ID,
        title: String = "Title",
        description: String? = null,
    ) = BookmarkEntity(
        id = UUID.randomUUID().toString(),
        url = url,
        originalUrl = url,
        title = title,
        description = description,
        siteName = null,
        thumbnailPath = null,
        faviconPath = null,
        accentColor = null,
        thumbnailWidth = null,
        thumbnailHeight = null,
        imageCandidates = null,
        categoryId = categoryId,
        metadataState = MetadataState.PENDING,
        failureCause = null,
        fetchAttempts = 0,
        lastFetchAt = null,
        manualFields = 0,
        isPinned = false,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
