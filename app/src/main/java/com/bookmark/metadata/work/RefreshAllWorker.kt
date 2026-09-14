package com.bookmark.metadata.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import com.bookmark.core.util.UrlNormalizer
import com.bookmark.metadata.MetadataFetcher
import com.bookmark.metadata.MetadataResult
import com.bookmark.metadata.image.ThumbnailPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Settings, "Refresh all metadata" (spec 5.6, 8.5).
 *
 * One work request rather than one per bookmark, so the concurrency limit of 4
 * and the 1s per-host politeness delay are enforceable -- fanning out to N
 * unique works would hand scheduling to WorkManager and let a library full of
 * links to the same host hammer it.
 *
 * Only `FAILED` and `FALLBACK` rows are eligible: `SUCCESS` and `PARTIAL` already
 * look right, and re-fetching them would be churn.
 */
@HiltWorker
class RefreshAllWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetcher: MetadataFetcher,
    private val repository: BookmarkRepository,
    private val thumbnails: ThumbnailPipeline,
) : CoroutineWorker(appContext, params) {

    private val gate = Semaphore(MAX_CONCURRENCY)
    private val hostLock = Mutex()
    private val lastRequestByHost = mutableMapOf<String, Long>()

    override suspend fun doWork(): Result = coroutineScope {
        val eligible = repository.findByStates(ELIGIBLE)
        if (eligible.isEmpty()) return@coroutineScope Result.success()

        eligible
            .map { bookmark -> async { gate.withPermit { refresh(bookmark) } } }
            .awaitAll()

        Result.success()
    }

    private suspend fun refresh(bookmark: Bookmark) {
        awaitHostTurn(bookmark.url)
        repository.markFetching(bookmark.id)

        // A refresh is one attempt, not a retry cycle: the automatic ceiling has
        // already been reached for anything in FAILED, and the user asking again
        // is what re-opens it.
        val attempts = bookmark.fetchAttempts + 1

        when (val result = runCatching { fetcher.fetch(bookmark.url) }.getOrNull()) {
            is MetadataResult.Success, is MetadataResult.Partial -> {
                val metadata = when (result) {
                    is MetadataResult.Success -> result.metadata
                    is MetadataResult.Partial -> result.metadata
                    else -> return
                }
                val locked = ManualField.isSet(bookmark.manualFields, ManualField.THUMBNAIL)
                val stored = if (locked || metadata.imageCandidates.isEmpty()) {
                    null
                } else {
                    thumbnails.store(metadata.imageCandidates, bookmark.id, repository.thumbnailDir())
                }
                val hasImage = stored != null || locked || bookmark.thumbnailPath != null
                val state = when {
                    !metadata.title.isNullOrBlank() && hasImage -> MetadataState.SUCCESS
                    !metadata.title.isNullOrBlank() || hasImage -> MetadataState.PARTIAL
                    else -> MetadataState.FALLBACK
                }
                repository.applyMetadata(bookmark.id, metadata, stored, state, null, attempts)
            }

            is MetadataResult.Fallback ->
                repository.recordFetchOutcome(bookmark.id, MetadataState.FALLBACK, result.cause, attempts)

            is MetadataResult.Failed ->
                repository.recordFetchOutcome(bookmark.id, MetadataState.FAILED, result.cause, attempts)

            // Null means fetch itself threw; leave the row where it was rather
            // than inventing a cause.
            MetadataResult.Pending, null ->
                repository.recordFetchOutcome(bookmark.id, bookmark.metadataState, null, attempts)
        }
    }

    /** Spec 8.5: at most one request per host per second. */
    private suspend fun awaitHostTurn(url: String) {
        val host = UrlNormalizer.host(url) ?: return
        val waitFor = hostLock.withLock {
            val now = System.currentTimeMillis()
            val earliest = (lastRequestByHost[host] ?: 0L) + HOST_DELAY_MILLIS
            val wait = (earliest - now).coerceAtLeast(0L)
            // Reserved before releasing the lock so concurrent workers queue up
            // behind each other rather than all reading the same last-request time.
            lastRequestByHost[host] = now + wait
            wait
        }
        if (waitFor > 0) delay(waitFor)
    }

    companion object {
        const val MAX_CONCURRENCY = 4
        const val HOST_DELAY_MILLIS = 1_000L

        /**
         * Spec 5.6 names only the failed and fallback states, but PENDING has to
         * be here too: a bookmark saved while "Fetch link previews
         * automatically" was off is never enqueued, so without this it would sit
         * in PENDING forever with no way back. SUCCESS and PARTIAL already look
         * right and re-fetching them would be churn.
         */
        val ELIGIBLE = listOf(MetadataState.FAILED, MetadataState.FALLBACK, MetadataState.PENDING)

        fun request(): OneTimeWorkRequest = MetadataEnqueuer.buildRequest<RefreshAllWorker>()
    }
}
