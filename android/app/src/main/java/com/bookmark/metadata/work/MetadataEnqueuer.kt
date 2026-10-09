package com.bookmark.metadata.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The one place metadata work is scheduled (spec 8.5), and the one place the
 * privacy toggle is enforced on the way in (spec 11).
 *
 * Keeping WorkManager behind this means `BookmarkRepository` does not import it,
 * which matters for the DAO-level instrumented tests.
 */
@Singleton
class MetadataEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Automatic fetch after a save. Silently does nothing when the user has
     * turned previews off -- with the toggle off the app makes no requests at
     * all (spec 11).
     */
    suspend fun enqueueAutomatic(bookmarkId: String) {
        if (!settingsRepository.preferences.first().fetchPreviewsAutomatically) return
        workManager.enqueueUniqueWork(
            uniqueName(bookmarkId),
            // An automatic fetch must not displace one already queued or running.
            ExistingWorkPolicy.KEEP,
            MetadataWorker.request(bookmarkId, manual = false),
        )
    }

    /**
     * The user asked for this one -- "Retry fetch" on the detail sheet, or
     * "Refresh preview" on the duplicate sheet.
     *
     * Deliberately not gated on the toggle: spec 11 says that with automatic
     * previews off, bookmarks use fallbacks "until the user taps fetch
     * manually". REPLACE, because the point is to supersede whatever is queued,
     * and a manual retry is not bound by the three-attempt ceiling.
     */
    fun enqueueManual(bookmarkId: String) {
        workManager.enqueueUniqueWork(
            uniqueName(bookmarkId),
            ExistingWorkPolicy.REPLACE,
            MetadataWorker.request(bookmarkId, manual = true),
        )
    }

    /** Settings, "Refresh all metadata". Also a deliberate user action. */
    fun enqueueRefreshAll() {
        workManager.enqueueUniqueWork(
            REFRESH_ALL_WORK,
            ExistingWorkPolicy.KEEP,
            RefreshAllWorker.request(),
        )
    }

    /**
     * Called when the privacy toggle goes off. Cancelling queued work is the
     * difference between "makes no new requests" and "makes no requests":
     * without this, everything enqueued before the toggle flipped would still
     * run the next time the device has a network.
     */
    fun cancelAllAutomatic() {
        workManager.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "metadata"
        const val REFRESH_ALL_WORK = "metadata:refresh-all"

        fun uniqueName(bookmarkId: String) = "metadata:$bookmarkId"

        /** Spec 8.5: connected, exponential from 30s. */
        internal val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        internal inline fun <reified W : androidx.work.ListenableWorker> buildRequest(
            data: Data = Data.EMPTY,
        ) = OneTimeWorkRequestBuilder<W>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
    }
}
