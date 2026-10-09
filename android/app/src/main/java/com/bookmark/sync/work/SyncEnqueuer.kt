package com.bookmark.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bookmark.account.data.AuthTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place sync work is scheduled, mirroring `MetadataEnqueuer`. Every
 * trigger is a no-op when signed out -- sync rides entirely on an opt-in
 * account, so a device that never signs in makes no network calls at all.
 */
@Singleton
class SyncEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authTokenStore: AuthTokenStore,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private fun signedOut(): Boolean = authTokenStore.currentTokenOrNull() == null

    /** ~15s after any local mutation -- a burst of edits collapses to one sync. */
    fun scheduleDebounced() {
        if (signedOut()) return
        workManager.enqueueUniqueWork(
            DEBOUNCED_WORK,
            ExistingWorkPolicy.REPLACE,
            buildRequest<SyncWorker>().setInitialDelay(15, TimeUnit.SECONDS).build(),
        )
    }

    /** Settings, "Sync now" -- a deliberate user action, does not displace a run already in flight. */
    fun syncNow() {
        if (signedOut()) return
        workManager.enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.KEEP,
            buildRequest<SyncWorker>().build(),
        )
    }

    /** Scheduled once at sign-in (and, harmlessly, on every app start -- KEEP makes it idempotent). */
    fun schedulePeriodic() {
        if (signedOut()) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(8, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Sign-out: cancels every queued/running sync, regardless of the (now signed-out) state. */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "sync"
        private const val DEBOUNCED_WORK = "sync:debounced"
        private const val NOW_WORK = "sync:now"
        private const val PERIODIC_WORK = "sync:periodic"

        private val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        internal inline fun <reified W : androidx.work.ListenableWorker> buildRequest(
            data: Data = Data.EMPTY,
        ) = OneTimeWorkRequestBuilder<W>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
    }
}
