package com.bookmark

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.data.ApplicationScope
import com.bookmark.debug.StrictModeInit
import com.bookmark.share.DirectShareShortcuts
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
    }
}
