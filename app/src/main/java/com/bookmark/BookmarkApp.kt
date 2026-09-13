package com.bookmark

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.bookmark.bookmarks.data.BookmarkRepository
import com.bookmark.core.data.ApplicationScope
import com.bookmark.share.DirectShareShortcuts
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class BookmarkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var bookmarkRepository: BookmarkRepository

    @Inject lateinit var directShareShortcuts: DirectShareShortcuts

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Cheap: list a directory and diff against the database (spec 7.4).
            bookmarkRepository.sweepOrphanThumbnails()
            directShareShortcuts.publish()
        }
    }
}
