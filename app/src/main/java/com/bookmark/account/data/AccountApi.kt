package com.bookmark.account.data

import com.bookmark.account.di.ApiHttpClient
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.network.API_BASE_URL
import com.bookmark.core.network.ApiJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Thrown for a non-2xx response; [code] is the server's error code when parseable. */
class ApiException(val code: String?, message: String) : Exception(message)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Plain synchronous OkHttp calls, mirroring [com.bookmark.metadata.http.HtmlFetcher]'s
 * style rather than a callback/enqueue one -- every call already runs inside
 * `withContext(io)`.
 */
@Singleton
class AccountApi @Inject constructor(
    @ApiHttpClient private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun register(email: String, password: String, deviceName: String): AuthResponse =
        withContext(io) {
            val body = ApiJson.encodeToString(RegisterRequest(email, password, deviceName))
            execute<AuthResponse>(post("register.php", body))
        }

    suspend fun login(email: String, password: String, deviceName: String): AuthResponse =
        withContext(io) {
            val body = ApiJson.encodeToString(LoginRequest(email, password, deviceName))
            execute<AuthResponse>(post("login.php", body))
        }

    /** Auth required -- [ApiHttpClient]'s `AuthInterceptor` attaches the header. */
    suspend fun logout(): Unit = withContext(io) {
        client.newCall(post("logout.php", "{}")).execute().use { response ->
            if (!response.isSuccessful) throw response.toApiException()
        }
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse = withContext(io) {
        val body = ApiJson.encodeToString(ForgotPasswordRequest(email))
        execute<ForgotPasswordResponse>(post("forgot_password.php", body))
    }

    private fun post(path: String, jsonBody: String): Request = Request.Builder()
        .url(API_BASE_URL + "api/" + path)
        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw response.toApiException()
            val text = response.body.string()
            return ApiJson.decodeFromString(text)
        }
    }

    private fun Response.toApiException(): ApiException {
        val text = runCatching { body.string() }.getOrNull()
        val parsed = text?.let { runCatching { ApiJson.decodeFromString<ApiErrorBody>(it) }.getOrNull() }
        return ApiException(
            code = parsed?.error?.code,
            message = parsed?.error?.message ?: "Request failed ($code)",
        )
    }
}
