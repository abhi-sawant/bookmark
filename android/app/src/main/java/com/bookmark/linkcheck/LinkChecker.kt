package com.bookmark.linkcheck

import com.bookmark.core.data.IoDispatcher
import com.bookmark.metadata.http.RequestUrls
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class LinkCheckOutcome(val status: LinkStatus, val httpCode: Int?)

/** The decision rules, kept pure so they are testable without a socket. */
object LinkStatusRules {
    /** Only a 404 or 410 means "gone"; everything else that is not a success is inconclusive. */
    fun fromHttpCode(code: Int): LinkStatus = when (code) {
        in 200..399 -> LinkStatus.OK
        404, 410 -> LinkStatus.BROKEN
        else -> LinkStatus.ERROR
    }

    /** HEAD is refused by plenty of healthy servers, so these results are re-checked with GET. */
    fun headNeedsGetRetry(code: Int): Boolean = code !in 200..399 && code != 404 && code != 410
}

/**
 * Asks a site whether a saved link still resolves: HEAD first, then a one-byte
 * ranged GET when HEAD is refused. Redirects are followed by hand (the shared
 * client does not follow them), at most [MAX_REDIRECTS], upgrading cleartext
 * hops like the metadata fetcher does.
 */
@Singleton
class LinkChecker @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun check(url: String): LinkCheckOutcome = withContext(io) {
        val head = request(url, head = true) ?: return@withContext LinkCheckOutcome(LinkStatus.ERROR, null)
        if (head.httpCode != null && !LinkStatusRules.headNeedsGetRetry(head.httpCode)) return@withContext head
        request(url, head = false) ?: head
    }

    private fun request(url: String, head: Boolean): LinkCheckOutcome? {
        var current = RequestUrls.normalizeForRequest(url, cleartextBlocked = true) ?: return null
        repeat(MAX_REDIRECTS + 1) {
            val response = try {
                client.newCall(build(current, head)).execute()
            } catch (e: Throwable) {
                return LinkCheckOutcome(LinkStatus.ERROR, null)
            }
            response.use {
                if (it.isRedirect) {
                    val location = it.header("Location") ?: return LinkCheckOutcome(LinkStatus.ERROR, it.code)
                    current = RequestUrls.resolveRedirect(current, location, cleartextBlocked = true)
                        ?: return LinkCheckOutcome(LinkStatus.ERROR, it.code)
                    return@use
                }
                return LinkCheckOutcome(LinkStatusRules.fromHttpCode(it.code), it.code)
            }
        }
        return LinkCheckOutcome(LinkStatus.ERROR, null) // a redirect loop
    }

    private fun build(url: String, head: Boolean): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        return if (head) builder.head().build() else builder.header("Range", "bytes=0-0").get().build()
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
