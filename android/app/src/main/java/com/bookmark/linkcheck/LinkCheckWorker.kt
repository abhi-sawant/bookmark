package com.bookmark.linkcheck

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.model.Bookmark
import com.bookmark.core.util.UrlNormalizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Settings, "Check links". One work request for the whole library so the
 * concurrency limit and the one-request-per-host-per-second politeness delay
 * (the same rules as "Refresh all metadata") are enforceable.
 */
@HiltWorker
class LinkCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: LinkChecker,
    private val repository: BookmarkRepository,
    private val dao: LinkCheckDao,
) : CoroutineWorker(appContext, params) {

    private val gate = Semaphore(MAX_CONCURRENCY)
    private val hostLock = Mutex()
    private val lastRequestByHost = mutableMapOf<String, Long>()

    override suspend fun doWork(): Result = coroutineScope {
        val bookmarks = repository.observeAllLive().first()
        dao.deleteOrphans()
        bookmarks
            .map { bookmark -> async { gate.withPermit { checkOne(bookmark) } } }
            .awaitAll()
        Result.success()
    }

    private suspend fun checkOne(bookmark: Bookmark) {
        awaitHostTurn(bookmark.url)
        val outcome = checker.check(bookmark.url)
        dao.upsert(LinkCheckEntity(bookmark.id, outcome.status, outcome.httpCode, System.currentTimeMillis()))
    }

    private suspend fun awaitHostTurn(url: String) {
        val host = UrlNormalizer.host(url) ?: return
        val waitFor = hostLock.withLock {
            val now = System.currentTimeMillis()
            val earliest = (lastRequestByHost[host] ?: 0L) + HOST_DELAY_MILLIS
            val wait = (earliest - now).coerceAtLeast(0L)
            lastRequestByHost[host] = now + wait
            wait
        }
        if (waitFor > 0) delay(waitFor)
    }

    companion object {
        const val MAX_CONCURRENCY = 4
        const val HOST_DELAY_MILLIS = 1_000L
        const val UNIQUE_NAME = "link-check-all"
    }
}

@Singleton
class LinkCheckEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<LinkCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LinkCheckWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
