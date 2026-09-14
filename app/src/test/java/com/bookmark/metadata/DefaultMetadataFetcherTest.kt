package com.bookmark.metadata

import com.bookmark.metadata.http.HtmlFetcher
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fetch, parse and classify end to end, over a local server.
 *
 * The classification is the contract the worker depends on to pick a
 * [com.bookmark.core.model.MetadataState], so the spec 8.1 distinctions -- and
 * especially the rule that PARTIAL and FALLBACK are not errors -- are pinned
 * here rather than left implicit.
 */
class DefaultMetadataFetcherTest {

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

    private fun fetcher() = DefaultMetadataFetcher(
        HtmlFetcher(
            OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .followRedirects(false)
                .build(),
            Dispatchers.IO,
            upgradeCleartext = false,
        ),
    )

    private fun html(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .body(body)
        .build()

    // -- classification ------------------------------------------------------

    @Test
    fun `title and image together are a success`() = runTest {
        server.enqueue(
            html(
                """<html><head>
                   <meta property="og:title" content="Complete">
                   <meta property="og:image" content="https://cdn.example/hero.jpg">
                   </head></html>""",
            ),
        )

        val result = fetcher().fetch(server.url("/a").toString())

        val success = result as MetadataResult.Success
        assertEquals("Complete", success.metadata.title)
        assertEquals(listOf("https://cdn.example/hero.jpg"), success.metadata.imageCandidates)
    }

    @Test
    fun `a title with no image is partial, not an error`() = runTest {
        server.enqueue(html("""<html><head><meta property="og:title" content="Text only"></head></html>"""))

        val result = fetcher().fetch(server.url("/a").toString())

        assertTrue("expected PARTIAL, got $result", result is MetadataResult.Partial)
        // Spec 8.6: "image download failed only" is silent -- no cause to show.
        assertEquals(null, (result as MetadataResult.Partial).cause)
    }

    @Test
    fun `a reachable page with nothing usable is a silent fallback`() = runTest {
        // No title, no meta, no images -- and both UA attempts see the same.
        server.enqueue(html("<html><head></head><body>hi</body></html>"))
        server.enqueue(html("<html><head></head><body>hi</body></html>"))

        val result = fetcher().fetch(server.url("/a").toString())

        assertEquals(MetadataResult.Fallback(FailureCause.NOTHING_USABLE), result)
        assertEquals(null, FailureCause.NOTHING_USABLE.userMessage())
    }

    // -- spec 8.6, the failure taxonomy --------------------------------------

    @Test
    fun `a blocking site is a fallback, not a failure`() = runTest {
        // X, Instagram and paywalled news land here. Expected, not a bug (spec 7.5).
        server.enqueue(MockResponse.Builder().code(403).build())
        server.enqueue(MockResponse.Builder().code(403).build())

        val result = fetcher().fetch(server.url("/a").toString())

        assertEquals(MetadataResult.Fallback(FailureCause.BLOCKED), result)
        assertEquals("This site doesn't share previews", FailureCause.BLOCKED.userMessage())
    }

    @Test
    fun `a missing page is a hard failure`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        assertEquals(
            MetadataResult.Failed(FailureCause.NOT_FOUND),
            fetcher().fetch(server.url("/gone").toString()),
        )
    }

    @Test
    fun `a server error is a hard failure`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        assertEquals(
            MetadataResult.Failed(FailureCause.SERVER_ERROR),
            fetcher().fetch(server.url("/oops").toString()),
        )
    }

    @Test
    fun `every result maps to the state the spec 8_1 diagram names`() {
        assertEquals(
            com.bookmark.core.model.MetadataState.SUCCESS,
            MetadataResult.Success(PageMetadata()).state,
        )
        assertEquals(
            com.bookmark.core.model.MetadataState.PARTIAL,
            MetadataResult.Partial(PageMetadata(), null).state,
        )
        assertEquals(
            com.bookmark.core.model.MetadataState.FALLBACK,
            MetadataResult.Fallback(FailureCause.NOTHING_USABLE).state,
        )
        assertEquals(
            com.bookmark.core.model.MetadataState.FAILED,
            MetadataResult.Failed(FailureCause.TIMEOUT).state,
        )
        assertEquals(com.bookmark.core.model.MetadataState.PENDING, MetadataResult.Pending.state)
    }

    @Test
    fun `only hard failures carry a user-visible message`() {
        // PARTIAL and FALLBACK look complete to the user (spec 8.1), so their
        // causes must stay silent.
        assertEquals(null, FailureCause.NOTHING_USABLE.userMessage())
        assertEquals(null, FailureCause.IMAGE_ONLY.userMessage())
        assertEquals("Couldn't reach this site", FailureCause.UNREACHABLE.userMessage())
        assertEquals("This site took too long to respond", FailureCause.TIMEOUT.userMessage())
        assertEquals("This page wasn't found", FailureCause.NOT_FOUND.userMessage())
        assertEquals("This site is having problems", FailureCause.SERVER_ERROR.userMessage())
        assertEquals("Preview pending", FailureCause.OFFLINE.userMessage())
    }

    // -- spec 7.5 special cases ---------------------------------------------

    @Test
    fun `a direct image url is its own thumbnail`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "image/jpeg").build())

        val url = server.url("/photos/my-holiday-snap.jpg").toString()
        val result = fetcher().fetch(url) as MetadataResult.Success

        assertEquals("my holiday snap", result.metadata.title)
        assertEquals(listOf(url), result.metadata.imageCandidates)
    }

    @Test
    fun `a pdf takes its title from the filename and has no image`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").build())

        val result = fetcher().fetch(server.url("/reports/Q3-summary.pdf").toString())

        val partial = result as MetadataResult.Partial
        assertEquals("Q3 summary", partial.metadata.title)
        assertTrue(partial.metadata.imageCandidates.isEmpty())
    }

    @Test
    fun `a youtube url gets ytimg thumbnails ahead of whatever the page says`() = runTest {
        server.enqueue(
            html(
                """<html><head>
                   <meta property="og:title" content="Never Gonna Give You Up">
                   <meta property="og:image" content="https://cdn.example/page-supplied.jpg">
                   </head></html>""",
            ),
        )
        // Resolve youtube.com to the local mock server, so the id extraction and
        // the injection are both real while the response is ours.
        val result = fetcherResolvingEverythingLocally()
            .fetch("http://www.youtube.com:${server.port}/watch?v=$VIDEO_ID")

        val success = result as MetadataResult.Success
        assertEquals("Never Gonna Give You Up", success.metadata.title)
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/$VIDEO_ID/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$VIDEO_ID/hqdefault.jpg",
                "https://cdn.example/page-supplied.jpg",
            ),
            success.metadata.imageCandidates,
        )
    }

    @Test
    fun `an unreachable youtube page still yields a thumbnail`() = runTest {
        // The watch page is gone, but the ytimg URLs derive from the id alone,
        // so the preview survives (spec 7.5).
        val result = DefaultMetadataFetcher(
            HtmlFetcher(
                OkHttpClient.Builder()
                    .callTimeout(2, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .dns { hostname -> throw UnknownHostException(hostname) }
                    .build(),
                Dispatchers.IO,
                upgradeCleartext = true,
            ),
        ).fetch("https://www.youtube.com/watch?v=$VIDEO_ID")

        val partial = result as MetadataResult.Partial
        assertEquals(FailureCause.UNREACHABLE, partial.cause)
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/$VIDEO_ID/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$VIDEO_ID/hqdefault.jpg",
            ),
            partial.metadata.imageCandidates,
        )
    }

    @Test
    fun `a lookalike youtube host gets no special treatment`() = runTest {
        val result = DefaultMetadataFetcher(
            HtmlFetcher(
                OkHttpClient.Builder()
                    .callTimeout(2, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .dns { hostname -> throw UnknownHostException(hostname) }
                    .build(),
                Dispatchers.IO,
                upgradeCleartext = true,
            ),
        ).fetch("https://www.youtube.com.evil.example/watch?v=$VIDEO_ID")

        assertEquals(MetadataResult.Failed(FailureCause.UNREACHABLE), result)
    }

    /** Sends every hostname to the mock server, so any host can be simulated. */
    private fun fetcherResolvingEverythingLocally() = DefaultMetadataFetcher(
        HtmlFetcher(
            OkHttpClient.Builder()
                .callTimeout(2, TimeUnit.SECONDS)
                .followRedirects(false)
                .dns { InetAddress.getAllByName(server.hostName).toList() }
                .build(),
            Dispatchers.IO,
            upgradeCleartext = false,
        ),
    )

    private companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"
    }
}
