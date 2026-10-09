package com.bookmark.bookmarks.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
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
import com.bookmark.core.ui.theme.accentFill
import com.bookmark.core.ui.theme.panel

/**
 * Stable UiAutomator selectors for the :macrobenchmark module (M6), which
 * black-box-drives the app and has no compile-time access to these
 * composables. Keep in sync with `macrobenchmark/.../BaselineProfileGenerator.kt`.
 */
object HomeTestTags {
    const val GRID = "home_grid"
    const val LIST = "home_list"
    const val ADD_FAB = "add_fab"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> String?,
    onSelectCategory: (String?) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onBookmarkLongPress: (Bookmark) -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** Grid-to-detail thumbnail morph (spec 10). Null outside a shared-transition layout. */
    sharedTransitionScope: SharedTransitionScope? = null,
    /** The bookmark currently shown in the detail sheet, if any -- its grid card hides its own
     *  thumbnail while the shared element renders it at the detail sheet's hero position. */
    openDetailBookmarkId: String? = null,
    reducedMotion: Boolean = false,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Bookmarks",
                count = state.totalCount,
                onSearch = onSearch,
                searchEnabled = state.totalCount > 0,
            )

            if (state.isEmpty) {
                EmptyHome(onAdd = onAdd, modifier = Modifier.weight(1f))
            } else {
                CategoryFilterRow(
                    categories = state.categories,
                    selectedCategoryId = state.selectedCategoryId,
                    totalCount = state.totalCount,
                    onSelect = onSelectCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dimens.gridOuterPadding, end = Dimens.gridOuterPadding, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClickLabel = "Sort order") { sortMenuOpen = true }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = "Sort order",
                                tint = BookmarkTheme.colors.mutedLabel,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = state.sortOrder.shortLabel,
                                style = BookmarkTheme.text.caption,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        SortMenu(
                            expanded = sortMenuOpen,
                            sortOrder = state.sortOrder,
                            onDismiss = { sortMenuOpen = false },
                            onSetSortOrder = onSetSortOrder,
                        )
                    }
                    ViewModeToggle(viewMode = state.viewMode, onSetViewMode = onSetViewMode)
                }

                when (state.viewMode) {
                    ViewMode.GRID -> BookmarkGrid(
                        state = state,
                        thumbnailFor = thumbnailFor,
                        onOpen = onOpenBookmark,
                        onLongPress = onBookmarkLongPress,
                        sharedTransitionScope = sharedTransitionScope,
                        openDetailBookmarkId = openDetailBookmarkId,
                        reducedMotion = reducedMotion,
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

/** The two-way list/grid switch: a recessed trough, the active mode on a raised chip. */
@Composable
private fun ViewModeToggle(viewMode: ViewMode, onSetViewMode: (ViewMode) -> Unit) {
    val colors = BookmarkTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.hairline)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            Triple(ViewMode.LIST, Icons.AutoMirrored.Outlined.ViewList, "Switch to list"),
            Triple(ViewMode.GRID, Icons.Outlined.GridView, "Switch to grid"),
        ).forEach { (mode, icon, label) ->
            val selected = viewMode == mode
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (selected) Modifier.background(colors.cardSurface) else Modifier)
                    .clickable(role = Role.Button, onClickLabel = label) { onSetViewMode(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.onSurface else colors.mutedLabel,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BookmarkGrid(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> String?,
    onOpen: (Bookmark) -> Unit,
    onLongPress: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    openDetailBookmarkId: String? = null,
    reducedMotion: Boolean = false,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.GRID),
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
                thumbnailPath = thumbnailFor(bookmark),
                onClick = { onOpen(bookmark) },
                onLongClick = { onLongPress(bookmark) },
                sharedTransitionScope = sharedTransitionScope,
                isSharedThumbnailVisible = bookmark.id != openDetailBookmarkId,
                reducedMotion = reducedMotion,
            )
        }
    }
}

@Composable
private fun BookmarkList(
    state: HomeUiState,
    thumbnailFor: (Bookmark) -> String?,
    onOpen: (Bookmark) -> Unit,
    onLongPress: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.LIST),
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
                thumbnailPath = thumbnailFor(bookmark),
                onClick = { onOpen(bookmark) },
                onLongClick = { onLongPress(bookmark) },
            )
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    sortOrder: SortOrder,
    onDismiss: () -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, BookmarkTheme.colors.hairline),
    ) {
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
                        style = BookmarkTheme.text.rowTitle,
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
            .padding(horizontal = 44.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Three flat tiles fanned like a stack of saved links.
        Box(modifier = Modifier.size(width = 168.dp, height = 140.dp)) {
            val tileShape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .size(104.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer { rotationZ = -10f }
                    .clip(tileShape)
                    .background(BookmarkTheme.colors.accentSoft),
            )
            Box(
                Modifier
                    .size(104.dp)
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp)
                    .graphicsLayer { rotationZ = 8f }
                    .clip(tileShape)
                    .background(BookmarkTheme.colors.hairline),
            )
            Box(
                Modifier
                    .size(104.dp)
                    .align(Alignment.BottomCenter)
                    .panel(tileShape, raised = true),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = BookmarkTheme.colors.accent,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            text = "Nothing saved yet",
            style = BookmarkTheme.text.emptyTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 30.dp),
        )
        Text(
            text = "Share a link to this app from anywhere — tap Share in your browser and pick Bookmarks.",
            style = BookmarkTheme.text.fieldValue,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        PrimaryButton(
            text = "Paste a link",
            onClick = onAdd,
            height = 52.dp,
            modifier = Modifier.padding(top = 26.dp),
        )
    }
}

@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.fabSize)
            .testTag(HomeTestTags.ADD_FAB)
            .shadow(8.dp, com.bookmark.core.ui.theme.BookmarkShapes.fab, ambientColor = BookmarkTheme.colors.accent.copy(alpha = 0.5f), spotColor = BookmarkTheme.colors.accent.copy(alpha = 0.5f))
            .accentFill(com.bookmark.core.ui.theme.BookmarkShapes.fab)
            .clickable(role = Role.Button, onClickLabel = "Add bookmark", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Add bookmark",
            tint = BookmarkTheme.colors.onAccent,
            modifier = Modifier.size(26.dp),
        )
    }
}
