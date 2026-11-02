package com.bookmark.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The home-screen widget. The system calls [onUpdate] when it is placed and every ~30 minutes. */
class BookmarkWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        BookmarkWidget.update(context) { pending.finish() }
    }
}
