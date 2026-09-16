package com.bookmark.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.RowDivider
import com.bookmark.core.ui.components.SecondaryButton
import com.bookmark.core.ui.components.SegmentedControl
import com.bookmark.core.ui.components.SettingsGroup
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens

/**
 * The preview shown before an import commits (spec 5.6): counts from the
 * file, and a Merge / Replace everything choice. Modeled on
 * [com.bookmark.bookmarks.detail.DuplicateBookmarkSheet]'s shape -- title,
 * an info card, a button row -- the one existing precedent for this kind of
 * sheet in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(
    preview: ImportPreview,
    selectedMode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BookmarkShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Dimens.sheetHorizontalPadding)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Import backup",
                style = BookmarkTheme.text.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = preview.fileName,
                style = BookmarkTheme.text.monoCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            SettingsGroup(modifier = Modifier.padding(top = 16.dp)) {
                PreviewCountRow("Bookmarks in file", preview.bookmarksInFile)
                RowDivider()
                PreviewCountRow("New to this device", preview.newToDevice)
                RowDivider()
                PreviewCountRow("Already saved", preview.alreadySaved)
                RowDivider()
                PreviewCountRow("Categories", preview.categoriesInFile)
            }
            SegmentedControl(
                options = listOf("Merge", "Replace everything"),
                selectedIndex = ImportMode.entries.indexOf(selectedMode),
                onSelect = { index -> onModeChange(ImportMode.entries[index]) },
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Import", onClick = onImport, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewCountRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = BookmarkTheme.text.rowTitle, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = count.toString(),
            style = BookmarkTheme.text.monoCounter,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
