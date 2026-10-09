package com.bookmark.metadata.http

import com.bookmark.metadata.FailureCause
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import com.bookmark.core.data.IoDispatcher

/**
 * The HTTP half of the metadata engine (spec 7.2).
 *
 * The two-User-Agent strategy is the single highest-leverage rule in that
 * section: many sites serve Open Graph tags only to recognised crawlers, and
 * others block unknown agents outright. Desktop Chrome first, then
 * `facebookexternalhit/1.1` -- the agent almost everyone whitelists precisely so
 * link previews work -- and never a third. The ceiling lives in [fetch] rather
 * than in a convention, so it cannot drift.
 */
@Singleton
class HtmlFetcher internal constructor(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher,
    /**
     * Mirrors `usesCleartextTraffic="false"` in the manifest: with cleartext
     * blocked at the socket, an `http://` URL can only work if it is upgraded.
     * Always true in the app; tests drive the rest of the policy over
     * MockWebServer, which speaks cleartext.
     */
    private val upgradeCleartext: Boolean,
) {

    // A separate constructor rather than a default argument: Dagger does not
    // read Kotlin default values, so a defaulted Boolean asks it for a Boolean
    // binding that nothing provides.
    @Inject
    constructor(
        client: OkHttpClient,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(client, io, upgradeCleartext = true)

    suspend fun fetch(url: String): FetchOutcome = withContext(io) {
        val first = attempt(url, UserAgent.CHROME)

        if (!warrantsCrawlerRetry(first)) return@withContext first

        val second = attempt(url, UserAgent.FACEBOOK)
        // A retry that lands worse than the first attempt is discarded: a site
        // that 403s the crawler UA but served usable HTML to Chrome should keep
        // the Chrome result.
        if (second is FetchOutcome.Html || first !is FetchOutcome.Html) second else first
    }

    /**
     * Retry once when the first attempt was refused, or when it succeeded but
     * the page carried no social tags at all -- the signal that this site only
     * emits them for crawlers.
     */
    private fun warrantsCrawlerRetry(outcome: FetchOutcome): Boolean = when (outcome) {
        is FetchOutcome.Failure -> outcome.cause == FailureCause.BLOCKED
        is FetchOutcome.Html -> outcome.document
            .select("meta[property^=og:], meta[name^=og:], meta[name^=twitter:], meta[property^=twitter:]")
            .isEmpty()
        else -> false
    }

    private fun attempt(url: String, userAgent: String): FetchOutcome {
        // usesCleartextTraffic=false blocks http:// at the socket, so every hop
        // is upgraded rather than only the URL the user typed (spec 7.2).
        var current = RequestUrls.normalizeForRequest(url, upgradeCleartext)
            ?: return FetchOutcome.Failure(FailureCause.UNREACHABLE)

        repeat(MAX_REDIRECTS + 1) {
            val response = try {
                client.newCall(request(current, userAgent)).execute()
            } catch (e: Throwable) {
                return FetchOutcome.Failure(e.toCause())
            }

            response.use {
                if (it.isRedirect) {
                    val location = it.header("Location")
                        ?: return FetchOutcome.Failure(FailureCause.UNREACHABLE)
                    current = RequestUrls.resolveRedirect(current, location, upgradeCleartext)
                        ?: return FetchOutcome.Failure(FailureCause.UNREACHABLE)
                    return@use
                }
                return handle(it, current)
            }
        }
        // More than five hops is a redirect loop, not a slow site.
        return FetchOutcome.Failure(FailureCause.UNREACHABLE)
    }

    private fun request(url: String, userAgent: String) = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent)
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Accept-Language", acceptLanguage())
        // 512KB is far past </head> on any sane page; the read usually stops
        // well before this via the early abort below.
        .header("Range", "bytes=0-${MAX_BODY_BYTES - 1}")
        .get()
        .build()

    private fun handle(response: Response, url: String): FetchOutcome {
        if (!response.isSuccessful) return FetchOutcome.Failure(response.code.toCause())

        val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
        val mimeType = contentType.substringBefore(';').trim()

        return when {
            mimeType == "text/html" || mimeType == "application/xhtml+xml" -> {
                val charset = charsetOf(contentType)
                val bytes = readHead(response, scanForHeadClose = !isWideCharset(charset))
                // Jsoup re-detects from <meta charset> when the header omits one,
                // and defaults to UTF-8 -- spec 7.5's encoding rule, for free.
                val document = Jsoup.parse(bytes.inputStream(), charset, url)
                FetchOutcome.Html(document, url)
            }

            mimeType.startsWith("image/") -> FetchOutcome.Image(url)
            mimeType == "application/pdf" -> FetchOutcome.Pdf(url)
            // Anything else has no preview to give (spec 7.5).
            else -> FetchOutcome.Failure(FailureCause.NOTHING_USABLE)
        }
    }

    /**
     * Reads until `</head>`, the byte cap, or the end of the body.
     *
     * Scanning for the ASCII bytes of `</head` is safe for every ASCII-superset
     * encoding -- UTF-8, ISO-8859-1, windows-1252 -- which is everything the web
     * serves in practice. A declared UTF-16 charset interleaves NUL bytes, so the
     * scan is skipped there and the cap does the bounding instead.
     */
    private fun readHead(response: Response, scanForHeadClose: Boolean): ByteArray {
        val stream = response.body.byteStream()
        var buffer = ByteArray(INITIAL_BUFFER_BYTES)
        var size = 0
        var scannedUpTo = 0

        while (size < MAX_BODY_BYTES) {
            if (size == buffer.size) buffer = buffer.copyOf(minOf(buffer.size * 2, MAX_BODY_BYTES))

            val read = try {
                stream.read(buffer, size, buffer.size - size)
            } catch (e: IOException) {
                break // Whatever arrived is still worth parsing.
            }
            if (read == -1) break
            size += read

            if (!scanForHeadClose) continue

            // Re-scan from just behind the previous frontier so a needle split
            // across two reads is still found.
            val from = maxOf(0, scannedUpTo - HEAD_CLOSE.size + 1)
            if (indexOfAsciiIgnoreCase(buffer, size, HEAD_CLOSE, from) != -1) {
                return buffer.copyOf(size)
            }
            scannedUpTo = size
        }
        return buffer.copyOf(size)
    }

    private fun indexOfAsciiIgnoreCase(haystack: ByteArray, size: Int, needle: ByteArray, from: Int): Int {
        outer@ for (start in from..size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset].lowercaseAscii() != needle[offset]) continue@outer
            }
            return start
        }
        return -1
    }

    private fun Byte.lowercaseAscii(): Byte =
        if (this >= 'A'.code.toByte() && this <= 'Z'.code.toByte()) (this + 32).toByte() else this

    private fun charsetOf(contentType: String): String? =
        Regex("charset=([^;\\s]+)").find(contentType)
            ?.groupValues?.get(1)
            ?.trim('"', '\'')
            ?.takeIf { runCatching { java.nio.charset.Charset.isSupported(it) }.getOrDefault(false) }

    private fun isWideCharset(charset: String?): Boolean =
        charset != null && charset.lowercase(Locale.ROOT).startsWith("utf-16")

    /** Device locale first, then English (spec 7.2). */
    private fun acceptLanguage(): String = "${Locale.getDefault().toLanguageTag()},en;q=0.8"

    /** Spec 8.6, verbatim. */
    private fun Int.toCause(): FailureCause = when (this) {
        401, 403, 429 -> FailureCause.BLOCKED
        404, 410 -> FailureCause.NOT_FOUND
        in 500..599 -> FailureCause.SERVER_ERROR
        else -> FailureCause.UNREACHABLE
    }

    private fun Throwable.toCause(): FailureCause = when (this) {
        is SocketTimeoutException, is InterruptedIOException -> FailureCause.TIMEOUT
        is UnknownHostException, is ConnectException, is SSLException -> FailureCause.UNREACHABLE
        is IOException -> FailureCause.UNREACHABLE
        else -> FailureCause.UNREACHABLE
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_BYTES = 512 * 1024
        const val INITIAL_BUFFER_BYTES = 16 * 1024
        val HEAD_CLOSE = "</head".toByteArray(Charsets.US_ASCII)
    }
}

/** Exactly two, per spec 7.2. */
object UserAgent {
    const val CHROME =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val FACEBOOK = "facebookexternalhit/1.1"
}
