package com.bookmark.categories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.ui.components.CategoryDot
import com.bookmark.core.ui.components.ScreenHeader
import com.bookmark.core.ui.components.dragHandle
import com.bookmark.core.ui.components.rememberDragDropState
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.accentFill
import com.bookmark.core.ui.theme.panel
import com.bookmark.core.ui.theme.parseCategoryColor

@Composable
fun CategoriesScreen(
    categories: List<CategoryWithCount>,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Category) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveCommitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, onMove)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Categories",
                count = categories.size,
                onSearch = onSearch,
                )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.gridOuterPadding,
                    end = Dimens.gridOuterPadding,
                    top = 4.dp,
                    bottom = 140.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    categories,
                    key = { _, entry -> entry.category.id },
                    contentType = { _, _ -> "categoryRow" },
                ) { index, entry ->
                    CategoryRow(
                        entry = entry,
                        onClick = { onEdit(entry.category) },
                        handleModifier = Modifier.dragHandle(dragDropState, index, onMoveCommitted),
                        modifier = Modifier.graphicsLayer {
                            translationY = dragDropState.offsetFor(index)
                            if (dragDropState.draggingItemIndex == index) shadowElevation = 12f
                        },
                    )
                }
            }
        }

        NewCategoryFab(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.fabEndMargin, bottom = Dimens.fabBottomMargin),
        )
    }
}

@Composable
private fun CategoryRow(
    entry: CategoryWithCount,
    onClick: () -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().panel(BookmarkShapes.categoryRow)) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp)
                .semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Reorder ${entry.category.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // 48dp target around a 20dp glyph, per the accessibility minimum.
                modifier = handleModifier
                    .size(Dimens.touchTarget)
                    .padding(14.dp),
            )
            CategoryDot(
                color = parseCategoryColor(entry.category.colorHex),
                size = Dimens.categoryDotLarge,
            )
            Text(
                text = entry.category.name,
                style = BookmarkTheme.text.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (entry.category.isDefault) {
                Box(
                    modifier = Modifier
                        .panel(RoundedCornerShape(percent = 50))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Default",
                        style = BookmarkTheme.text.siteLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = entry.count.toString(),
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewCategoryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.extendedFabHeight)
            .accentFill(BookmarkShapes.extendedFab)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = BookmarkTheme.colors.onAccent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "New category",
            style = BookmarkTheme.text.buttonLabel,
            color = BookmarkTheme.colors.onAccent,
        )
    }
}
