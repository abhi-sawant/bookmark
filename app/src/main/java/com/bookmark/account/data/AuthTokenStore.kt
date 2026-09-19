package com.bookmark.account.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookmark.core.data.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Its own DataStore file ("account"), separate from the cosmetic "settings"
 * one -- excluded from Android Auto Backup via `data_extraction_rules.xml` so
 * the bearer token never leaves the device via backup. Also used by
 * `com.bookmark.sync.data.SyncStateStore` (same file, both sync-related
 * identity/state) -- internal rather than private so it stays a single
 * DataStore instance per process; declaring a second `preferencesDataStore`
 * delegate for the same file name would crash at runtime.
 */
internal val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** Whether this device currently holds a sync session. */
sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val email: String, val token: String) : AuthState
}

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private object Keys {
        val token = stringPreferencesKey("token")
        val email = stringPreferencesKey("email")
        val deviceId = stringPreferencesKey("device_id")
    }

    /**
     * Hot, so [AuthInterceptor] (an OkHttp `Interceptor`, which cannot suspend)
     * can read the current token synchronously via [currentTokenOrNull].
     */
    val authState: StateFlow<AuthState> = context.accountDataStore.data
        .map { prefs -> prefs.toAuthState() }
        .stateIn(applicationScope, SharingStarted.Eagerly, AuthState.SignedOut)

    fun currentTokenOrNull(): String? = (authState.value as? AuthState.SignedIn)?.token

    /** Generated once and persisted; purely informational (`X-Device-Id` header). */
    suspend fun localDeviceId(): String {
        val existing = context.accountDataStore.data.first()[Keys.deviceId]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.accountDataStore.edit { it[Keys.deviceId] = generated }
        return generated
    }

    suspend fun signIn(token: String, email: String) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.token] = token
            prefs[Keys.email] = email
        }
    }

    suspend fun signOut() {
        context.accountDataStore.edit { prefs ->
            prefs.remove(Keys.token)
            prefs.remove(Keys.email)
        }
    }

    private fun Preferences.toAuthState(): AuthState {
        val token = this[Keys.token]
        val email = this[Keys.email]
        return if (token != null && email != null) {
            AuthState.SignedIn(email = email, token = token)
        } else {
            AuthState.SignedOut
        }
    }
}
