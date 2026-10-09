package com.bookmark.metadata.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

/**
 * JSON-LD extraction (spec 7.3), flattened to the four fields the parser wants.
 *
 * Real-world JSON-LD is frequently malformed -- truncated by a template engine,
 * doubled up, or simply not JSON -- so every block is parsed independently and a
 * failure discards only that block. Nothing here may throw: a broken script tag
 * on a page must never cost the user their preview.
 */
object JsonLd {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    data class Fields(
        val headline: String? = null,
        val description: String? = null,
        val images: List<String> = emptyList(),
        val publisherName: String? = null,
    )

    /**
     * Types that describe the *site* rather than the page. Their `name` is the
     * publication, not the headline -- a WebSite node emitted before the Article
     * node must not be allowed to supply the title.
     */
    private val SITE_LEVEL_TYPES = setOf(
        "website", "organization", "breadcrumblist", "sitenavigationelement",
        "imageobject", "person", "wpheader", "wpfooter", "listitem",
    )

    fun extract(document: Document): Fields {
        val nodes = document.select("script[type=application/ld+json]")
            .flatMap { element -> parseBlock(element.data()) }

        val (siteNodes, pageNodes) = nodes.partition { it.isSiteLevel() }

        return Fields(
            // `headline` is unambiguous, so it is exhausted across every node
            // before `name` is considered at all -- and `name` is only read from
            // page-level nodes.
            headline = nodes.firstNotNullOfOrNull { it.string("headline") }
                ?: pageNodes.firstNotNullOfOrNull { it.string("name") },
            description = pageNodes.firstNotNullOfOrNull { it.string("description") }
                ?: nodes.firstNotNullOfOrNull { it.string("description") },
            images = nodes.flatMap { it.images() }.distinct(),
            publisherName = nodes.firstNotNullOfOrNull { node ->
                (node["publisher"] as? JsonObject)?.string("name")
            } ?: siteNodes.firstNotNullOfOrNull { it.string("name") },
        )
    }

    /** `@type` is a string or an array of them; either may name a site-level type. */
    private fun JsonObject.isSiteLevel(): Boolean {
        val type = this["@type"] ?: return false
        val names = when (type) {
            is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(type.content)
            else -> emptyList()
        }
        return names.any { it.lowercase() in SITE_LEVEL_TYPES }
    }

    /** One `<script>` body to zero or more objects, expanding arrays and `@graph`. */
    private fun parseBlock(raw: String): List<JsonObject> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        return flatten(root)
    }

    private fun flatten(element: kotlinx.serialization.json.JsonElement): List<JsonObject> =
        when (element) {
            is JsonArray -> element.flatMap { flatten(it) }
            is JsonObject -> {
                val graph = element["@graph"]
                if (graph != null) listOf(element) + flatten(graph) else listOf(element)
            }
            else -> emptyList()
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifBlank { null }

    /**
     * `image` is specified as a string, an array, or an ImageObject -- and in
     * practice as any nesting of the three.
     */
    private fun JsonObject.images(): List<String> = when (val image = this["image"]) {
        null -> emptyList()
        else -> imageUrls(image)
    }

    private fun imageUrls(element: kotlinx.serialization.json.JsonElement): List<String> =
        when (element) {
            is JsonArray -> element.flatMap { imageUrls(it) }
            is JsonObject -> listOfNotNull(element.string("url") ?: element.string("contentUrl"))
            is JsonPrimitive -> if (element.isString) {
                listOfNotNull(element.jsonPrimitive.content.trim().ifBlank { null })
            } else {
                emptyList()
            }
        }
}
