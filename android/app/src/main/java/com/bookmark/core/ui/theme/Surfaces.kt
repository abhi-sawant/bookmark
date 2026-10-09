package com.bookmark.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The backdrop every screen sits on: one flat, solid colour. */
@Composable
fun SlateBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}

/**
 * A raised surface: solid fill and a 1dp hairline. Depth comes from the line,
 * not from blur or shadow, so nothing costs anything while scrolling.
 * [raised] is for things that float over content (bars, menus).
 */
@Composable
fun Modifier.panel(shape: Shape, raised: Boolean = false): Modifier {
    val c = BookmarkTheme.colors
    return this
        .clip(shape)
        .background(if (raised) c.raisedSurface else c.cardSurface, shape)
        .border(width = 1.dp, color = c.hairline, shape = shape)
}

/** The solid accent fill: primary buttons, the FAB, an "on" switch. */
@Composable
fun Modifier.accentFill(shape: Shape): Modifier {
    val c = BookmarkTheme.colors
    return this
        .clip(shape)
        .background(c.accent, shape)
}
