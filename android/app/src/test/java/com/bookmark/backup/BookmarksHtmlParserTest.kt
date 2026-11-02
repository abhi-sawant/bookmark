package com.bookmark.backup

import com.bookmark.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksHtmlParserTest {
    private val sample = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
            <DL><p>
                <DT><A HREF="https://example.com/top?utm_source=x" ADD_DATE="1700000100">Top level</A>
                <DT><H3 ADD_DATE="1700000200">Recipes</H3>
                <DL><p>
                    <DT><A HREF="https://example.com/pasta" ADD_DATE="1700000300">Pasta</A>
                    <DT><A HREF="https://example.com/pasta/" ADD_DATE="1700000301">Pasta again</A>
                    <DT><H3>Desserts &amp; sweets</H3>
                    <DL><p>
                        <DT><A HREF="https://example.com/cake">Cake</A>
                    </DL><p>
                </DL><p>
                <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
                <DT><A HREF="ftp://example.com/file">FTP</A>
            </DL><p>
            <DT><H3>Other bookmarks</H3>
            <DL><p>
                <DT><A HREF="https://example.org/other">Other</A>
            </DL><p>
        </DL><p>
    """.trimIndent()

    private var n = 0
    private val parsed = BookmarksHtmlParser.parse(sample, now = 2_000_000_000_000L, newId = { "id${++n}" })

    @Test
    fun `recognises the format`() {
        assertTrue(BookmarksHtmlParser.looksLikeBookmarksHtml(sample))
        assertFalse(BookmarksHtmlParser.looksLikeBookmarksHtml("{\"not\": \"html\"}"))
    }

    @Test
    fun `keeps web links only, de-duplicated by normalised url`() {
        assertEquals(
            listOf(
                "https://example.com/cake",
                "https://example.com/pasta",
                "https://example.com/top",
                "https://example.org/other",
            ),
            parsed.bookmarks.map { it.url }.sorted(),
        )
    }

    @Test
    fun `folders become categories and root folders go to Unsorted`() {
        assertEquals(listOf("Desserts & sweets", "Recipes"), parsed.categories.map { it.name }.sorted())
        val byUrl = parsed.bookmarks.associateBy { it.url }
        assertEquals(Category.UNSORTED_ID, byUrl.getValue("https://example.com/top").categoryId)
        assertEquals(Category.UNSORTED_ID, byUrl.getValue("https://example.org/other").categoryId)
        val recipes = parsed.categories.first { it.name == "Recipes" }.id
        val desserts = parsed.categories.first { it.name == "Desserts & sweets" }.id
        assertEquals(recipes, byUrl.getValue("https://example.com/pasta").categoryId)
        assertEquals(desserts, byUrl.getValue("https://example.com/cake").categoryId)
    }

    @Test
    fun `add date becomes the saved date and the link text the title`() {
        val pasta = parsed.bookmarks.first { it.url == "https://example.com/pasta" }
        assertEquals(1_700_000_300_000L, pasta.createdAt)
        assertEquals("Pasta", pasta.title)
        assertTrue(parsed.fromBrowser)
    }
}
