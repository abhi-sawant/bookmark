package com.bookmark.search

/**
 * Terms to highlight, derived the same way [com.bookmark.bookmarks.data.BookmarkRepository.search]
 * derives the terms it feeds into the FTS `MATCH` expression -- split on
 * whitespace, quotes stripped -- minus the trailing `*` it appends for FTS4's
 * own prefix syntax, which is not a display concern. Kept in one place so
 * highlighting and the actual search stay in sync; if the split logic ever
 * changes it has to change in both places, since the DAO doesn't hand back
 * match offsets for this query.
 */
fun queryTermsFor(query: String): List<String> =
    query.trim().split(Regex("\\s+"))
        .map { it.replace("\"", "") }
        .filter { it.isNotBlank() }

/**
 * Every disjoint word-range in [text] that starts with one of [terms],
 * case-insensitively -- mirrors the FTS4 `term*` prefix match, so a result
 * highlights exactly the substring that made it match. Word-bounded so a term
 * only lights up whole tokens ("html" highlights "HTML," but not the "html"
 * inside "mathHTMLish").
 */
fun findHighlightRanges(text: String, terms: List<String>): List<IntRange> {
    if (terms.isEmpty()) return emptyList()
    val lowerTerms = terms.map { it.lowercase() }.filter { it.isNotBlank() }
    if (lowerTerms.isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    for (match in Regex("""\w+""").findAll(text)) {
        val word = match.value.lowercase()
        if (lowerTerms.any { term -> word.startsWith(term) }) {
            ranges += match.range
        }
    }
    return ranges
}
