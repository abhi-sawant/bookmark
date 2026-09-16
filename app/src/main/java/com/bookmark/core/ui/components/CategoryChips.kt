package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor

/**
 * Filter chip, 32dp tall with an 8dp radius rather than a full pill -- the
 * design is specific about this and it reads differently from a Material chip.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    trailingCount: Int? = null,
    height: Dp = Dimens.chipHeight,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(BookmarkShapes.chip)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.chip)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.chipHorizontalPadding)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (dotColor != null) 7.dp else 6.dp),
    ) {
        if (dotColor != null) CategoryDot(dotColor)
        Text(
            text = label,
            style = BookmarkTheme.text.chipLabel,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (trailingCount != null) {
            Text(
                text = trailingCount.toString(),
                style = BookmarkTheme.text.siteLine,
                color = (
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    ).copy(alpha = 0.65f),
            )
        }
    }
}

/**
 * "All" pinned first with a live count, then one chip per category. The colour
 * dot is never the only carrier of meaning -- the name sits right beside it.
 */
@Composable
fun CategoryFilterRow(
    categories: List<CategoryWithCount>,
    selectedCategoryId: String?,
    totalCount: Int,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    // Search's chip row shows no counts at all (they'd describe category
    // totals, not matches for the current query, which would mislead).
    showCounts: Boolean = true,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.chipGap),
        contentPadding = contentPadding,
    ) {
        item(key = "all") {
            CategoryChip(
                label = "All",
                selected = selectedCategoryId == null,
                onClick = { onSelect(null) },
                trailingCount = if (showCounts) totalCount else null,
            )
        }
        items(categories, key = { it.category.id }) { entry ->
            CategoryChip(
                label = entry.category.name,
                selected = selectedCategoryId == entry.category.id,
                onClick = { onSelect(entry.category.id) },
                dotColor = parseCategoryColor(entry.category.colorHex),
            )
        }
    }
}
