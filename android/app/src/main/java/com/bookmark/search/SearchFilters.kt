package com.bookmark.search

import androidx.compose.runtime.Immutable
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category

/** Quick narrowing chips on the Search screen. Web mirrors this in features/home/selectors.ts. */
@Immutable
data class SearchFilters(
    val pinnedOnly: Boolean = false,
    /** "No category": bookmarks still in Unsorted. */
    val unsortedOnly: Boolean = false,
    /** Saved in the last seven days. */
    val thisWeek: Boolean = false,
    /** Not opened since it was saved. */
    val unreadOnly: Boolean = false,
    /** The last link check found the page gone. */
    val brokenOnly: Boolean = false,
) {
    val isActive: Boolean get() = pinnedOnly || unsortedOnly || thisWeek || unreadOnly || brokenOnly

    fun apply(
        list: List<Bookmark>,
        now: Long = System.currentTimeMillis(),
        brokenIds: Set<String> = emptySet(),
    ): List<Bookmark> =
        list.filter { b ->
            (!pinnedOnly || b.isPinned) &&
                (!unsortedOnly || b.categoryId == Category.UNSORTED_ID) &&
                (!thisWeek || b.createdAt >= now - WEEK_MS) &&
                (!unreadOnly || b.isUnread) &&
                (!brokenOnly || b.id in brokenIds)
        }

    companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** The last few searches, newest first, case-insensitively de-duplicated. */
object RecentSearches {
    const val MAX = 8

    fun add(current: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return current
        return (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
    }
}
