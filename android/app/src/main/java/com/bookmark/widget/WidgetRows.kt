package com.bookmark.widget

import com.bookmark.core.model.Bookmark

/** Which bookmarks the home-screen widget lists: pinned first, then newest, capped at [MAX]. */
object WidgetRows {
    const val MAX = 5

    fun pick(bookmarks: List<Bookmark>, max: Int = MAX): List<Bookmark> =
        bookmarks
            .sortedWith(compareByDescending<Bookmark> { it.isPinned }.thenByDescending { it.createdAt })
            .take(max)
}
