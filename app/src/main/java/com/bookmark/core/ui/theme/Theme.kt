package com.bookmark.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bookmark.core.model.ThemeMode

@Composable
fun BookmarkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    trueBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    // Dynamic colour is on by default (spec 10); the hand-picked palette from the
    // design is the fallback, and the only thing shown below Android 12.
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && trueBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
        )
    }

    // The app theme can disagree with the system one (the user picks Light/Dark
    // in Settings), and the status/navigation bar icons follow the window, not
    // the Compose theme -- without this they are unreadable in that case.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalBookmarkColors provides if (dark) DarkBookmarkColors else LightBookmarkColors,
        LocalBookmarkTextStyles provides DesignTextStyles,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BookmarkTypography,
            shapes = Material3Shapes,
            content = content,
        )
    }
}

/** Shorthand for the non-Material design tokens. */
object BookmarkTheme {
    val colors: BookmarkColors
        @Composable get() = LocalBookmarkColors.current

    val text: BookmarkTextStyles
        @Composable get() = LocalBookmarkTextStyles.current
}
