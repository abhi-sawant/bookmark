package com.bookmark.core.util

import java.util.Locale

/**
 * Tag rules shared by every path that accepts tags (edit sheet, import, sync).
 * Mirrored by web/src/core/util/tags.ts -- keep the two identical, both apps
 * push to the same account.
 */
object Tags {
    const val MAX_COUNT = 10
    const val MAX_LENGTH = 30

    /** Room in an input field for [MAX_COUNT] tags of [MAX_LENGTH] characters plus separators. */
    const val INPUT_MAX = 400

    /** Trimmed, `#`-less, lower-case, single-spaced, de-duplicated and capped, in input order. */
    fun normalize(raw: Iterable<String>): List<String> =
        raw.asSequence()
            .map { it.trim().trimStart('#').replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).take(MAX_LENGTH).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_COUNT)
            .toList()

    /** Splits free text on commas and new lines, then [normalize]s it. */
    fun parse(input: String): List<String> = normalize(input.split(',', '\n'))

    /** Storage form: newline-joined, null when there are none (a tag never contains a newline). */
    fun encode(tags: List<String>): String? = tags.joinToString("\n").ifBlank { null }

    fun decode(column: String?): List<String> =
        column?.lineSequence()?.filter(String::isNotBlank)?.toList().orEmpty()
}
