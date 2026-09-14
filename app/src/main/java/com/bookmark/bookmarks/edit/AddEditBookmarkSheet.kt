package com.bookmark.bookmarks.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bookmark.core.model.BookmarkLimits
import com.bookmark.core.model.Category
import com.bookmark.core.ui.components.CategoryDot
import com.bookmark.core.ui.components.OutlinedField
import com.bookmark.core.ui.components.PreviewCard
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.SecondaryButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBookmarkSheet(
    state: AddEditUiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onAcceptClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onSelectThumbnailCandidate: (String) -> Unit,
    onPickLocalThumbnail: (Uri) -> Unit,
    onRemoveThumbnail: () -> Unit,
    onRetryLivePreview: () -> Unit,
    onSave: () -> Unit,
) {
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
                text = if (state.isEditing) "Edit bookmark" else "Add bookmark",
                style = BookmarkTheme.text.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedField(
                label = "URL",
                value = state.url,
                onValueChange = onUrlChange,
                placeholder = "https://",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (state.urlError != null) {
                Text(
                    text = state.urlError,
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            state.clipboardSuggestion?.let { suggestion ->
                ClipboardChip(
                    url = suggestion,
                    onAccept = onAcceptClipboard,
                    onDismiss = onDismissClipboard,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }

            // Nothing meaningful to preview until there is a URL to derive it from.
            if (state.url.isNotBlank()) {
                PreviewCard(
                    title = state.previewTitle,
                    description = state.description.ifBlank { null },
                    siteName = state.siteName,
                    url = state.url,
                    thumbnailFile = null,
                    fetching = state.fetching,
                    previewModel = state.previewThumbnailModel,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            OutlinedField(
                label = "Title",
                value = state.title,
                onValueChange = onTitleChange,
                maxLength = BookmarkLimits.TITLE_MAX,
                showCounter = true,
                modifier = Modifier.padding(top = 14.dp),
            )

            OutlinedField(
                label = "Description",
                value = state.description,
                onValueChange = onDescriptionChange,
                maxLength = BookmarkLimits.DESCRIPTION_MAX,
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThumbnailPicker(
                    state = state,
                    onSelectCandidate = onSelectThumbnailCandidate,
                    onPickLocal = onPickLocalThumbnail,
                    onRemove = onRemoveThumbnail,
                    onRetry = onRetryLivePreview,
                    modifier = Modifier.weight(1f),
                )
                CategoryPicker(
                    categories = state.categories,
                    selectedId = state.categoryId,
                    onSelect = onCategoryChange,
                    onCreate = onCreateCategory,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
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

@Composable
private fun ClipboardChip(
    url: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(Dimens.chipHeight)
            .clip(BookmarkShapes.chip)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "From clipboard · ${url.removePrefix("https://").removePrefix("http://").take(28)}…",
            style = BookmarkTheme.text.rowSubtitle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(onClick = onAccept),
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Dismiss clipboard suggestion",
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun OutlinedPicker(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDotColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = modifier
            .height(Dimens.secondaryButtonHeight)
            .clip(BookmarkShapes.field)
            .border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leadingDotColor != null) CategoryDot(leadingDotColor, size = 8.dp)
        Text(
            text = "$label ▾",
            style = BookmarkTheme.text.rowSubtitle,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Thumbnail dropdown (spec 5.2 item 5): choose another candidate the parser
 * found, pick an image from the device, remove it outright, or retry the
 * live fetch. There is no "selected" label to show -- unlike [CategoryPicker]
 * the button text is always "Thumbnail", matching the design.
 */
@Composable
private fun ThumbnailPicker(
    state: AddEditUiState,
    onSelectCandidate: (String) -> Unit,
    onPickLocal: (Uri) -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPickLocal) }

    // "if the parser found several" (spec 5.2 item 5) -- with only one candidate
    // there is nothing else to choose, so the section does not appear at all.
    val currentCandidate = when (val choice = state.thumbnailChoice) {
        is ThumbnailChoice.Candidate -> choice.url
        ThumbnailChoice.Auto -> state.imageCandidates.firstOrNull()
        ThumbnailChoice.Removed, is ThumbnailChoice.Local -> null
    }
    val otherCandidates = if (state.imageCandidates.size > 1) {
        state.imageCandidates.filterNot { it == currentCandidate }
    } else {
        emptyList()
    }

    Box(modifier = modifier) {
        OutlinedPicker(
            label = "Thumbnail",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (otherCandidates.isNotEmpty()) {
                Text(
                    text = "Choose another image found on page",
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                otherCandidates.forEach { candidate ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AsyncImage(
                                    model = candidate,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(BookmarkShapes.thumbnailMedium),
                                )
                                Text("Image ${state.imageCandidates.indexOf(candidate) + 1}")
                            }
                        },
                        onClick = {
                            onSelectCandidate(candidate)
                            expanded = false
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("Pick from device") },
                onClick = {
                    expanded = false
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                enabled = state.previewThumbnailModel != null,
                onClick = {
                    onRemove()
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Retry fetch") },
                onClick = {
                    onRetry()
                    expanded = false
                },
            )
        }
    }
}

/** Category dropdown with the inline "New category" the spec asks for (5.2). */
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val selected = categories.firstOrNull { it.id == selectedId }

    Box(modifier = modifier) {
        OutlinedPicker(
            label = selected?.name ?: "Category",
            onClick = { expanded = true },
            leadingDotColor = parseCategoryColor(selected?.colorHex),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CategoryDot(parseCategoryColor(category.colorHex), size = 8.dp)
                            Text(category.name)
                        }
                    },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
            if (creating) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    OutlinedField(
                        label = "New category",
                        value = newName,
                        onValueChange = { newName = it },
                        maxLength = Category.NAME_MAX_LENGTH,
                        imeAction = ImeAction.Done,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DropdownMenuItem(
                    text = { Text("Create \"${newName.trim()}\"") },
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreate(newName.trim())
                        newName = ""
                        creating = false
                        expanded = false
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("＋ New category") },
                    onClick = { creating = true },
                )
            }
        }
    }
}
