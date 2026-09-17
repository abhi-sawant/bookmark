package com.bookmark.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.Bookmark
import com.bookmark.core.ui.components.BookmarkThumbnail
import com.bookmark.core.ui.components.CategoryFilterRow
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme

@Composable
fun SearchScreen(
    state: SearchUiState,
    thumbnailFor: (Bookmark) -> String?,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onBookmarkLongPress: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            SearchField(
                value = state.query,
                onValueChange = onQueryChange,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
            )
            if (state.query.isNotEmpty()) {
                IconButton(onClick = onClearQuery, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        CategoryFilterRow(
            categories = state.categories,
            selectedCategoryId = state.selectedCategoryId,
            totalCount = state.totalCount,
            onSelect = onSelectCategory,
            showCounts = false,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        when {
            state.query.isBlank() -> SearchPrompt(
                title = "Search your bookmarks",
                subtitle = "Titles, sites and descriptions -- as you type.",
            )

            state.hasSearched && state.results.isEmpty() -> SearchPrompt(
                title = "No matches",
                subtitle = "Try a different word, or clear the category filter.",
            )

            else -> {
                if (state.hasSearched) {
                    Text(
                        text = "${state.results.size} results" +
                            (state.elapsedMs?.let { " · ${it}ms" } ?: ""),
                        style = BookmarkTheme.text.monoCaption,
                        color = BookmarkTheme.colors.monoLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.results, key = { it.id }, contentType = { "searchResultRow" }) { bookmark ->
                        SearchResultRow(
                            bookmark = bookmark,
                            categoryName = state.categories
                                .find { it.category.id == bookmark.categoryId }
                                ?.category?.name,
                            thumbnailPath = thumbnailFor(bookmark),
                            queryTerms = state.queryTerms,
                            onClick = { onOpenBookmark(bookmark) },
                            onLongClick = { onBookmarkLongPress(bookmark) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Search bookmarks",
                style = BookmarkTheme.text.fieldValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = BookmarkTheme.text.fieldValue.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag(SEARCH_FIELD_TEST_TAG),
        )
    }
}

/** Kept in sync with `macrobenchmark/.../BaselineProfileGenerator.kt` (M6). */
const val SEARCH_FIELD_TEST_TAG = "search_field"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    bookmark: Bookmark,
    categoryName: String?,
    thumbnailPath: String?,
    queryTerms: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Open ${bookmark.title}",
                onLongClickLabel = "Bookmark actions",
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookmarkThumbnail(
            bookmark = bookmark,
            thumbnailPath = thumbnailPath,
            modifier = Modifier
                .size(56.dp)
                .clip(BookmarkShapes.thumbnailMedium),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedText(
                    text = bookmark.title,
                    ranges = findHighlightRanges(bookmark.title, queryTerms),
                    highlightColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                style = BookmarkTheme.text.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(bookmark.siteName, categoryName).joinToString(" · "),
                style = BookmarkTheme.text.siteLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun highlightedText(text: String, ranges: List<IntRange>, highlightColor: Color) =
    buildAnnotatedString {
        append(text)
        for (range in ranges) {
            addStyle(SpanStyle(background = highlightColor), range.first, range.last + 1)
        }
    }

@Composable
private fun SearchPrompt(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = BookmarkTheme.text.emptyTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = BookmarkTheme.text.fieldValue,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}
