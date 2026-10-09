package com.bookmark.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/** The sync backend, per the harmonized API contract (plan §1.3). */
const val API_BASE_URL = "https://api.bookmark.slowatcoding.com/"

/**
 * Shared wire-format [Json] for the `account`/`sync` packages.
 *
 * The backend's JSON is natural PHP/MySQL snake_case; [JsonNamingStrategy.SnakeCase]
 * maps every Kotlin camelCase property (`categoryId`, `isDefault`, ...) to its
 * wire name (`category_id`, `is_default`, ...) automatically, so the
 * `@Serializable` DTOs stay plain Kotlin with no per-field `@SerialName`.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
    encodeDefaults = true
}
