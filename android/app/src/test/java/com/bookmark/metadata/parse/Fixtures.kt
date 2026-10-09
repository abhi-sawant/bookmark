package com.bookmark.metadata.parse

import java.io.File
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Loads the saved `<head>` blocks in `test/resources/fixtures/`.
 *
 * They are real bytes from real sites, trimmed of inline CSS and non-JSON-LD
 * script bodies, so the parser is exercised against the shapes the web actually
 * serves rather than the shape the spec wishes it served -- with no network.
 */
object Fixtures {

    fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name.html")) {
            "missing fixture: $name.html"
        }.use { it.readBytes() }

    /**
     * Parsed the way [com.bookmark.metadata.http.HtmlFetcher] parses: bytes plus
     * an optional header charset, letting Jsoup fall back to `<meta charset>`.
     */
    fun document(name: String, url: String = "https://example.com/", charset: String? = null): Document =
        Jsoup.parse(bytes(name).inputStream(), charset, url)

    /** Every fixture on disk, so a new one is covered the moment it is added. */
    fun names(): List<String> {
        val directory = javaClass.getResource("/fixtures")
            ?: error("fixtures directory not on the test classpath")
        return File(directory.toURI()).list().orEmpty()
            .filter { it.endsWith(".html") }
            .map { it.removeSuffix(".html") }
            .sorted()
    }
}
