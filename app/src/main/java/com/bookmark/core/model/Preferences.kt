package com.bookmark.core.model

import androidx.compose.runtime.Immutable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { GRID, LIST }

enum class SortOrder {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    CATEGORY;

    /** The label shown in the mono affordance beside the filter row. */
    val shortLabel: String
        get() = when (this) {
            NEWEST -> "NEWEST"
            OLDEST -> "OLDEST"
            TITLE_AZ -> "A–Z"
            CATEGORY -> "CATEGORY"
        }
}

@Immutable
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val trueBlack: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val lastUsedCategoryId: String? = null,
    val fetchPreviewsAutomatically: Boolean = true,
)
