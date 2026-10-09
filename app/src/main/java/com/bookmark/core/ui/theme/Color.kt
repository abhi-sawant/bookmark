package com.bookmark.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * "Slate": calm cool neutrals with a single green. Surfaces are flat and solid,
 * separated by hairlines rather than blur or shadow; the one accent only appears
 * where something is on, selected-as-action or primary. Light is the cool-grey
 * paper, dark is near-black with a faint green cast.
 *
 * Material roles carry text, the accent and the opaque surfaces sheets and
 * dialogs sit on. The few tokens with no M3 role live in [BookmarkColors].
 */

private val SlateGreenLight = Color(0xFF0B7A54)
private val SlateGreenDark = Color(0xFF5EE0A9)

val LightColors: ColorScheme = lightColorScheme(
    primary = SlateGreenLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EDE9),
    onPrimaryContainer = Color(0xFF053D2A),
    inversePrimary = SlateGreenDark,

    secondary = Color(0xFF4A5157),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E5E8),
    onSecondaryContainer = Color(0xFF0F1214),

    tertiary = Color(0xFF4A5157),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E5E8),
    onTertiaryContainer = Color(0xFF0F1214),

    error = Color(0xFFC4352F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4E2),
    onErrorContainer = Color(0xFF5A100D),

    background = Color(0xFFF2F3F4),
    onBackground = Color(0xFF0F1214),
    surface = Color(0xFFF2F3F4),
    onSurface = Color(0xFF0F1214),
    surfaceVariant = Color(0xFFE3E5E8),
    onSurfaceVariant = Color(0xFF6A7076),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE9EBED),

    outline = Color(0xFFC9CDD1),
    outlineVariant = Color(0xFFE3E5E8),

    inverseSurface = Color(0xFF0F1214),
    inverseOnSurface = Color(0xFFECEFF0),
    scrim = Color(0xFF000000),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = SlateGreenDark,
    onPrimary = Color(0xFF06231A),
    primaryContainer = Color(0xFF12231C),
    onPrimaryContainer = Color(0xFFBFF3DC),
    inversePrimary = SlateGreenLight,

    secondary = Color(0xFFB4BBC0),
    onSecondary = Color(0xFF111416),
    secondaryContainer = Color(0xFF222729),
    onSecondaryContainer = Color(0xFFECEFF0),

    tertiary = Color(0xFFB4BBC0),
    onTertiary = Color(0xFF111416),
    tertiaryContainer = Color(0xFF222729),
    onTertiaryContainer = Color(0xFFECEFF0),

    error = Color(0xFFFF8A84),
    onError = Color(0xFF4A0907),
    errorContainer = Color(0xFF3A1210),
    onErrorContainer = Color(0xFFFFD9D6),

    background = Color(0xFF0B0D0E),
    onBackground = Color(0xFFECEFF0),
    surface = Color(0xFF0B0D0E),
    onSurface = Color(0xFFECEFF0),
    surfaceVariant = Color(0xFF222729),
    onSurfaceVariant = Color(0xFF8B9298),

    surfaceContainerLowest = Color(0xFF080A0B),
    surfaceContainerLow = Color(0xFF141718),
    surfaceContainer = Color(0xFF141718),
    surfaceContainerHigh = Color(0xFF181C1D),
    surfaceContainerHighest = Color(0xFF222729),

    outline = Color(0xFF3A4144),
    outlineVariant = Color(0xFF222729),

    inverseSurface = Color(0xFFECEFF0),
    inverseOnSurface = Color(0xFF111416),
    scrim = Color(0xFF000000),
)

/** OLED variant: the surfaces collapse to true black, everything else holds. */
val TrueBlackColors: ColorScheme = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0E0F),
    surfaceContainer = Color(0xFF0C0E0F),
    surfaceContainerHigh = Color(0xFF121516),
    surfaceContainerHighest = Color(0xFF1B1F20),
)

/**
 * Design tokens that have no Material 3 role. Reached through [LocalBookmarkColors]
 * so they track the active theme the same way the M3 scheme does.
 */
@Immutable
data class BookmarkColors(
    /** Fill of cards, rows, fields and grouped settings: the raised surface. */
    val cardSurface: Color,
    /** Fill of things that float over content: the bottom bar, menus. */
    val raisedSurface: Color,
    /** The 1dp line that separates surfaces. */
    val hairline: Color,
    /** Pressed / hovered / highlighted neutral. */
    val hover: Color,
    /** The one accent: switches, FAB, primary buttons, links. */
    val accent: Color,
    val onAccent: Color,
    /** A quiet wash of the accent: icon chips, the active bottom tab. */
    val accentSoft: Color,
    /** Text and glyphs that sit on [accentSoft]. */
    val onAccentSoft: Color,
    /** Fill of the selected chip / segment: the ink itself. */
    val selectedFill: Color,
    val onSelectedFill: Color,
    /** Background of the "Preview pending" pill. */
    val pendingPillContainer: Color,
    val onPendingPillContainer: Color,
    /** Section headers, counters, timings. */
    val mutedLabel: Color,
    /** Detail-sheet hard-failure card. */
    val failureContainer: Color,
    val onFailureContainer: Color,
    val onFailureContainerVariant: Color,
    /** Fill behind the selected option card in the delete-category dialog. */
    val selectedOptionContainer: Color,
    /** Skeleton bars and the shimmering thumbnail in the quick-save sheet. */
    val skeleton: Color,
    val skeletonHighlight: Color,
)

val LightBookmarkColors = BookmarkColors(
    cardSurface = Color(0xFFFFFFFF),
    raisedSurface = Color(0xFFFFFFFF),
    hairline = Color(0xFFE3E5E8),
    hover = Color(0xFFE9EBED),
    accent = SlateGreenLight,
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE4EDE9),
    onAccentSoft = Color(0xFF0B7A54),
    selectedFill = Color(0xFF0F1214),
    onSelectedFill = Color(0xFFF2F3F4),
    pendingPillContainer = Color(0xFFE3E5E8),
    onPendingPillContainer = Color(0xFF4A5157),
    mutedLabel = Color(0xFF6A7076),
    failureContainer = Color(0xFFFBE4E2),
    onFailureContainer = Color(0xFF5A100D),
    onFailureContainerVariant = Color(0xFF8A3B37),
    selectedOptionContainer = Color(0xFFE4EDE9),
    skeleton = Color(0xFFE3E5E8),
    skeletonHighlight = Color(0xFFF2F3F4),
)

val DarkBookmarkColors = BookmarkColors(
    cardSurface = Color(0xFF141718),
    raisedSurface = Color(0xFF141718),
    hairline = Color(0xFF222729),
    hover = Color(0xFF1D2123),
    accent = SlateGreenDark,
    onAccent = Color(0xFF06231A),
    accentSoft = Color(0xFF12231C),
    onAccentSoft = Color(0xFF5EE0A9),
    selectedFill = Color(0xFFECEFF0),
    onSelectedFill = Color(0xFF0B0D0E),
    pendingPillContainer = Color(0xFF222729),
    onPendingPillContainer = Color(0xFFB4BBC0),
    mutedLabel = Color(0xFF8B9298),
    failureContainer = Color(0xFF3A1210),
    onFailureContainer = Color(0xFFFFD9D6),
    onFailureContainerVariant = Color(0xFFE5A9A5),
    selectedOptionContainer = Color(0xFF12231C),
    skeleton = Color(0xFF1D2123),
    skeletonHighlight = Color(0xFF2A3033),
)

/** True-black counterpart to [DarkBookmarkColors]: same palette on a black ground. */
val TrueBlackBookmarkColors = DarkBookmarkColors.copy(
    cardSurface = Color(0xFF0C0E0F),
    raisedSurface = Color(0xFF0C0E0F),
    hairline = Color(0xFF1B1F20),
)

val LocalBookmarkColors = staticCompositionLocalOf { DarkBookmarkColors }

/**
 * The eight category swatches offered in the create/edit dialog. Tuned bright
 * enough to read as a dot on the dark surface; also the source palette for
 * generated monogram tiles (spec 8.3) -- a domain always hashes to the same
 * entry, which is what makes a grid of fallbacks scannable.
 */
val CategorySwatches: List<Color> = listOf(
    Color(0xFFFB923C), // Reading
    Color(0xFF38BDF8), // Dev
    Color(0xFFA78BFA), // Design
    Color(0xFF6C8CFF), // Watch later
    Color(0xFFF472B6), // Recipes
    Color(0xFF8F96C4), // neutral / Unsorted
    Color(0xFFFACC15),
    Color(0xFF34D399),
)

/** Hex strings for the same swatches, for persistence in `categories.colorHex`. */
val CategorySwatchHex: List<String> = listOf(
    "#FB923C", "#38BDF8", "#A78BFA", "#6C8CFF",
    "#F472B6", "#8F96C4", "#FACC15", "#34D399",
)

/** The pre-Slate swatches. Already-saved categories carry these hex values. */
private val LegacySwatchHex: List<String> = listOf(
    "#B0552F", "#0F7A6B", "#7A4FD6", "#2A5FD6",
    "#C0392B", "#7D918D", "#7D5416", "#00504A",
)

/** Maps a pre-Aurora swatch hex onto its current counterpart; anything else passes through. */
fun normalizeCategoryHex(hex: String?): String? {
    if (hex == null) return null
    val i = LegacySwatchHex.indexOfFirst { it.equals(hex, ignoreCase = true) }
    return if (i >= 0) CategorySwatchHex[i] else hex
}

/** Colour a persisted `colorHex` back into a [Color], falling back to the neutral swatch. */
fun parseCategoryColor(hex: String?): Color {
    val normalized = normalizeCategoryHex(hex)
    if (normalized.isNullOrBlank()) return CategorySwatches[5]
    val cleaned = normalized.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return CategorySwatches[5]
    return when (cleaned.length) {
        6 -> Color(value or 0xFF000000L)
        8 -> Color(value)
        else -> CategorySwatches[5]
    }
}
