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
 * Aurora's three voices, each a single variable font so weights are `wght`
 * axis values rather than separate files: Bricolage Grotesque for display
 * (titles, card headlines), Instrument Sans for text, Geist Mono for counters
 * and labels.
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.bricolage_grotesque, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.instrument_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.instrument_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.instrument_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/** Baseline M3 scale, re-cut in DM Sans. Used by stock Material components. */
val BookmarkTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Bricolage),
        displayMedium = displayMedium.copy(fontFamily = Bricolage),
        displaySmall = displaySmall.copy(fontFamily = Bricolage),
        headlineLarge = headlineLarge.copy(fontFamily = Bricolage),
        headlineMedium = headlineMedium.copy(fontFamily = Bricolage),
        headlineSmall = headlineSmall.copy(fontFamily = Bricolage),
        titleLarge = titleLarge.copy(fontFamily = Bricolage),
        titleMedium = titleMedium.copy(fontFamily = InstrumentSans),
        titleSmall = titleSmall.copy(fontFamily = InstrumentSans),
        bodyLarge = bodyLarge.copy(fontFamily = InstrumentSans),
        bodyMedium = bodyMedium.copy(fontFamily = InstrumentSans),
        bodySmall = bodySmall.copy(fontFamily = InstrumentSans),
        labelLarge = labelLarge.copy(fontFamily = InstrumentSans),
        labelMedium = labelMedium.copy(fontFamily = InstrumentSans),
        labelSmall = labelSmall.copy(fontFamily = InstrumentSans),
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
        fontFamily = Bricolage, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.012).em,
    ),
    screenCount = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    sheetTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.01).em,
    ),
    dialogTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.01).em,
    ),
    detailTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.01).em,
    ),
    emptyTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.012).em,
    ),
    cardTitle = TextStyle(
        fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.em,
    ),
    cardDescription = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp, lineHeight = 18.sp),
    siteLine = TextStyle(fontFamily = InstrumentSans, fontSize = 12.5.sp, lineHeight = 16.sp),
    chipLabel = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    fieldLabel = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.02.em,
    ),
    fieldValue = TextStyle(fontFamily = InstrumentSans, fontSize = 15.5.sp, lineHeight = 21.sp),
    rowTitle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, lineHeight = 21.sp),
    rowSubtitle = TextStyle(fontFamily = InstrumentSans, fontSize = 12.5.sp, lineHeight = 17.sp),
    buttonLabel = TextStyle(
        fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    monoSection = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.08.em,
    ),
    monoCounter = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, lineHeight = 14.sp),
    monoCaption = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
)

val LocalBookmarkTextStyles = staticCompositionLocalOf { DesignTextStyles }
