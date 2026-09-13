package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bookmark.core.ui.theme.CategorySwatches
import com.bookmark.core.ui.theme.DmSans
import com.bookmark.core.util.DomainColor
import com.bookmark.core.util.TitleFallback

/**
 * The generated fallback thumbnail (spec 8.3).
 *
 * Rendered at display time from the URL alone, so it costs no storage and never
 * needs a fetch. The colour is a deterministic hash of the registrable domain,
 * which is what makes a grid of fallbacks scannable rather than noisy -- the
 * same site is always the same colour.
 */
@Composable
fun MonogramTile(
    url: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    accentColor: Color? = null,
) {
    val background = accentColor ?: remember(url) { CategorySwatches[DomainColor.indexFor(url)] }
    val monogram = remember(url) { TitleFallback.monogram(url) }

    Box(
        modifier = modifier
            .background(background)
            // The title beside it already names the bookmark; a screen reader
            // announcing two letters of the domain adds nothing.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            color = Color.White,
            fontFamily = DmSans,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
        )
    }
}

/**
 * Fallback tiles have no intrinsic aspect ratio, so the staggered grid would
 * collapse to uniform rows. Deriving the height from the same domain hash keeps
 * the stagger the design shows while staying stable across recompositions.
 */
fun fallbackTileHeightDp(url: String): Int {
    val heights = intArrayOf(96, 104, 110, 120, 132, 148, 158, 176)
    return heights[DomainColor.indexFor(url)]
}

@Composable
fun MonogramTileFullSize(url: String, accentColor: Color? = null, fontSize: TextUnit = 34.sp) {
    MonogramTile(
        url = url,
        modifier = Modifier.fillMaxSize(),
        fontSize = fontSize,
        accentColor = accentColor,
    )
}
