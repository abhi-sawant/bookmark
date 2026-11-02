package com.bookmark.linkcheck

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Outcome of the last "Check links" run for one bookmark. Device-local; never synced. */
enum class LinkStatus {
    OK,

    /** The server answered 404 or 410: the page is gone. */
    BROKEN,

    /** Anything inconclusive (timeouts, 403/429/5xx, TLS trouble, offline): not treated as broken. */
    ERROR,
}

/**
 * Kept in its own table, keyed by bookmark id, rather than as columns on
 * `bookmarks`: a bookmark row is replaced wholesale when sync pulls it, which
 * would wipe device-local columns. There is deliberately no foreign key for the
 * same reason (a REPLACE would cascade); orphans are swept at launch.
 */
@Entity(tableName = "link_checks")
data class LinkCheckEntity(
    @PrimaryKey val bookmarkId: String,
    val status: LinkStatus,
    val httpCode: Int?,
    val checkedAt: Long,
)

@Dao
interface LinkCheckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(check: LinkCheckEntity)

    @Query("SELECT bookmarkId FROM link_checks WHERE status = 'BROKEN'")
    fun observeBrokenIds(): Flow<List<String>>

    @Query("SELECT * FROM link_checks")
    fun observeAll(): Flow<List<LinkCheckEntity>>

    /** Rows whose bookmark no longer exists (deleted for good, or removed by sync). */
    @Query("DELETE FROM link_checks WHERE bookmarkId NOT IN (SELECT id FROM bookmarks)")
    suspend fun deleteOrphans()

    @Query("DELETE FROM link_checks")
    suspend fun clear()
}
