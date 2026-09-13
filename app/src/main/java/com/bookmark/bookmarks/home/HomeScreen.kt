package com.bookmark.bookmarks.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.SortOrder
import com.bookmark.core.model.ViewMode
import com.bookmark.core.ui.components.BookmarkGridCard
import com.bookmark.core.ui.components.BookmarkListRow
import com.bookmark.core.ui.components.CategoryFilterRow
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.ScreenHeader
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import java.io.File

@Composable
fun HomeScreen(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> File?,
    onSelectCategory: (String?) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onBookmarkLongPress: (Bookmark) -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Bookmarks",
                count = state.totalCount,
                onSearch = onSearch,
                onOverflow = { menuOpen = true },
                searchEnabled = state.totalCount > 0,
                overflowHighlighted = menuOpen,
                overflowMenu = {
                    HomeOverflowMenu(
                        expanded = menuOpen,
                        viewMode = state.viewMode,
                        sortOrder = state.sortOrder,
                        onDismiss = { menuOpen = false },
                        onSetViewMode = onSetViewMode,
                        onSetSortOrder = onSetSortOrder,
                    )
                },
            )

            if (state.isEmpty) {
                EmptyHome(onAdd = onAdd, modifier = Modifier.weight(1f))
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryFilterRow(
                        categories = state.categories,
                        selectedCategoryId = state.selectedCategoryId,
                        totalCount = state.totalCount,
                        onSelect = onSelectCategory,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.sortOrder.shortLabel,
                        style = BookmarkTheme.text.monoCaption,
                        color = BookmarkTheme.colors.monoLabel,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                when (state.viewMode) {
                    ViewMode.GRID -> BookmarkGrid(
                        state = state,
                        thumbnailFor = thumbnailFor,
                        onOpen = onOpenBookmark,
                        onLongPress = onBookmarkLongPress,
                        modifier = Modifier.weight(1f),
                    )

                    ViewMode.LIST -> BookmarkList(
                        state = state,
                        thumbnailFor = thumbnailFor,
                        onOpen = onOpenBookmark,
                        onLongPress = onBookmarkLongPress,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!state.isEmpty) {
            AddFab(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.fabEndMargin, bottom = Dimens.fabBottomMargin),
            )
        }
    }
}

@Composable
private fun BookmarkGrid(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> File?,
    onOpen: (Bookmark) -> Unit,
    onLongPress: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.gridOuterPadding,
            end = Dimens.gridOuterPadding,
            bottom = 120.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gridGutter),
        verticalItemSpacing = Dimens.gridGutter,
    ) {
        items(
            items = state.bookmarks,
            key = { it.id },
            contentType = { "bookmarkCard" },
        ) { bookmark ->
            BookmarkGridCard(
                bookmark = bookmark,
                category = state.categoriesById[bookmark.categoryId],
                thumbnailFile = thumbnailFor(bookmark),
                onClick = { onOpen(bookmark) },
                onLongClick = { onLongPress(bookmark) },
            )
        }
    }
}

@Composable
private fun BookmarkList(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> File?,
    onOpen: (Bookmark) -> Unit,
    onLongPress: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        items(
            items = state.bookmarks,
            key = { it.id },
            contentType = { "bookmarkRow" },
        ) { bookmark ->
            BookmarkListRow(
                bookmark = bookmark,
                category = state.categoriesById[bookmark.categoryId],
                thumbnailFile = thumbnailFor(bookmark),
                onClick = { onOpen(bookmark) },
                onLongClick = { onLongPress(bookmark) },
            )
        }
    }
}

@Composable
private fun HomeOverflowMenu(
    expanded: Boolean,
    viewMode: ViewMode,
    sortOrder: SortOrder,
    onDismiss: () -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(if (viewMode == ViewMode.GRID) "Switch to list" else "Switch to grid")
            },
            onClick = {
                onSetViewMode(if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
                onDismiss()
            },
        )
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = when (order) {
                            SortOrder.NEWEST -> "Sort: Newest"
                            SortOrder.OLDEST -> "Sort: Oldest"
                            SortOrder.TITLE_AZ -> "Sort: Title A–Z"
                            SortOrder.CATEGORY -> "Sort: Category"
                        },
                        color = if (order == sortOrder) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                onClick = {
                    onSetSortOrder(order)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = "Nothing saved yet",
            style = BookmarkTheme.text.emptyTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 26.dp),
        )
        Text(
            text = "Share a link to this app from anywhere — tap Share in your browser and pick Bookmarks.",
            style = BookmarkTheme.text.fieldValue,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 9.dp),
        )
        PrimaryButton(
            text = "Paste a link",
            onClick = onAdd,
            height = 48.dp,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(Dimens.fabSize),
        shape = com.bookmark.core.ui.theme.BookmarkShapes.fab,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Add bookmark",
            modifier = Modifier.size(28.dp),
        )
    }
}
