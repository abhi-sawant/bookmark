package com.bookmark.core.model

/** How long a bookmark stays in Trash before it is removed for good. Web mirrors this in data/trash.ts. */
object TrashPolicy {
    const val RETENTION_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Bookmarks trashed before this instant are due for permanent deletion. */
    fun cutoff(now: Long): Long = now - RETENTION_DAYS * DAY_MS

    /** Whole days left before [trashedAt] expires, never negative. */
    fun daysLeft(trashedAt: Long, now: Long): Int {
        val remainingMs = trashedAt + RETENTION_DAYS * DAY_MS - now
        return if (remainingMs <= 0) 0 else ((remainingMs + DAY_MS - 1) / DAY_MS).toInt()
    }
}
