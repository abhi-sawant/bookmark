package com.bookmark.bookmarks.home

import com.bookmark.core.model.Bookmark
import kotlin.random.Random

/**
 * "Surprise me": a random bookmark from the older half of the collection, so
 * the button resurfaces things saved a while ago rather than yesterday's link.
 * Mirrors web/src/features/home/surprise.ts.
 */
fun pickSurprise(list: List<Bookmark>, random: Random = Random): Bookmark? {
    if (list.isEmpty()) return null
    val oldestFirst = list.sortedBy { it.createdAt }
    val pool = oldestFirst.take(maxOf(1, (oldestFirst.size + 1) / 2))
    return pool[random.nextInt(pool.size)]
}
