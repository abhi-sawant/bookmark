package com.bookmark.backup

import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.model.Category
import com.bookmark.core.model.MetadataState
import com.bookmark.core.util.UrlNormalizer
import java.util.Locale
import java.util.UUID
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Reads a browser bookmarks export (Chrome, Edge, Firefox and Safari all write
 * the Netscape format) into the same shape as a backup, so the existing preview
 * and merge import handle it. Each non-root folder becomes a category (matched
 * by name against existing ones on import); everything else lands in Unsorted.
 * Non-web links and repeats are dropped. Mirrors web/src/data/bookmarksHtml.ts.
 */
object BookmarksHtmlParser {

    /** Folders browsers put everything under; links directly in one go to Unsorted. */
    private val ROOT_FOLDERS = setOf(
        "bookmarks bar", "bookmarks toolbar", "bookmarks menu", "other bookmarks",
        "mobile bookmarks", "favorites", "favorites bar", "bookmarks",
    )

    /** The category swatches, in order (Color.kt CategorySwatches). */
    private val SWATCHES = listOf(
        "#FB923C", "#38BDF8", "#A78BFA", "#6C8CFF", "#F472B6", "#8F96C4", "#FACC15", "#34D399",
    )

    fun looksLikeBookmarksHtml(head: String): Boolean =
        Regex("netscape-bookmark-file|<dt>\\s*<a\\s", RegexOption.IGNORE_CASE).containsMatchIn(head.take(4096))

    fun parse(
        html: String,
        now: Long = System.currentTimeMillis(),
        newId: () -> String = { UUID.randomUUID().toString() },
    ): ParsedBackup {
        val doc = Jsoup.parse(html)
        val categories = mutableListOf<CategoryBackupDto>()
        val categoryByName = mutableMapOf<String, String>()
        val bookmarks = mutableListOf<BookmarkBackupDto>()
        val seen = mutableSetOf<String>()

        for (a in doc.select("a[href]")) {
            val href = a.attr("href").trim()
            if (!href.startsWith("http://", ignoreCase = true) && !href.startsWith("https://", ignoreCase = true)) continue
            if (!UrlNormalizer.isValid(href)) continue
            val url = UrlNormalizer.normalize(href)
            if (!seen.add(url)) continue

            var categoryId = Category.UNSORTED_ID
            folderOf(a)?.let { folder ->
                categoryId = categoryByName.getOrPut(folder.lowercase(Locale.ROOT)) {
                    val id = newId()
                    categories += CategoryBackupDto(
                        id = id,
                        name = folder,
                        colorHex = SWATCHES[categories.size % SWATCHES.size],
                        sortOrder = categories.size,
                        isDefault = false,
                        createdAt = now,
                    )
                    id
                }
            }

            val addDateSeconds = a.attr("add_date").toLongOrNull()
            val created = if (addDateSeconds != null && addDateSeconds > 0) addDateSeconds * 1000 else now
            bookmarks += BookmarkBackupDto(
                id = newId(),
                url = url,
                originalUrl = href.take(2048),
                title = a.text().trim().ifBlank { UrlNormalizer.host(url) ?: url }.take(BookmarkLimits.TITLE_MAX),
                categoryId = categoryId,
                metadataState = MetadataState.PENDING.name,
                createdAt = minOf(created, now),
                updatedAt = now,
            )
        }

        return ParsedBackup(
            manifest = BackupManifestDto(exportedAt = now, bookmarkCount = bookmarks.size, categoryCount = categories.size),
            bookmarks = bookmarks,
            categories = categories,
            fromBrowser = true,
        )
    }

    private fun headingOf(dl: Element): Element? {
        dl.previousElementSibling()?.takeIf { it.tagName().equals("h3", ignoreCase = true) }?.let { return it }
        val parent = dl.parent() ?: return null
        return if (parent.tagName().equals("dt", ignoreCase = true)) {
            parent.children().firstOrNull { it.tagName().equals("h3", ignoreCase = true) }
        } else {
            null
        }
    }

    /** The nearest enclosing folder's name, or null for a root folder or no folder. */
    private fun folderOf(a: Element): String? {
        var dl: Element? = a.parents().firstOrNull { it.tagName().equals("dl", ignoreCase = true) }
        while (dl != null) {
            val name = headingOf(dl)?.text()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                return if (name.lowercase(Locale.ROOT) in ROOT_FOLDERS) null else name.take(Category.NAME_MAX_LENGTH)
            }
            dl = dl.parents().firstOrNull { it.tagName().equals("dl", ignoreCase = true) }
        }
        return null
    }
}
