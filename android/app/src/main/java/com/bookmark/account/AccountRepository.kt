package com.bookmark.account

import android.os.Build
import com.bookmark.account.data.AccountApi
import com.bookmark.account.data.AuthState
import com.bookmark.account.data.AuthTokenStore
import com.bookmark.sync.work.SyncEnqueuer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps [AccountApi] + [AuthTokenStore]: the one place sign-in/out also drives
 * the sync engine's own lifecycle (starting/stopping periodic work).
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountApi: AccountApi,
    private val authTokenStore: AuthTokenStore,
    private val syncEnqueuer: SyncEnqueuer,
) {

    val authState: StateFlow<AuthState> = authTokenStore.authState

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        val response = accountApi.register(email, password, deviceName())
        authTokenStore.signIn(response.token, email)
        syncEnqueuer.schedulePeriodic()
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = accountApi.login(email, password, deviceName())
        authTokenStore.signIn(response.token, email)
        syncEnqueuer.schedulePeriodic()
    }

    /**
     * Best-effort revoke on the server; the local session is always cleared
     * regardless of whether the network call succeeded -- a stuck token that
     * can't reach the server is exactly when the user most wants "signed out"
     * to actually mean signed out on this device.
     */
    suspend fun logout(): Result<Unit> {
        val networkResult = runCatching { accountApi.logout() }
        authTokenStore.signOut()
        syncEnqueuer.cancelAll()
        return networkResult
    }

    suspend fun forgotPassword(email: String): Result<String> = runCatching {
        val response = accountApi.forgotPassword(email)
        response.message ?: "If that email has an account, a reset link is on its way."
    }

    private fun deviceName(): String {
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.ifBlank { "Android device" }
        } else {
            "$manufacturer $model".trim().ifBlank { "Android device" }
        }
    }
}
