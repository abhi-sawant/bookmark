package com.bookmark.sync.data

import androidx.room.Entity

enum class TombstoneType { BOOKMARK, CATEGORY }

/**
 * Records that a bookmark/category was permanently deleted on this device, so
 * the next sync push can tell the server -- and only the server, since
 * bookmarks/categories themselves stay true hard-delete tables (the existing
 * Snackbar-undo UX is unaffected either way, sync on or off).
 *
 * A row is inserted only once a delete becomes irreversible (the undo window
 * closes -- see `BookmarkRepository.discardDeleted`/`CategoryRepository.discardDeleted`),
 * and removed once the push that reported it succeeds. Nothing else reads
 * this table.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["id", "entityType"])
data class SyncTombstoneEntity(
    val id: String,
    val entityType: String,
    val deletedAt: Long,
)
