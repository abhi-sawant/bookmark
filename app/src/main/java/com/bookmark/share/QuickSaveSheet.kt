package com.bookmark.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.ui.components.CategoryChip
import com.bookmark.core.ui.components.OutlinedField
import com.bookmark.core.ui.components.PreviewCard
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.SecondaryButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor

/**
 * The share-sheet hot path (spec 5.3, 6.3). A condensed add sheet: preview,
 * editable title, category chips with the last-used one pre-selected, then
 * Save and More options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSaveSheet(
    state: QuickSaveUiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onUseOtherUrl: (String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onSave: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    var othersExpanded by remember { mutableStateOf(false) }
    var creatingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BookmarkShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.sheetHorizontalPadding)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Save to Bookmarks",
                style = BookmarkTheme.text.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            PreviewCard(
                title = state.title,
                description = null,
                siteName = state.siteName,
                url = state.url,
                thumbnailFile = null,
                fetching = state.fetching,
                previewModel = state.imageCandidates.firstOrNull(),
                modifier = Modifier.padding(top = 14.dp),
            )

            // Shown only when extraction found nothing, so the share is still
            // recoverable rather than dying with a toast (spec 6.2 rule 3).
            if (state.urlError != null) {
                OutlinedField(
                    label = "URL",
                    value = state.url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = state.urlError,
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            OutlinedField(
                label = "Title",
                value = state.title,
                onValueChange = onTitleChange,
                maxLength = BookmarkLimits.TITLE_MAX,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (state.otherUrls.isNotEmpty()) {
                Text(
                    text = "Other links in this share ${if (othersExpanded) "▴" else "▾"}",
                    style = BookmarkTheme.text.rowSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .clickable { othersExpanded = !othersExpanded },
                )
                AnimatedVisibility(visible = othersExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        state.otherUrls.forEach { url ->
                            Text(
                                text = url,
                                style = BookmarkTheme.text.siteLine,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUseOtherUrl(url) }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Text(
                text = "Save to",
                style = BookmarkTheme.text.siteLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.chipGap),
                verticalArrangement = Arrangement.spacedBy(Dimens.chipGap),
            ) {
                state.categories.forEach { entry ->
                    CategoryChip(
                        label = entry.category.name,
                        selected = entry.category.id == state.selectedCategoryId,
                        onClick = { onCategoryChange(entry.category.id) },
                        dotColor = parseCategoryColor(entry.category.colorHex),
                    )
                }
                CategoryChip(
                    label = "＋ New",
                    selected = false,
                    onClick = { creatingCategory = true },
                )
            }

            if (creatingCategory) {
                OutlinedField(
                    label = "New category",
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    maxLength = com.bookmark.core.model.Category.NAME_MAX_LENGTH,
                    modifier = Modifier.padding(top = 10.dp),
                )
                PrimaryButton(
                    text = "Create",
                    onClick = {
                        onCreateCategory(newCategoryName.trim())
                        newCategoryName = ""
                        creatingCategory = false
                    },
                    enabled = newCategoryName.isNotBlank(),
                    height = Dimens.secondaryButtonHeight,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = "More options",
                    onClick = onMoreOptions,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Save",
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
