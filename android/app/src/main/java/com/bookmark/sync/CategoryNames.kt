package com.bookmark.sync

import com.bookmark.core.model.Category
import java.util.Locale

/**
 * Category names are unique ignoring case on the phone, but nothing stops the
 * server holding two categories with the same name (for example one made on
 * the web and one on another phone). Pulling the second one would evict the
 * first through the unique index, orphaning its bookmarks and stalling sync on
 * a foreign-key error. Instead the incoming one gets a local " (2)" suffix.
 */
object CategoryNames {
    inline fun unique(name: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(name)) return name
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = name.take(Category.NAME_MAX_LENGTH - suffix.length) + suffix
            if (!isTaken(candidate)) return candidate
            n++
        }
    }

    /** Case-insensitive membership, matching the database's NOCASE unique index. */
    fun takenIn(names: Collection<String>): (String) -> Boolean {
        val lower = names.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        return { it.lowercase(Locale.ROOT) in lower }
    }
}
