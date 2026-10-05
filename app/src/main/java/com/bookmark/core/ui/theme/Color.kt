package com.bookmark.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * "Aurora": a deep ink base lit by soft violet / cyan / magenta light, with
 * frosted-glass surfaces on top. Dark is the home theme; the light scheme is
 * its pearl counterpart (lavender-white, peach + sky light).
 *
 * Material roles carry text, accents and the opaque surfaces sheets and dialogs
 * sit on. Everything translucent -- cards, bars, chips -- lives in
 * [BookmarkColors] so it can be tuned per theme without touching M3 roles.
 */

private val Violet80 = Color(0xFFB4A9FF)
private val Violet40 = Color(0xFF5B45E0)

val LightColors: ColorScheme = lightColorScheme(
    primary = Violet40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3DEFF),
    onPrimaryContainer = Color(0xFF1B0D6B),
    inversePrimary = Violet80,

    secondary = Color(0xFF3C5EA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE4FF),
    onSecondaryContainer = Color(0xFF14204A),

    tertiary = Color(0xFFA23F8F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2),
    onTertiaryContainer = Color(0xFF3B0033),

    error = Color(0xFFBA1A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),

    background = Color(0xFFF4EFFB),
    onBackground = Color(0xFF1D1A30),
    surface = Color(0xFFF4EFFB),
    onSurface = Color(0xFF1D1A30),
    surfaceVariant = Color(0xFFE6E0F5),
    onSurfaceVariant = Color(0xFF5F5B78),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F4FF),
    surfaceContainer = Color(0xFFF1ECFA),
    surfaceContainerHigh = Color(0xFFEAE4F6),
    surfaceContainerHighest = Color(0xFFE2DBF2),

    outline = Color(0x331D1A30),
    outlineVariant = Color(0x1F1D1A30),

    inverseSurface = Color(0xFF2E2B45),
    inverseOnSurface = Color(0xFFF1EEFF),
    scrim = Color(0xFF000000),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA79BFF),
    onPrimary = Color(0xFF14104D),
    primaryContainer = Color(0xFF3A3290),
    onPrimaryContainer = Color(0xFFE6E2FF),
    inversePrimary = Violet40,

    secondary = Color(0xFF8AD0F7),
    onSecondary = Color(0xFF00334A),
    secondaryContainer = Color(0xFF38408A),
    onSecondaryContainer = Color(0xFFEEEBFF),

    tertiary = Color(0xFFF2A9E6),
    onTertiary = Color(0xFF4A0A40),
    tertiaryContainer = Color(0xFF6B2A61),
    onTertiaryContainer = Color(0xFFFFD7F2),

    error = Color(0xFFFFB3BE),
    onError = Color(0xFF670020),
    errorContainer = Color(0xFF8A1634),
    onErrorContainer = Color(0xFFFFDADF),

    background = Color(0xFF090A17),
    onBackground = Color(0xFFECEEFF),
    surface = Color(0xFF0F1124),
    onSurface = Color(0xFFECEEFF),
    surfaceVariant = Color(0xFF252849),
    onSurfaceVariant = Color(0xFFA9AEDA),

    surfaceContainerLowest = Color(0xFF070813),
    surfaceContainerLow = Color(0xFF111327),
    surfaceContainer = Color(0xFF15182F),
    surfaceContainerHigh = Color(0xFF1C1F3A),
    surfaceContainerHighest = Color(0xFF252849),

    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),

    inverseSurface = Color(0xFFECEEFF),
    inverseOnSurface = Color(0xFF1A1C33),
    scrim = Color(0xFF000000),
)

/** OLED variant: the surfaces collapse to true black, everything else holds. */
val TrueBlackColors: ColorScheme = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF05060D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF07080F),
    surfaceContainer = Color(0xFF0C0D18),
    surfaceContainerHigh = Color(0xFF131427),
    surfaceContainerHighest = Color(0xFF1A1C33),
)

/**
 * Design tokens that have no Material 3 role. Reached through [LocalBookmarkColors]
 * so they track the active theme the same way the M3 scheme does.
 */
@Immutable
data class BookmarkColors(
    /** Frosted card fill: translucent so the aurora shows through. */
    val cardSurface: Color,
    /** Stronger glass for things that float over content: bars, menus. */
    val glassStrong: Color,
    /** Hairline that outlines glass; drawn as a top-lit gradient. */
    val glassEdgeTop: Color,
    val glassEdgeBottom: Color,
    /** Pressed / hovered glass. */
    val glassHover: Color,
    /** Brand gradient: primary actions, selected chip, FAB. */
    val accentStart: Color,
    val accentEnd: Color,
    val onAccent: Color,
    /** The three aurora orbs behind everything, and how bright they glow. */
    val orbA: Color,
    val orbB: Color,
    val orbC: Color,
    val orbAlpha: Float,
    /** Background of the "Preview pending" pill. */
    val pendingPillContainer: Color,
    val onPendingPillContainer: Color,
    /** Mono affordance labels: section headers, counters, timings. */
    val monoLabel: Color,
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
    cardSurface = Color(0x99FFFFFF),
    glassStrong = Color(0xC7FFFFFF),
    glassEdgeTop = Color(0xFFFFFFFF),
    glassEdgeBottom = Color(0x80FFFFFF),
    glassHover = Color(0xCCFFFFFF),
    accentStart = Color(0xFFFF7A5C),
    accentEnd = Color(0xFF9A4DFF),
    onAccent = Color(0xFFFFFFFF),
    orbA = Color(0xFFFFB59A),
    orbB = Color(0xFFC3B5FF),
    orbC = Color(0xFF9EE0FF),
    orbAlpha = 0.8f,
    pendingPillContainer = Color(0x1F5B45E0),
    onPendingPillContainer = Color(0xFF4A3BB0),
    monoLabel = Color(0xFF5B45E0),
    failureContainer = Color(0xFFFFE6DE),
    onFailureContainer = Color(0xFF6B2512),
    onFailureContainerVariant = Color(0xFF8A4330),
    selectedOptionContainer = Color(0x265B45E0),
    skeleton = Color(0x261D1A30),
    skeletonHighlight = Color(0x4DFFFFFF),
)

val DarkBookmarkColors = BookmarkColors(
    cardSurface = Color(0x12FFFFFF),
    glassStrong = Color(0x8C14162C),
    glassEdgeTop = Color(0x38FFFFFF),
    glassEdgeBottom = Color(0x0FFFFFFF),
    glassHover = Color(0x1FFFFFFF),
    accentStart = Color(0xFF8A7BFF),
    accentEnd = Color(0xFF3AA8F6),
    onAccent = Color(0xFFFFFFFF),
    orbA = Color(0xFF6B5CFF),
    orbB = Color(0xFF1FB6E8),
    orbC = Color(0xFFD946EF),
    orbAlpha = 0.5f,
    pendingPillContainer = Color(0x1FFFFFFF),
    onPendingPillContainer = Color(0xFFC4C8EE),
    monoLabel = Color(0xFFA9AEDA),
    failureContainer = Color(0x40FF8A6B),
    onFailureContainer = Color(0xFFFFD9CC),
    onFailureContainerVariant = Color(0xFFE8B5A3),
    selectedOptionContainer = Color(0x337C6CFF),
    skeleton = Color(0x1AFFFFFF),
    skeletonHighlight = Color(0x33FFFFFF),
)

/** True-black counterpart to [DarkBookmarkColors]: the same glass, dimmer light. */
val TrueBlackBookmarkColors = DarkBookmarkColors.copy(
    glassStrong = Color(0xB3080914),
    orbAlpha = 0.3f,
)

val LocalBookmarkColors = staticCompositionLocalOf { DarkBookmarkColors }

/**
 * The eight category swatches offered in the create/edit dialog. Tuned bright
 * enough to read as a dot on the dark aurora; also the source palette for
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

/** The pre-Aurora swatches. Already-saved categories carry these hex values. */
private val LegacySwatchHex: List<String> = listOf(
    "#B0552F", "#0F7A6B", "#7A4FD6", "#2A5FD6",
    "#C0392B", "#7D918D", "#7D5416", "#00504A",
)

/** Maps a pre-Aurora swatch hex onto its Aurora counterpart; anything else passes through. */
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
