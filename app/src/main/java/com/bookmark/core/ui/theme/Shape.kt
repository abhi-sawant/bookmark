package com.bookmark.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii: soft, but never pill-shaped except where a count lives. */
object BookmarkShapes {
    val card = RoundedCornerShape(20.dp)
    val previewCard = RoundedCornerShape(20.dp)
    val settingsGroup = RoundedCornerShape(22.dp)
    val categoryRow = RoundedCornerShape(18.dp)
    val dialog = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val field = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(12.dp)
    val thumbnailLarge = RoundedCornerShape(18.dp)
    val thumbnailMedium = RoundedCornerShape(14.dp)
    val fab = RoundedCornerShape(19.dp)
    val extendedFab = RoundedCornerShape(19.dp)
    val primaryButton = RoundedCornerShape(16.dp)
    val smallButton = RoundedCornerShape(12.dp)
    val iconButton = RoundedCornerShape(14.dp)
    val countPill = RoundedCornerShape(percent = 50)
}

val Material3Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fixed measurements the design repeats across screens. */
object Dimens {
    val screenHeaderHeight: Dp = 64.dp
    val headerStartPadding: Dp = 20.dp
    val headerEndPadding: Dp = 16.dp
    val iconSlot: Dp = 46.dp
    val touchTarget: Dp = 48.dp

    val gridOuterPadding: Dp = 20.dp
    val gridGutter: Dp = 12.dp

    val listRowVerticalPadding: Dp = 11.dp
    val listRowHorizontalPadding: Dp = 20.dp
    val listThumbnail: Dp = 64.dp

    val chipHeight: Dp = 36.dp
    val chipHorizontalPadding: Dp = 14.dp
    val chipGap: Dp = 8.dp
    val categoryDot: Dp = 7.dp
    val categoryDotLarge: Dp = 12.dp

    val fabSize: Dp = 58.dp
    val fabEndMargin: Dp = 20.dp
    /** Clears the bottom navigation bar, as drawn. */
    val fabBottomMargin: Dp = 20.dp
    val extendedFabHeight: Dp = 56.dp

    val sheetHorizontalPadding: Dp = 20.dp
    val primaryButtonHeight: Dp = 52.dp
    val secondaryButtonHeight: Dp = 44.dp
    val detailHeroHeight: Dp = 196.dp
    val previewThumbnail: Dp = 76.dp
}
