package com.bookmark.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bookmark.R

/**
 * DM Sans ships as a single variable font, so each weight is the same file with
 * a different `wght` axis value rather than three separate TTFs.
 */
val DmSans = FontFamily(
    Font(
        R.font.dm_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.dm_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.dm_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Baseline M3 scale, re-cut in DM Sans. Used by stock Material components. */
val BookmarkTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = DmSans),
        displayMedium = displayMedium.copy(fontFamily = DmSans),
        displaySmall = displaySmall.copy(fontFamily = DmSans),
        headlineLarge = headlineLarge.copy(fontFamily = DmSans),
        headlineMedium = headlineMedium.copy(fontFamily = DmSans),
        headlineSmall = headlineSmall.copy(fontFamily = DmSans),
        titleLarge = titleLarge.copy(fontFamily = DmSans),
        titleMedium = titleMedium.copy(fontFamily = DmSans),
        titleSmall = titleSmall.copy(fontFamily = DmSans),
        bodyLarge = bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = bodyMedium.copy(fontFamily = DmSans),
        bodySmall = bodySmall.copy(fontFamily = DmSans),
        labelLarge = labelLarge.copy(fontFamily = DmSans),
        labelMedium = labelMedium.copy(fontFamily = DmSans),
        labelSmall = labelSmall.copy(fontFamily = DmSans),
    )
}

/**
 * The design's own styles, which mostly sit between M3 steps (14.5sp card
 * titles, 12.5sp descriptions). Kept separate from [BookmarkTypography] so the
 * stock components keep a sane scale and screens can be literal about the spec.
 */
@Immutable
data class BookmarkTextStyles(
    val screenTitle: TextStyle,
    val screenCount: TextStyle,
    val sheetTitle: TextStyle,
    val dialogTitle: TextStyle,
    val detailTitle: TextStyle,
    val emptyTitle: TextStyle,
    val cardTitle: TextStyle,
    val cardDescription: TextStyle,
    val siteLine: TextStyle,
    val chipLabel: TextStyle,
    val fieldLabel: TextStyle,
    val fieldValue: TextStyle,
    val rowTitle: TextStyle,
    val rowSubtitle: TextStyle,
    val buttonLabel: TextStyle,
    val monoSection: TextStyle,
    val monoCounter: TextStyle,
    val monoCaption: TextStyle,
)

val DesignTextStyles = BookmarkTextStyles(
    screenTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em,
    ),
    screenCount = TextStyle(fontFamily = DmSans, fontSize = 12.5.sp, lineHeight = 16.sp),
    sheetTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 24.sp,
    ),
    dialogTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    detailTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 21.sp, lineHeight = 27.sp,
    ),
    emptyTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    cardTitle = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp, lineHeight = 19.sp,
    ),
    cardDescription = TextStyle(fontFamily = DmSans, fontSize = 12.5.sp, lineHeight = 17.sp),
    siteLine = TextStyle(fontFamily = DmSans, fontSize = 11.5.sp, lineHeight = 15.sp),
    chipLabel = TextStyle(fontFamily = DmSans, fontSize = 14.sp, lineHeight = 18.sp),
    fieldLabel = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp, lineHeight = 14.sp,
    ),
    fieldValue = TextStyle(fontFamily = DmSans, fontSize = 15.sp, lineHeight = 20.sp),
    rowTitle = TextStyle(fontFamily = DmSans, fontSize = 15.sp, lineHeight = 20.sp),
    rowSubtitle = TextStyle(fontFamily = DmSans, fontSize = 12.sp, lineHeight = 17.sp),
    buttonLabel = TextStyle(
        fontFamily = DmSans, fontWeight = FontWeight.Medium,
        fontSize = 14.5.sp, lineHeight = 19.sp,
    ),
    monoSection = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.1.em,
    ),
    monoCounter = TextStyle(fontFamily = PlexMono, fontSize = 9.5.sp, lineHeight = 13.sp),
    monoCaption = TextStyle(
        fontFamily = PlexMono, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.08.em,
    ),
)

val LocalBookmarkTextStyles = staticCompositionLocalOf { DesignTextStyles }
