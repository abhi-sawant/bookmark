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

    // Slate owns the surfaces and neutrals. Dynamic colour (when the user turns
    // it on) only re-tints the accent roles from the wallpaper, so the look
    // holds while the primary/secondary/tertiary follow the system.
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = dynamicColor && supportsDynamic

    val base = when {
        dark && trueBlack -> TrueBlackColors
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = if (useDynamic) {
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(
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
    } else {
        base
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

    val baseColors = when {
        dark && trueBlack -> TrueBlackBookmarkColors
        dark -> DarkBookmarkColors
        else -> LightBookmarkColors
    }
    // The accent tokens follow the wallpaper too, not just the M3 roles.
    val bookmarkColors = if (useDynamic) {
        baseColors.copy(
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            accentSoft = scheme.primaryContainer,
            onAccentSoft = scheme.onPrimaryContainer,
        )
    } else {
        baseColors
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
