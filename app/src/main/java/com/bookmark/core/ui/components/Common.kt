package com.bookmark.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.panel
import androidx.compose.foundation.layout.Arrangement

/** The 32x4 grabber at the top of every bottom sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = 10.dp)
            .size(width = 32.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
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
            .heightIn(min = 22.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(BookmarkTheme.colors.pendingPillContainer)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Preview pending",
            style = BookmarkTheme.text.siteLine,
            color = BookmarkTheme.colors.onPendingPillContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
 * a recessed trough with the chosen option lifted onto the ink fill.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val troughShape = RoundedCornerShape(14.dp)
    val segmentShape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(troughShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height - 6.dp)
                    .clip(segmentShape)
                    .then(if (selected) Modifier.background(BookmarkTheme.colors.selectedFill, segmentShape) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = BookmarkTheme.text.chipLabel,
                    textAlign = TextAlign.Center,
                    color = if (selected) {
                        BookmarkTheme.colors.onSelectedFill
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** The quiet lowercase label above a group of rows (Settings, import preview). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.lowercase(),
        style = BookmarkTheme.text.sectionLabel,
        color = BookmarkTheme.colors.mutedLabel,
        modifier = modifier.padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 9.dp),
    )
}

/**
 * The rounded card Settings groups its rows into. Shared with the import
 * preview sheet, which shows the same kind of grouped-row list.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel(BookmarkShapes.settingsGroup),
        content = content,
    )
}

/** Hairline divider between rows inside a [SettingsGroup]. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = BookmarkTheme.colors.hairline)
}
