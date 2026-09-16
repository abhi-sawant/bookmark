package com.bookmark.benchmark

import com.bookmark.bookmarks.data.BookmarkDao
import com.bookmark.bookmarks.data.BookmarkEntity
import com.bookmark.core.model.Category
import com.bookmark.core.model.MetadataState
import java.util.UUID

/**
 * M6 scroll-benchmark support: seeds [count] synthetic bookmarks directly via
 * the DAO, bypassing [com.bookmark.bookmarks.data.BookmarkRepository.save] on
 * purpose -- that path enqueues a real metadata fetch per bookmark, which
 * would make a 500-item seed slow and network-dependent for what should be a
 * pure scroll/render measurement. Rows are inserted already in a terminal
 * [MetadataState.FALLBACK] state (monogram tile, no pending work) so the grid
 * renders its real steady-state layout immediately.
 *
 * A per-build-type real/no-op split (like [com.bookmark.debug.StrictModeInit])
 * was tried first, but AGP 9's baseline-profile-plugin-created `benchmarkRelease`
 * build type unconditionally inherits all of `src/release/java` alongside its
 * own source folder -- there is no way to give it something different from
 * plain `release` without a duplicate-symbol clash, short of reintroducing a
 * `BuildConfig` branch. Living in `src/main` unconditionally is simpler and
 * has no real downside: this only ever fires when the caller
 * ([MainActivity][com.bookmark.MainActivity]) receives a positive
 * `EXTRA_BENCHMARK_SEED_COUNT`, which nothing sends outside
 * `ScrollBenchmark`'s own `startActivityAndWait()` -- MainActivity is already
 * an exported launcher activity, so this adds no new attack surface, and the
 * effect is bounded to inserting local rows into the app's own database.
 */
suspend fun seedBenchmarkBookmarksIfRequested(dao: BookmarkDao, count: Int) {
    if (count <= 0) return
    val now = System.currentTimeMillis()
    val entities = (1..count).map { i ->
        BookmarkEntity(
            id = UUID.randomUUID().toString(),
            url = "https://example.com/benchmark-seed/$i",
            originalUrl = "https://example.com/benchmark-seed/$i",
            title = "Benchmark seed bookmark $i",
            description = "Synthetic bookmark $i generated for the M6 scroll benchmark.",
            siteName = "example.com",
            thumbnailPath = null,
            faviconPath = null,
            accentColor = null,
            thumbnailWidth = null,
            thumbnailHeight = null,
            imageCandidates = null,
            categoryId = Category.UNSORTED_ID,
            metadataState = MetadataState.FALLBACK,
            failureCause = null,
            fetchAttempts = 0,
            lastFetchAt = null,
            manualFields = 0,
            isPinned = false,
            createdAt = now - i,
            updatedAt = now - i,
        )
    }
    dao.insertAll(entities)
}
