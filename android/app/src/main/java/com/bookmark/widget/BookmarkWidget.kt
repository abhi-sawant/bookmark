package com.bookmark.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.bookmark.R
import com.bookmark.bookmarks.data.BookmarkRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bookmarkRepository(): BookmarkRepository
}

/** Builds and pushes the home-screen widget. Called from the provider and after app/widget saves. */
object BookmarkWidget {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ROW_IDS = intArrayOf(
        R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3, R.id.widget_row_4, R.id.widget_row_5,
    )

    /** Refreshes every placed widget; a no-op when none is on a home screen. */
    fun update(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, BookmarkWidgetProvider::class.java))
        if (ids.isEmpty()) {
            onDone()
            return
        }
        scope.launch {
            try {
                val repository = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java).bookmarkRepository()
                val rows = WidgetRows.pick(repository.observeAllLive().first())
                for (id in ids) manager.updateAppWidget(id, build(app, rows.map { it.title to it.url }))
            } finally {
                onDone()
            }
        }
    }

    private fun build(context: Context, rows: List<Pair<String, String>>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bookmarks)

        views.setOnClickPendingIntent(
            R.id.widget_save,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, WidgetSaveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        views.setViewVisibility(R.id.widget_empty, if (rows.isEmpty()) View.VISIBLE else View.GONE)
        ROW_IDS.forEachIndexed { index, viewId ->
            val row = rows.getOrNull(index)
            if (row == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, row.first)
                views.setOnClickPendingIntent(
                    viewId,
                    PendingIntent.getActivity(
                        context,
                        index + 1,
                        Intent(Intent.ACTION_VIEW, Uri.parse(row.second)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
        return views
    }
}
