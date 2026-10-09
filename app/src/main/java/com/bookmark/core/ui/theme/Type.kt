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
 * Slate speaks in one voice: Hanken Grotesk, a single variable font so weights
 * are `wght` axis values rather than separate files. Hierarchy comes from
 * weight and size, with tight tracking on the large steps.
 */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.hanken_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.hanken_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.hanken_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Baseline M3 scale, re-cut in Hanken Grotesk. Used by stock Material components. */
val BookmarkTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = HankenGrotesk),
        displayMedium = displayMedium.copy(fontFamily = HankenGrotesk),
        displaySmall = displaySmall.copy(fontFamily = HankenGrotesk),
        headlineLarge = headlineLarge.copy(fontFamily = HankenGrotesk),
        headlineMedium = headlineMedium.copy(fontFamily = HankenGrotesk),
        headlineSmall = headlineSmall.copy(fontFamily = HankenGrotesk),
        titleLarge = titleLarge.copy(fontFamily = HankenGrotesk),
        titleMedium = titleMedium.copy(fontFamily = HankenGrotesk),
        titleSmall = titleSmall.copy(fontFamily = HankenGrotesk),
        bodyLarge = bodyLarge.copy(fontFamily = HankenGrotesk),
        bodyMedium = bodyMedium.copy(fontFamily = HankenGrotesk),
        bodySmall = bodySmall.copy(fontFamily = HankenGrotesk),
        labelLarge = labelLarge.copy(fontFamily = HankenGrotesk),
        labelMedium = labelMedium.copy(fontFamily = HankenGrotesk),
        labelSmall = labelSmall.copy(fontFamily = HankenGrotesk),
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
    val sectionLabel: TextStyle,
    val counter: TextStyle,
    val caption: TextStyle,
)

val DesignTextStyles = BookmarkTextStyles(
    screenTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.04).em,
    ),
    screenCount = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
    sheetTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.03).em,
    ),
    dialogTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.03).em,
    ),
    detailTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.03).em,
    ),
    emptyTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.04).em,
    ),
    cardTitle = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em,
    ),
    cardDescription = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    siteLine = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    chipLabel = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    fieldLabel = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 15.sp,
    ),
    fieldValue = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, lineHeight = 21.sp),
    rowTitle = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    rowSubtitle = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp),
    buttonLabel = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    sectionLabel = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.01.em,
    ),
    counter = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp),
    caption = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp,
    ),
)

val LocalBookmarkTextStyles = staticCompositionLocalOf { DesignTextStyles }
