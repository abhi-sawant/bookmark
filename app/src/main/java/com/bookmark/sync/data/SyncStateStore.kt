package com.bookmark.sync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.bookmark.account.data.accountDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Sync's cursor + last-sync-time, in the same "account" DataStore file as
 * [com.bookmark.account.data.AuthTokenStore] -- both are sync-related identity/state.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val cursor = longPreferencesKey("sync_cursor")
        val lastSyncedAt = longPreferencesKey("sync_last_synced_at")
    }

    suspend fun cursor(): Long = context.accountDataStore.data.first()[Keys.cursor] ?: 0L

    suspend fun setCursor(value: Long) {
        context.accountDataStore.edit { it[Keys.cursor] = value }
    }

    suspend fun lastSyncedAt(): Long? = context.accountDataStore.data.first()[Keys.lastSyncedAt]

    val lastSyncedAtFlow: Flow<Long?> = context.accountDataStore.data.map { it[Keys.lastSyncedAt] }

    suspend fun recordSuccess(atMillis: Long) {
        context.accountDataStore.edit { it[Keys.lastSyncedAt] = atMillis }
    }
}
