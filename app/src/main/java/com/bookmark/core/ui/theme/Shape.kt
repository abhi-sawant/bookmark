package com.bookmark.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii as drawn in the 2a screen set. */
object BookmarkShapes {
    val card = RoundedCornerShape(26.dp)
    val previewCard = RoundedCornerShape(22.dp)
    val settingsGroup = RoundedCornerShape(24.dp)
    val categoryRow = RoundedCornerShape(20.dp)
    val dialog = RoundedCornerShape(32.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val field = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(percent = 50)
    val thumbnailLarge = RoundedCornerShape(18.dp)
    val thumbnailMedium = RoundedCornerShape(14.dp)
    val fab = RoundedCornerShape(22.dp)
    val extendedFab = RoundedCornerShape(22.dp)
    val primaryButton = RoundedCornerShape(percent = 50)
    val smallButton = RoundedCornerShape(14.dp)
}

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fixed measurements the design repeats across screens. */
object Dimens {
    val screenHeaderHeight: Dp = 64.dp
    val headerStartPadding: Dp = 20.dp
    val headerEndPadding: Dp = 16.dp
    val iconSlot: Dp = 46.dp
    val touchTarget: Dp = 48.dp

    val gridOuterPadding: Dp = 16.dp
    val gridGutter: Dp = 12.dp

    val listRowVerticalPadding: Dp = 11.dp
    val listRowHorizontalPadding: Dp = 20.dp
    val listThumbnail: Dp = 72.dp

    val chipHeight: Dp = 38.dp
    val chipHorizontalPadding: Dp = 15.dp
    val chipGap: Dp = 8.dp
    val categoryDot: Dp = 7.dp
    val categoryDotLarge: Dp = 12.dp

    val fabSize: Dp = 62.dp
    val fabEndMargin: Dp = 18.dp
    /** Clears the bottom navigation bar, as drawn. */
    val fabBottomMargin: Dp = 20.dp
    val extendedFabHeight: Dp = 56.dp

    val sheetHorizontalPadding: Dp = 20.dp
    val primaryButtonHeight: Dp = 52.dp
    val secondaryButtonHeight: Dp = 44.dp
    val detailHeroHeight: Dp = 196.dp
    val previewThumbnail: Dp = 76.dp
}
