package com.bookmark.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The backdrop every screen sits on: the theme's base colour with three soft
 * orbs of light (violet, cyan, magenta in dark; peach, lilac, sky in light).
 * Drawn once as radial gradients -- no blur pass -- so it costs nothing while
 * scrolling.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.background
    val colors = BookmarkTheme.colors
    Box(
        modifier = modifier
            .background(base)
            .drawBehind {
                val w = size.width
                val h = size.height
                val a = colors.orbAlpha
                fun orb(color: Color, center: Offset, radius: Float, strength: Float) = drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = a * strength), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                )
                orb(colors.orbA, Offset(w * -0.05f, h * 0.02f), w * 1.05f, 0.62f)
                orb(colors.orbB, Offset(w * 1.1f, h * 0.38f), w * 0.95f, 0.42f)
                orb(colors.orbC, Offset(w * 0.0f, h * 1.0f), w * 1.0f, 0.4f)
            },
    ) {
        content()
    }
}

/**
 * Frosted-glass surface: translucent fill and a top-lit hairline. A real
 * backdrop blur is deliberately not used -- the aurora behind is already soft,
 * and a blur layer per card is what makes scrolling stutter.
 */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false): Modifier {
    val c = BookmarkTheme.colors
    return this
        .clip(shape)
        .background(if (strong) c.glassStrong else c.cardSurface, shape)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(c.glassEdgeTop, c.glassEdgeBottom)),
            shape = shape,
        )
}

/** The brand gradient fill (violet to sky in dark; coral to violet in light). */
@Composable
fun Modifier.accentFill(shape: Shape): Modifier {
    val c = BookmarkTheme.colors
    return this
        .clip(shape)
        .background(Brush.linearGradient(listOf(c.accentStart, c.accentEnd)), shape)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.08f))),
            shape = shape,
        )
}
