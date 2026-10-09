package com.bookmark

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.data.ApplicationScope
import com.bookmark.debug.StrictModeInit
import com.bookmark.share.DirectShareShortcuts
import com.bookmark.sync.work.SyncEnqueuer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class BookmarkApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var bookmarkRepository: BookmarkRepository

    @Inject lateinit var directShareShortcuts: DirectShareShortcuts

    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var syncEnqueuer: SyncEnqueuer

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    override fun onCreate() {
        StrictModeInit.install()
        super.onCreate()
        applicationScope.launch {
            // Cheap: list a directory and diff against the database (spec 7.4).
            bookmarkRepository.sweepOrphanThumbnails()
            directShareShortcuts.publish()
        }

        // A no-op when signed out; idempotent via ExistingPeriodicWorkPolicy.KEEP
        // when already scheduled -- safe to call unconditionally on every start.
        syncEnqueuer.schedulePeriodic()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    syncEnqueuer.syncNow()
                }
            },
        )
    }
}
