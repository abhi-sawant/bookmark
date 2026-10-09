package com.bookmark.metadata

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.core.data.AppDatabase
import com.bookmark.core.data.SeedCallback
import com.bookmark.core.model.Category
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The manual-field lock is enforced inside the UPDATE rather than by a
 * read-check-write in Kotlin, so that a fetch completing while the user is
 * typing cannot clobber the edit. These tests pin that behaviour -- it is the
 * mechanism behind design principle 3, "the user's edit always wins".
 */
@RunWith(AndroidJUnit4::class)
class ApplyMetadataTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedCallback())
            .build()
        dao = database.bookmarkDao()
    }

    @After
    fun tearDown() = database.close()

    private fun insert(manualFields: Int) = runBlocking {
        dao.insert(
            BookmarkEntity(
                id = ID,
                url = "https://example.com/a",
                originalUrl = "https://example.com/a",
                title = "User's own title",
                description = "User's own description",
                siteName = null,
                thumbnailPath = "user-picked.webp",
                faviconPath = null,
                accentColor = null,
                thumbnailWidth = 10,
                thumbnailHeight = 20,
                imageCandidates = null,
                categoryId = Category.UNSORTED_ID,
                metadataState = MetadataState.PENDING,
                failureCause = null,
                fetchAttempts = 0,
                lastFetchAt = null,
                manualFields = manualFields,
                isPinned = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    private fun applyFetched() = runBlocking {
        dao.applyMetadata(
            id = ID,
            title = "Fetched title",
            description = "Fetched description",
            siteName = "Example",
            thumbnailPath = "fetched.webp",
            thumbnailWidth = 800,
            thumbnailHeight = 600,
            accentColor = 0x00FF00,
            imageCandidates = "https://example.com/1.jpg",
            state = MetadataState.SUCCESS,
            failureCause = null,
            attempts = 1,
            now = 999L,
        )
    }

    @Test
    fun anUnlockedBookmarkTakesEveryFetchedField() = runBlocking {
        insert(ManualField.NONE)
        applyFetched()

        val row = dao.findById(ID)!!
        assertEquals("Fetched title", row.title)
        assertEquals("Fetched description", row.description)
        assertEquals("fetched.webp", row.thumbnailPath)
        assertEquals(800, row.thumbnailWidth)
        assertEquals(MetadataState.SUCCESS, row.metadataState)
        assertEquals(1, row.fetchAttempts)
        assertEquals(999L, row.lastFetchAt)
    }

    @Test
    fun aManualTitleSurvivesAFetch() = runBlocking {
        insert(ManualField.TITLE)
        applyFetched()

        val row = dao.findById(ID)!!
        assertEquals("User's own title", row.title)
        // Only the title is locked; everything else still updates.
        assertEquals("Fetched description", row.description)
        assertEquals("fetched.webp", row.thumbnailPath)
    }

    @Test
    fun aManualDescriptionSurvivesAFetch() = runBlocking {
        insert(ManualField.DESCRIPTION)
        applyFetched()

        val row = dao.findById(ID)!!
        assertEquals("User's own description", row.description)
        assertEquals("Fetched title", row.title)
    }

    @Test
    fun aManualThumbnailAndItsDimensionsSurviveAFetch() = runBlocking {
        insert(ManualField.THUMBNAIL)
        applyFetched()

        val row = dao.findById(ID)!!
        assertEquals("user-picked.webp", row.thumbnailPath)
        assertEquals(10, row.thumbnailWidth)
        assertEquals(20, row.thumbnailHeight)
        // The accent colour is derived from the image, so it is locked with it.
        assertNull(row.accentColor)
    }

    @Test
    fun everyFieldLockedLeavesOnlyTheStateChanged() = runBlocking {
        insert(ManualField.TITLE or ManualField.DESCRIPTION or ManualField.THUMBNAIL)
        applyFetched()

        val row = dao.findById(ID)!!
        assertEquals("User's own title", row.title)
        assertEquals("User's own description", row.description)
        assertEquals("user-picked.webp", row.thumbnailPath)
        assertEquals(MetadataState.SUCCESS, row.metadataState)
    }

    @Test
    fun aFetchDoesNotBumpUpdatedAt() = runBlocking {
        // A background fetch is not a user edit; bumping updatedAt would
        // misreport when the bookmark last changed.
        insert(ManualField.NONE)
        applyFetched()

        assertEquals(1L, dao.findById(ID)!!.updatedAt)
    }

    @Test
    fun recordingAFailureLeavesAnExistingPreviewIntact() = runBlocking {
        insert(ManualField.NONE)
        applyFetched()

        dao.applyFetchFailure(
            id = ID,
            state = MetadataState.FAILED,
            failureCause = FailureCause.TIMEOUT.name,
            attempts = 3,
            now = 1000L,
        )

        val row = dao.findById(ID)!!
        assertEquals("fetched.webp", row.thumbnailPath)
        assertEquals("Fetched title", row.title)
        assertEquals(MetadataState.FAILED, row.metadataState)
        assertEquals(FailureCause.TIMEOUT.name, row.failureCause)
        assertEquals(3, row.fetchAttempts)
    }

    @Test
    fun clearingThumbnailsLeavesTheBookmarksThemselvesAlone() = runBlocking {
        insert(ManualField.NONE)
        applyFetched()

        dao.clearAllThumbnails()

        val row = dao.findById(ID)!!
        assertNull(row.thumbnailPath)
        assertNull(row.thumbnailWidth)
        assertNull(row.accentColor)
        assertEquals("Fetched title", row.title)
        assertEquals(MetadataState.SUCCESS, row.metadataState)
    }

    @Test
    fun countByStatesFeedsTheRefreshAllRow() = runBlocking {
        insert(ManualField.NONE)
        dao.applyFetchFailure(ID, MetadataState.FAILED, FailureCause.TIMEOUT.name, 3, 1L)

        assertEquals(1, dao.countByStates(listOf(MetadataState.FAILED, MetadataState.FALLBACK)))
        assertEquals(0, dao.countByStates(listOf(MetadataState.SUCCESS)))
    }

    private companion object {
        const val ID = "b1"
    }
}
