package com.bookmark.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bookmark.sync.SyncRepository
import com.bookmark.sync.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs one push-then-pull cycle. Mirrors `MetadataWorker`'s shape. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncRepository.sync()) {
        is SyncResult.Success -> Result.success()
        // Never actually scheduled given SyncEnqueuer's own signed-out guard,
        // but handled gracefully rather than treated as a failure if it races.
        is SyncResult.NotSignedIn -> Result.success()
        is SyncResult.Failed -> Result.retry()
    }
}
