package com.bookmark.metadata.parse

import com.bookmark.core.model.BookmarkLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The synthetic fixtures pin down exact behaviour; the real ones guard against
 * the parser throwing or silently producing nothing on pages as they are
 * actually served.
 */
class MetadataParserTest {

    private fun parse(fixture: String, url: String = "https://example.com/page") =
        MetadataParser.parse(Fixtures.document(fixture, url), url)

    // -- title precedence ----------------------------------------------------

    @Test
    fun `og title wins over twitter and document title`() {
        val result = parse("og_image_secure_url")
        assertEquals("Secure URL wins", result.title)
    }

    @Test
    fun `twitter title is used when og is absent`() {
        val result = parse("twitter_only")
        assertEquals("Title from Twitter card", result.title)
    }

    @Test
    fun `json-ld headline is used when both social tags are absent`() {
        val result = parse("jsonld_graph")
        assertEquals("Headline from the graph", result.title)
    }

    @Test
    fun `document title has its site suffix stripped`() {
        val result = parse("title_suffix", url = "https://acme.example/guide")
        assertEquals("How To Build An App", result.title)
    }

    @Test
    fun `document title suffix matching the host is stripped`() {
        val result = parse("title_suffix_host", url = "https://example.com/a")
        assertEquals("Some Article", result.title)
    }

    @Test
    fun `a page with no metadata at all yields a null title`() {
        val result = parse("no_head_tags")
        assertNull(result.title)
        assertNull(result.description)
        assertTrue(result.imageCandidates.isEmpty())
    }

    // -- description ---------------------------------------------------------

    @Test
    fun `json-ld description is used when meta tags are absent`() {
        assertEquals("Description from the graph node.", parse("jsonld_graph").description)
    }

    @Test
    fun `meta description is used when og and twitter are absent`() {
        assertEquals("A guide.", parse("title_suffix").description)
    }

    // -- images --------------------------------------------------------------

    @Test
    fun `secure url is preferred over the plain og image`() {
        val result = parse("og_image_secure_url")
        assertEquals("https://example.com/secure.jpg", result.imageCandidates.first())
    }

    @Test
    fun `image candidates are capped at five`() {
        val result = parse("multi_og_image")
        assertEquals(MetadataParser.MAX_IMAGE_CANDIDATES, result.imageCandidates.size)
        assertEquals("https://example.com/1.jpg", result.imageCandidates.first())
        assertEquals("https://example.com/5.jpg", result.imageCandidates.last())
    }

    @Test
    fun `relative images resolve against base href, not the page url`() {
        val result = parse("base_href_relative", url = "https://www.example.com/deep/page.html")
        assertEquals("https://cdn.example.org/site/images/hero.png", result.imageCandidates.first())
    }

    @Test
    fun `apple touch icons fall back largest first`() {
        val result = parse("apple_touch_icon_only")
        assertEquals(
            listOf(
                "https://example.com/icon-180.png",
                "https://example.com/icon-120.png",
                "https://example.com/icon-76.png",
                "https://example.com/favicon-32.png",
            ),
            result.imageCandidates,
        )
    }

    @Test
    fun `json-ld image arrays contribute candidates`() {
        val result = parse("jsonld_graph")
        assertEquals(
            listOf("https://example.com/g1.jpg", "https://example.com/g2.jpg"),
            result.imageCandidates,
        )
    }

    // -- site name -----------------------------------------------------------

    @Test
    fun `json-ld publisher supplies the site name`() {
        assertEquals("Graph Publishing", parse("jsonld_graph").siteName)
    }

    @Test
    fun `site name falls back through the known-host map`() {
        // Not the bare "ogp.me": a fetch must not downgrade the site name that
        // BookmarkRepository.save already derived from the bundled host map.
        val url = "https://ogp.me/"
        assertEquals(
            "Open Graph Protocol",
            MetadataParser.parse(Fixtures.document("no_head_tags", url), url).siteName,
        )
    }

    @Test
    fun `site name falls back to the title-cased domain for an unknown host`() {
        assertEquals("Example", parse("no_head_tags", "https://www.example.com/x").siteName)
    }

    // -- robustness ----------------------------------------------------------

    @Test
    fun `malformed json-ld blocks are skipped rather than throwing`() {
        val result = parse("malformed_jsonld")
        assertEquals("The good one", result.title)
        assertEquals(listOf("https://example.com/good.jpg"), result.imageCandidates)
    }

    @Test
    fun `entities are decoded and whitespace collapsed`() {
        val result = parse("entities_and_whitespace")
        assertEquals("Tom & Jerry’s “Big” Adventure", result.title)
        assertEquals(
            "Line one. Line two — with runs of space and a nbsp.",
            result.description,
        )
    }

    @Test
    fun `a declared iso-8859-1 charset is honoured`() {
        val document = Fixtures.document(
            "charset_iso8859",
            url = "https://example.com/",
            charset = "ISO-8859-1",
        )
        val result = MetadataParser.parse(document, "https://example.com/")
        assertEquals("Café crème à la mode", result.title)
    }

    @Test
    fun `charset is detected from the meta tag when the header omits it`() {
        // charset = null is what HtmlFetcher passes when Content-Type carries none.
        val document = Fixtures.document("charset_iso8859", url = "https://example.com/", charset = null)
        val result = MetadataParser.parse(document, "https://example.com/")
        assertEquals("Café crème à la mode", result.title)
    }

    @Test
    fun `over-long fields are capped`() {
        val result = parse("overlong_fields")
        assertTrue(result.title!!.length <= BookmarkLimits.TITLE_MAX)
        assertTrue(result.description!!.length <= BookmarkLimits.DESCRIPTION_MAX)
        assertTrue(result.siteName!!.length <= BookmarkLimits.SITE_NAME_MAX)
    }

    @Test
    fun `favicons are deliberately not extracted`() {
        assertNull(parse("base_href_relative").faviconUrl)
    }

    // -- suffix stripping against the real corpus ----------------------------

    @Test
    fun `a known-host display name is stripped as a suffix`() {
        val url = "https://news.ycombinator.com/item?id=1"
        val result = MetadataParser.parse(Fixtures.document("hn_item", url), url)
        assertEquals("Y Combinator", result.title)
    }

    @Test
    fun `a suffix matching the domain stem is stripped`() {
        val url = "https://en.wikipedia.org/wiki/Open_Graph_protocol"
        val result = MetadataParser.parse(Fixtures.document("wikipedia_article", url), url)
        assertEquals("Open Graph protocol", result.title)
    }

    @Test
    fun `a suffix that abbreviates og site name is stripped`() {
        // og:site_name is "MDN Web Docs"; the title ends "| MDN".
        val url = "https://developer.mozilla.org/en-US/docs/Web/HTML"
        val result = MetadataParser.parse(Fixtures.document("mdn_doc", url), url)
        assertEquals("HTML: HyperText Markup Language", result.title)
    }

    @Test
    fun `a real title whose tail is not the site survives`() {
        val url = "https://kotlinlang.org/"
        val result = MetadataParser.parse(Fixtures.document("kotlinlang", url), url)
        assertEquals("Kotlin Programming Language", result.title)
    }

    // -- the real corpus -----------------------------------------------------

    @Test
    fun `every fixture parses without throwing and stays within the caps`() {
        val names = Fixtures.names()
        assertTrue("expected a substantial corpus, found ${names.size}", names.size >= 30)

        names.forEach { name ->
            val url = "https://example.com/$name"
            val result = MetadataParser.parse(Fixtures.document(name, url), url)

            assertTrue("$name: too many images", result.imageCandidates.size <= MetadataParser.MAX_IMAGE_CANDIDATES)
            result.title?.let { assertTrue("$name: title over cap", it.length <= BookmarkLimits.TITLE_MAX) }
            result.description?.let { assertTrue("$name: description over cap", it.length <= BookmarkLimits.DESCRIPTION_MAX) }
            result.siteName?.let { assertTrue("$name: siteName over cap", it.length <= BookmarkLimits.SITE_NAME_MAX) }
            result.imageCandidates.forEach { candidate ->
                assertTrue(
                    "$name: unresolved image candidate '$candidate'",
                    candidate.startsWith("http://") || candidate.startsWith("https://"),
                )
            }
            assertTrue("$name: blank title", result.title?.isNotBlank() ?: true)
        }
    }

    @Test
    fun `the real fixtures that advertise open graph yield a title`() {
        // Anything with og:title must produce one; a regression here means the
        // precedence chain broke, not that a site changed.
        val ogPages = listOf(
            "ogp_me", "github_repo", "vercel", "apple", "kotlinlang", "jsoup",
            "notion", "figma", "substack", "theverge_article", "wikipedia_article",
            "youtube_watch", "soundcloud", "smashingmag", "css_tricks", "bbc_news",
        )
        ogPages.forEach { name ->
            val result = MetadataParser.parse(Fixtures.document(name), "https://example.com/")
            assertNotNull("$name: expected a title", result.title)
            assertTrue("$name: expected a title", result.title!!.isNotBlank())
        }
    }
}
