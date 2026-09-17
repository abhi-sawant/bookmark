package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookmark.core.ui.theme.CategorySwatches
import com.bookmark.core.ui.theme.DmSans
import com.bookmark.core.util.DomainColor
import com.bookmark.core.util.TitleFallback
import kotlin.math.sqrt

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
            .diagonalStripes()
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

/**
 * The diagonal hairline texture the design draws behind every no-thumbnail
 * tile: `repeating-linear-gradient(135deg, rgba(0,0,0,.05) 0 5px, transparent
 * 5px 11px)`. Drawn on top of the solid monogram fill rather than the CSS's
 * literal stacking (tint + stripe, fully covered by an opaque monogram layer)
 * so the texture is actually visible instead of hidden under solid colour.
 */
private fun Modifier.diagonalStripes(
    stripeColor: Color = Color.Black.copy(alpha = 0.05f),
    stripeWidth: Dp = 5.dp,
    period: Dp = 11.dp,
): Modifier = drawWithCache {
    val stripeWidthPx = stripeWidth.toPx()
    val periodPx = period.toPx()
    val diagonal = sqrt(size.width * size.width + size.height * size.height)
    onDrawWithContent {
        drawContent()
        clipRect {
            rotate(135f) {
                var x = -diagonal
                while (x < diagonal) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(x, -diagonal),
                        size = Size(stripeWidthPx, diagonal * 2),
                    )
                    x += periodPx
                }
            }
        }
    }
}
