package com.bookmark.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme

/** The 32x4 grabber at the top of every bottom sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 10.dp)
            .size(width = 32.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** The coloured dot that carries category identity throughout the app. */
@Composable
fun CategoryDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 7.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            // The dot never carries meaning alone -- a name always sits beside it.
            .clearAndSetSemantics { },
    )
}

/** "Preview pending" (spec 8.6). Deliberately quiet: it is not an error. */
@Composable
fun PendingPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BookmarkTheme.colors.pendingPillContainer)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Preview pending",
            style = BookmarkTheme.text.siteLine,
            color = BookmarkTheme.colors.onPendingPillContainer,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Sweeping highlight used while metadata is in flight (spec 8.1, FETCHING). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = BookmarkShapes.thumbnailLarge) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = BookmarkTheme.colors.skeleton
    val highlight = BookmarkTheme.colors.skeletonHighlight
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        (progress - 0.4f).coerceIn(0f, 1f) to base,
                        progress.coerceIn(0f, 1f) to highlight,
                        (progress + 0.4f).coerceIn(0f, 1f) to base,
                    ),
                ),
            ),
    )
}

/** A flat skeleton line, as drawn in the quick-save preview card. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(BookmarkTheme.colors.skeleton),
    )
}

/**
 * The two- and three-way segmented controls in Settings and the import sheet:
 * a single outlined container with hairline dividers, not individual buttons.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 38.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(BookmarkShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.field),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = BookmarkTheme.text.chipLabel,
                    textAlign = TextAlign.Center,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** Uppercase IBM Plex Mono section label, as used across Settings. */
@Composable
fun MonoSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = BookmarkTheme.text.monoSection,
        color = BookmarkTheme.colors.monoLabel,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * The rounded card Settings groups its rows into. Shared with the import
 * preview sheet, which shows the same kind of grouped-row list.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookmarkShapes.settingsGroup,
        color = BookmarkTheme.colors.cardSurface,
        shadowElevation = 1.dp,
    ) {
        Column(content = content)
    }
}

/** Hairline divider between rows inside a [SettingsGroup]. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
