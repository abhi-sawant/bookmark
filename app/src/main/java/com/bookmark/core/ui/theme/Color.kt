package com.bookmark.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palette taken verbatim from the "2a" screen set in the Claude Design project.
 * Where the design names a colour, the design wins over the Material Theme
 * Builder output for the same seed -- notably `outline`, which the design uses
 * at #C3CFCB for field and chip borders rather than M3's darker stroke.
 */

private val Teal40 = Color(0xFF006A60) // primary
private val Teal90 = Color(0xFF9FF2E4)
private val Teal80 = Color(0xFF83D5C6)
private val Teal10 = Color(0xFF00201C)

val LightColors: ColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    inversePrimary = Teal80,

    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E1),
    onSecondaryContainer = Teal10,

    tertiary = Color(0xFF46617A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),

    error = Color(0xFF8C1D18),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF4FBF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF4FBF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF4B6461),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F7F4),
    surfaceContainer = Color(0xFFEAF2EF),
    surfaceContainerHigh = Color(0xFFE3ECE9),
    surfaceContainerHighest = Color(0xFFDDE7E3),

    outline = Color(0xFFC3CFCB),
    outlineVariant = Color(0xFFDCE7E3),

    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    scrim = Color(0xFF000000),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Teal90,
    inversePrimary = Teal40,

    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCDE8E1),

    tertiary = Color(0xFFADCAE6),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2E4A63),
    onTertiaryContainer = Color(0xFFCCE5FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0D1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0D1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),

    surfaceContainerLowest = Color(0xFF080F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303634),

    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4947),

    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    scrim = Color(0xFF000000),
)

/** OLED variant: the surfaces collapse to true black, everything else holds. */
val TrueBlackColors: ColorScheme = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0F0E),
    surfaceContainer = Color(0xFF101614),
    surfaceContainerHigh = Color(0xFF1A201E),
    surfaceContainerHighest = Color(0xFF242A28),
)

/**
 * Design tokens that have no Material 3 role. Reached through [LocalBookmarkColors]
 * so they track the active theme the same way the M3 scheme does.
 */
@Immutable
data class BookmarkColors(
    /**
     * Cards, sheets-on-surface and settings groups. Not an M3 role: in dark
     * themes `surfaceContainerLowest` is darker than the background, which makes
     * a card recede instead of lift.
     */
    val cardSurface: Color,
    /** Background of the nav bar and of the "Preview pending" pill. */
    val pendingPillContainer: Color,
    val onPendingPillContainer: Color,
    /** IBM Plex Mono affordance labels: section headers, counters, timings. */
    val monoLabel: Color,
    /** Detail-sheet hard-failure card. Warmer than the M3 error container. */
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
    pendingPillContainer = Color(0xFFDAE5E1),
    onPendingPillContainer = Color(0xFF3F4A48),
    monoLabel = Color(0xFF3F6B64),
    failureContainer = Color(0xFFE8DED9),
    onFailureContainer = Color(0xFF5C2F19),
    onFailureContainerVariant = Color(0xFF6B4231),
    selectedOptionContainer = Color(0xFFEAF5F2),
    skeleton = Color(0xFFE3ECE9),
    skeletonHighlight = Color(0xFFF2F8F6),
)

val DarkBookmarkColors = BookmarkColors(
    cardSurface = Color(0xFF1B211F),
    pendingPillContainer = Color(0xFF2A3331),
    onPendingPillContainer = Color(0xFFBEC9C6),
    monoLabel = Color(0xFF7FA9A1),
    failureContainer = Color(0xFF3A2A22),
    onFailureContainer = Color(0xFFF0C8B4),
    onFailureContainerVariant = Color(0xFFD4AE9B),
    selectedOptionContainer = Color(0xFF15211F),
    skeleton = Color(0xFF252B2A),
    skeletonHighlight = Color(0xFF303634),
)

/** True-black counterpart to [DarkBookmarkColors], scaled down against [TrueBlackColors]'s ramp. */
val TrueBlackBookmarkColors = BookmarkColors(
    cardSurface = TrueBlackColors.surfaceContainer,
    pendingPillContainer = TrueBlackColors.surfaceContainerHigh,
    onPendingPillContainer = Color(0xFFBEC9C6),
    monoLabel = Color(0xFF7FA9A1),
    failureContainer = Color(0xFF3A2A22),
    onFailureContainer = Color(0xFFF0C8B4),
    onFailureContainerVariant = Color(0xFFD4AE9B),
    selectedOptionContainer = TrueBlackColors.surfaceContainerLow,
    skeleton = TrueBlackColors.surfaceContainerHigh,
    skeletonHighlight = TrueBlackColors.surfaceContainerHighest,
)

val LocalBookmarkColors = staticCompositionLocalOf { LightBookmarkColors }

/**
 * The eight category swatches offered in the create/edit dialog, in the order
 * the design lays them out. Also the source palette for generated monogram
 * tiles (spec 8.3) -- a domain always hashes to the same entry, which is what
 * makes a grid of fallbacks scannable.
 */
val CategorySwatches: List<Color> = listOf(
    Color(0xFFB0552F), // Reading
    Color(0xFF0F7A6B), // Dev
    Color(0xFF7A4FD6), // Design
    Color(0xFF2A5FD6), // Watch later
    Color(0xFFC0392B), // Recipes
    Color(0xFF7D918D), // neutral / Unsorted
    Color(0xFF7D5416),
    Color(0xFF00504A),
)

/** Hex strings for the same swatches, for persistence in `categories.colorHex`. */
val CategorySwatchHex: List<String> = listOf(
    "#B0552F", "#0F7A6B", "#7A4FD6", "#2A5FD6",
    "#C0392B", "#7D918D", "#7D5416", "#00504A",
)

/** Colour a persisted `colorHex` back into a [Color], falling back to the neutral swatch. */
fun parseCategoryColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return CategorySwatches[5]
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return CategorySwatches[5]
    return when (cleaned.length) {
        6 -> Color(value or 0xFF000000L)
        8 -> Color(value)
        else -> CategorySwatches[5]
    }
}
