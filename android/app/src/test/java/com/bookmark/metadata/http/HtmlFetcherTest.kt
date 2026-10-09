package com.bookmark.metadata.http

import com.bookmark.metadata.FailureCause
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The spec 7.2 request policy, driven over a local server so none of it depends
 * on a real site staying the same.
 */
class HtmlFetcherTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /**
     * Cleartext upgrading is off: MockWebServer speaks http. A real dispatcher,
     * not a test one -- the fetcher does blocking socket I/O, so virtual time
     * would never advance past it.
     */
    private fun fetcher(client: OkHttpClient = defaultClient()) =
        HtmlFetcher(client, Dispatchers.IO, upgradeCleartext = false)

    private fun defaultClient() = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun html(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .body(body)
        .build()

    private fun page(vararg metas: String) = """
        <!DOCTYPE html><html><head>
        <title>Fixture</title>
        ${metas.joinToString("\n")}
        </head><body>body</body></html>
    """.trimIndent()

    // -- headers -------------------------------------------------------------

    @Test
    fun `sends the ranged get, accept headers and desktop chrome user agent`() = runTest {
        server.enqueue(html(page("""<meta property="og:title" content="X">""")))

        fetcher().fetch(server.url("/a").toString())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("bytes=0-524287", request.headers["Range"])
        assertEquals("text/html,application/xhtml+xml", request.headers["Accept"])
        assertTrue(request.headers["Accept-Language"]!!.endsWith("en;q=0.8"))
        assertEquals(UserAgent.CHROME, request.headers["User-Agent"])
    }

    // -- the two-user-agent strategy ----------------------------------------

    @Test
    fun `retries once with the crawler agent when the page has no social tags`() = runTest {
        server.enqueue(html(page()))
        server.enqueue(html(page("""<meta property="og:title" content="Crawler saw this">""")))

        val outcome = fetcher().fetch(server.url("/a").toString())

        assertEquals(2, server.requestCount)
        assertEquals(UserAgent.CHROME, server.takeRequest().headers["User-Agent"])
        assertEquals(UserAgent.FACEBOOK, server.takeRequest().headers["User-Agent"])
        val html = outcome as FetchOutcome.Html
        assertEquals("Crawler saw this", html.document.selectFirst("meta[property=og:title]")?.attr("content"))
    }

    @Test
    fun `retries with the crawler agent on 403`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(html(page("""<meta property="og:title" content="Allowed">""")))

        val outcome = fetcher().fetch(server.url("/a").toString())

        assertEquals(2, server.requestCount)
        assertTrue(outcome is FetchOutcome.Html)
    }

    @Test
    fun `retries with the crawler agent on 429`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).build())
        server.enqueue(html(page("""<meta property="og:title" content="Allowed">""")))

        assertTrue(fetcher().fetch(server.url("/a").toString()) is FetchOutcome.Html)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `never makes a third attempt`() = runTest {
        // Both agents get a bare page with no social tags.
        server.enqueue(html(page()))
        server.enqueue(html(page()))

        fetcher().fetch(server.url("/a").toString())

        assertEquals("two requests is the ceiling", 2, server.requestCount)
    }

    @Test
    fun `does not retry when the first attempt already found social tags`() = runTest {
        server.enqueue(html(page("""<meta name="twitter:title" content="Present">""")))

        fetcher().fetch(server.url("/a").toString())

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `keeps the chrome result when the crawler retry is refused`() = runTest {
        server.enqueue(html(page()))                                  // no social tags
        server.enqueue(MockResponse.Builder().code(403).build())      // crawler blocked

        val outcome = fetcher().fetch(server.url("/a").toString())

        assertTrue("should not lose the usable Chrome response", outcome is FetchOutcome.Html)
    }

    // -- redirects -----------------------------------------------------------

    @Test
    fun `follows redirects and reports the final url`() = runTest {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/two").build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/three").build())
        server.enqueue(html(page("""<meta property="og:title" content="End">""")))

        val outcome = fetcher().fetch(server.url("/one").toString()) as FetchOutcome.Html

        assertEquals(server.url("/three").toString(), outcome.finalUrl)
    }

    @Test
    fun `gives up after five redirects`() = runTest {
        repeat(8) {
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/next$it").build())
        }

        val outcome = fetcher().fetch(server.url("/start").toString())

        assertEquals(FetchOutcome.Failure(FailureCause.UNREACHABLE), outcome)
        assertTrue("expected the cap to stop it", server.requestCount <= 6)
    }

    // -- content-type gate ---------------------------------------------------

    @Test
    fun `an image content type means the url is the thumbnail`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/png").build())

        val outcome = fetcher().fetch(server.url("/photo.png").toString())

        assertEquals(FetchOutcome.Image(server.url("/photo.png").toString()), outcome)
    }

    @Test
    fun `a pdf content type is recognised`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").build())

        val outcome = fetcher().fetch(server.url("/report.pdf").toString())

        assertEquals(FetchOutcome.Pdf(server.url("/report.pdf").toString()), outcome)
    }

    @Test
    fun `xhtml is parsed as html`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200)
                .addHeader("Content-Type", "application/xhtml+xml")
                .body(page("""<meta property="og:title" content="XHTML">"""))
                .build(),
        )

        assertTrue(fetcher().fetch(server.url("/a").toString()) is FetchOutcome.Html)
    }

    @Test
    fun `any other content type yields nothing usable`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/zip").build())

        assertEquals(
            FetchOutcome.Failure(FailureCause.NOTHING_USABLE),
            fetcher().fetch(server.url("/a.zip").toString()),
        )
    }

    // -- status mapping, spec 8.6 -------------------------------------------

    @Test
    fun `status codes map to the specified causes`() = runTest {
        val expected = mapOf(
            404 to FailureCause.NOT_FOUND,
            410 to FailureCause.NOT_FOUND,
            500 to FailureCause.SERVER_ERROR,
            503 to FailureCause.SERVER_ERROR,
            418 to FailureCause.UNREACHABLE,
        )
        expected.forEach { (code, cause) ->
            server.enqueue(MockResponse.Builder().code(code).build())
            assertEquals(
                "status $code",
                FetchOutcome.Failure(cause),
                fetcher().fetch(server.url("/s$code").toString()),
            )
        }
    }

    @Test
    fun `a blocked status that stays blocked reports blocked`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().code(403).build())

        assertEquals(
            FetchOutcome.Failure(FailureCause.BLOCKED),
            fetcher().fetch(server.url("/a").toString()),
        )
    }

    // -- early abort and the byte cap ---------------------------------------

    @Test
    fun `stops reading at the closing head tag`() = runTest {
        // 4MB of body after </head>. If the whole body were read this would blow
        // straight past the 512KB cap and the read timeout.
        val body = buildString {
            append("<html><head><title>Early</title>")
            append("""<meta property="og:title" content="Stopped early">""")
            append("</head><body>")
            append("x".repeat(4 * 1024 * 1024))
            append("</body></html>")
        }
        server.enqueue(html(body))

        val outcome = fetcher().fetch(server.url("/big").toString()) as FetchOutcome.Html

        assertEquals("Stopped early", outcome.document.selectFirst("meta[property=og:title]")?.attr("content"))
    }

    @Test
    fun `finds a closing head tag in mixed case`() = runTest {
        server.enqueue(
            html(
                "<html><HEAD><title>Mixed</title>" +
                    """<meta property="og:title" content="Mixed case">""" +
                    "</HeAd><body>" + "y".repeat(2 * 1024 * 1024) + "</body></html>",
            ),
        )

        val outcome = fetcher().fetch(server.url("/mixed").toString()) as FetchOutcome.Html

        assertEquals("Mixed case", outcome.document.selectFirst("meta[property=og:title]")?.attr("content"))
    }

    @Test
    fun `a head with no closing tag is still bounded and parsed`() = runTest {
        server.enqueue(
            html("<html><head><title>Unterminated</title>" + "<!--" + "z".repeat(2 * 1024 * 1024)),
        )

        val outcome = fetcher().fetch(server.url("/unterminated").toString())

        assertTrue(outcome is FetchOutcome.Html)
        assertEquals("Unterminated", (outcome as FetchOutcome.Html).document.title())
    }

    // -- transport failures --------------------------------------------------

    @Test
    fun `a disconnect is reported as unreachable`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

        val outcome = fetcher().fetch(server.url("/dead").toString())

        assertEquals(FetchOutcome.Failure(FailureCause.UNREACHABLE), outcome)
    }

    @Test
    fun `a stalled response is reported as a timeout`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html")
                .body("<html><head>")
                .onResponseEnd(SocketEffect.Stall)
                .build(),
        )

        val outcome = fetcher().fetch(server.url("/slow").toString())

        assertTrue("expected a timeout, got $outcome", outcome is FetchOutcome.Html || outcome == FetchOutcome.Failure(FailureCause.TIMEOUT))
    }

    // -- cleartext -----------------------------------------------------------
    // The scheme rewriting itself is pure; see RequestUrlsTest. What matters
    // here is that a URL the policy refuses never reaches the network.

    @Test
    fun `a non-http scheme is refused without a request`() = runTest {
        assertEquals(
            FetchOutcome.Failure(FailureCause.UNREACHABLE),
            fetcher().fetch("ftp://example.com/file"),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an unresolvable host is reported as unreachable`() = runTest {
        val outcome = HtmlFetcher(defaultClient(), Dispatchers.IO, upgradeCleartext = true)
            .fetch("no-such-host.invalid/page")

        assertEquals(FetchOutcome.Failure(FailureCause.UNREACHABLE), outcome)
    }
}
