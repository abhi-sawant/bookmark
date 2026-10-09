package com.bookmark.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes. */
@Serializable
data object HomeRoute

@Serializable
data object CategoriesRoute

@Serializable
data object SettingsRoute

@Serializable
data object SearchRoute

/**
 * A single destination for the whole sign-in flow (Login/SignUp/ForgotPassword)
 * -- which of the three shows is in-screen state (see `BookmarkNavHost`), not a
 * separate route each, since none of them needs its own back-stack entry.
 */
@Serializable
data object AccountRoute

/** The three bottom-bar destinations, in the order the design lays them out. */
enum class TopLevelDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    CATEGORIES("Categories", Icons.Outlined.ViewList),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
