package com.bookmark.update

import android.content.Context
import android.content.pm.PackageManager
import com.bookmark.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A GitHub release newer than the installed build. */
data class AvailableUpdate(
    val version: String,
    val name: String,
    val changelog: String,
    val url: String,
)

@Serializable
private data class GithubRelease(
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * Asks GitHub for the latest published release and compares it with the
 * installed versionName. Uses the plain app client, not the API one: the
 * latter attaches the user's bearer token to every request.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    /** The newest release if it is ahead of this build and not skipped; null otherwise, including on any failure. */
    suspend fun checkForUpdate(): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatest() ?: return@runCatching null
            if (release.draft || release.prerelease) return@runCatching null
            val latest = release.tagName.removePrefix("v").removePrefix("V")
            if (!isNewer(latest, installedVersion())) return@runCatching null
            if (settingsRepository.skippedUpdateVersion.first() == latest) return@runCatching null
            AvailableUpdate(
                version = latest,
                name = release.name?.takeIf { it.isNotBlank() } ?: "Version $latest",
                changelog = release.body.orEmpty().trim(),
                url = release.htmlUrl,
            )
        }.getOrNull()
    }

    suspend fun skipVersion(version: String) = settingsRepository.setSkippedUpdateVersion(version)

    private fun fetchLatest(): GithubRelease? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return json.decodeFromString<GithubRelease>(body)
        }
    }

    private fun installedVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName.orEmpty()

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/abhi-sawant/bookmark/releases/latest"
    }
}

/** Numeric, dot-separated comparison ("2.10.0" > "2.9.1"); anything after a `-` is ignored. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
