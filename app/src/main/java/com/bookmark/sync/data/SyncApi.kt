package com.bookmark.sync.data

import com.bookmark.account.data.ApiException
import com.bookmark.account.data.ApiErrorBody
import com.bookmark.account.di.ApiHttpClient
import com.bookmark.core.data.IoDispatcher
import com.bookmark.core.network.API_BASE_URL
import com.bookmark.core.network.ApiJson
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()

/** Same synchronous-OkHttp style as `AccountApi`. */
@Singleton
class SyncApi @Inject constructor(
    @ApiHttpClient private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun pull(since: Long, limit: Int = 500): SyncPullResponse = withContext(io) {
        val url = (API_BASE_URL + "api/sync/pull.php").toHttpUrl().newBuilder()
            .addQueryParameter("since", since.toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        execute(request)
    }

    suspend fun push(request: SyncPushRequest): SyncPushResponse = withContext(io) {
        val body = ApiJson.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = Request.Builder()
            .url(API_BASE_URL + "api/sync/push.php")
            .post(body)
            .build()
        execute(httpRequest)
    }

    suspend fun uploadThumbnail(bookmarkId: String, file: File): String = withContext(io) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("bookmark_id", bookmarkId)
            .addFormDataPart("file", file.name, file.asRequestBody(WEBP_MEDIA_TYPE))
            .build()
        val request = Request.Builder()
            .url(API_BASE_URL + "api/thumbnails/upload.php")
            .post(body)
            .build()
        val result: UploadResponse = execute(request)
        result.url
    }

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

@kotlinx.serialization.Serializable
private data class UploadResponse(val url: String)
