package com.bookmark.metadata.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.ManualField
import com.bookmark.core.model.MetadataState
import com.bookmark.metadata.FailureCause
import com.bookmark.metadata.MetadataFetcher
import com.bookmark.metadata.MetadataResult
import com.bookmark.metadata.PageMetadata
import com.bookmark.metadata.image.StoredThumbnail
import com.bookmark.metadata.image.ThumbnailPipeline
import com.bookmark.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Fetches metadata for one bookmark and writes the outcome (spec 8.1, 8.5).
 *
 * WorkManager has no built-in attempt ceiling, so the three-attempt limit is
 * enforced here: on the third failure the row goes terminal `FAILED` and the
 * worker returns *success*, not failure -- the work is genuinely finished, and
 * reporting failure would leave it looking unhandled in `WorkManager`'s own
 * bookkeeping.
 */
@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fetcher: MetadataFetcher,
    private val repository: BookmarkRepository,
    private val thumbnails: ThumbnailPipeline,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookmarkId = inputData.getString(KEY_BOOKMARK_ID) ?: return Result.success()
        val manual = inputData.getBoolean(KEY_MANUAL, false)

        // Re-checked here as well as at enqueue time: the user can turn previews
        // off between the two, and the guarantee is no requests at all (spec 11).
        if (!manual && !settings.preferences.first().fetchPreviewsAutomatically) {
            return Result.success()
        }

        // Deleted while queued. Nothing to do, and nothing to report.
        val bookmark = repository.findById(bookmarkId) ?: return Result.success()

        return fetchInto(bookmark, manual)
    }

    private suspend fun fetchInto(bookmark: Bookmark, manual: Boolean): Result {
        repository.markFetching(bookmark.id)

        val attemptsMade = if (manual) 1 else runAttemptCount + 1

        return when (val result = fetcher.fetch(bookmark.url)) {
            is MetadataResult.Success -> apply(bookmark, result.metadata, null, attemptsMade)
            is MetadataResult.Partial -> apply(bookmark, result.metadata, result.cause, attemptsMade)

            // Reachable, but nothing usable. Terminal and silent -- FALLBACK is
            // not an error state (spec 8.1), so there is nothing to retry.
            is MetadataResult.Fallback -> {
                repository.recordFetchOutcome(
                    bookmark.id, MetadataState.FALLBACK, result.cause, attemptsMade,
                )
                Result.success()
            }

            is MetadataResult.Failed -> retryOrGiveUp(bookmark, result.cause, attemptsMade, manual)

            // Only reachable if the engine decides it cannot even try.
            MetadataResult.Pending -> Result.success()
        }
    }

    /**
     * A hard failure is retried up to [MAX_ATTEMPTS] with exponential backoff.
     * Between attempts the row returns to `PENDING`, which is what the spec 8.1
     * diagram shows and what puts the quiet "Preview pending" pill back on the
     * card rather than an error affordance.
     */
    private suspend fun retryOrGiveUp(
        bookmark: Bookmark,
        cause: FailureCause,
        attemptsMade: Int,
        manual: Boolean,
    ): Result {
        val exhausted = manual || attemptsMade >= MAX_ATTEMPTS
        return if (exhausted) {
            repository.recordFetchOutcome(bookmark.id, MetadataState.FAILED, cause, attemptsMade)
            Result.success()
        } else {
            repository.recordFetchOutcome(bookmark.id, MetadataState.PENDING, cause, attemptsMade)
            Result.retry()
        }
    }

    private suspend fun apply(
        bookmark: Bookmark,
        metadata: PageMetadata,
        cause: FailureCause?,
        attemptsMade: Int,
    ): Result {
        // The user picked their own image; leave it and do not spend a download.
        val thumbnailLocked = ManualField.isSet(bookmark.manualFields, ManualField.THUMBNAIL)

        val stored: StoredThumbnail? = if (thumbnailLocked || metadata.imageCandidates.isEmpty()) {
            null
        } else {
            thumbnails.store(
                candidates = metadata.imageCandidates,
                bookmarkId = bookmark.id,
                directory = repository.thumbnailDir(),
            )
        }

        val hasTitle = !metadata.title.isNullOrBlank()
        val hasImage = stored != null || thumbnailLocked || bookmark.thumbnailPath != null

        val state = when {
            hasTitle && hasImage -> MetadataState.SUCCESS
            // "Image download failed only" is PARTIAL and silent (spec 8.6).
            hasTitle || hasImage -> MetadataState.PARTIAL
            else -> MetadataState.FALLBACK
        }

        repository.applyMetadata(
            bookmarkId = bookmark.id,
            metadata = metadata,
            thumbnail = stored,
            state = state,
            // PARTIAL and FALLBACK show no message, so a cause is only recorded
            // when it would actually be read.
            cause = cause.takeIf { state == MetadataState.FAILED },
            attempts = attemptsMade,
        )
        return Result.success()
    }

    companion object {
        const val KEY_BOOKMARK_ID = "bookmarkId"
        const val KEY_MANUAL = "manual"

        /** Spec 8.5: three automatic attempts, then terminal FAILED for good. */
        const val MAX_ATTEMPTS = 3

        fun request(bookmarkId: String, manual: Boolean): OneTimeWorkRequest =
            MetadataEnqueuer.buildRequest<MetadataWorker>(
                Data.Builder()
                    .putString(KEY_BOOKMARK_ID, bookmarkId)
                    .putBoolean(KEY_MANUAL, manual)
                    .build(),
            )
    }
}
