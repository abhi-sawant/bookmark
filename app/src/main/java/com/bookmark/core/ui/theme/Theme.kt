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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bookmark.core.model.ThemeMode

@Composable
fun BookmarkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
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
    val useDynamic = dynamicColor && supportsDynamic

    // True black wins over dynamic colour: OLED power-saving is a deliberate,
    // strong preference, so the surface family always collapses to pure black
    // when both are on. Dynamic's wallpaper-derived accent roles (primary/
    // secondary/tertiary and their containers) are kept on top of that, rather
    // than losing them entirely to the hand-picked TrueBlackColors accents.
    val scheme = when {
        dark && trueBlack && useDynamic -> {
            val dynamic = dynamicDarkColorScheme(context)
            TrueBlackColors.copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                primaryContainer = dynamic.primaryContainer,
                onPrimaryContainer = dynamic.onPrimaryContainer,
                inversePrimary = dynamic.inversePrimary,
                secondary = dynamic.secondary,
                onSecondary = dynamic.onSecondary,
                secondaryContainer = dynamic.secondaryContainer,
                onSecondaryContainer = dynamic.onSecondaryContainer,
                tertiary = dynamic.tertiary,
                onTertiary = dynamic.onTertiary,
                tertiaryContainer = dynamic.tertiaryContainer,
                onTertiaryContainer = dynamic.onTertiaryContainer,
            )
        }
        dark && trueBlack -> TrueBlackColors
        dark && useDynamic -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
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

    val bookmarkColors = when {
        dark && trueBlack -> TrueBlackBookmarkColors
        dark -> DarkBookmarkColors
        else -> LightBookmarkColors
    }

    CompositionLocalProvider(
        LocalBookmarkColors provides bookmarkColors,
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
