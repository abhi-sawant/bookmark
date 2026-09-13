package com.bookmark.bookmarks.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.ui.components.PreviewCard
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.SecondaryButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The unique index on `url` rejects a re-save, so rather than an error the app
 * surfaces the bookmark that already exists (spec 14 Q1). Nothing is duplicated
 * and nothing is lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateBookmarkSheet(
    existing: Bookmark,
    category: Category?,
    thumbnailFile: File?,
    onDismiss: () -> Unit,
    onRefreshPreview: () -> Unit,
    onViewBookmark: () -> Unit,
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
                text = "Already saved in ${category?.name ?: Category.UNSORTED_NAME}",
                style = BookmarkTheme.text.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "You saved this link on ${formatDate(existing.createdAt)}. " +
                    "Nothing was duplicated.",
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 7.dp),
            )
            PreviewCard(
                title = existing.title,
                description = existing.description,
                siteName = listOfNotNull(existing.siteName, category?.name).joinToString(" · "),
                url = existing.url,
                thumbnailFile = thumbnailFile,
                fetching = false,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = "Refresh preview",
                    onClick = onRefreshPreview,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "View bookmark",
                    onClick = onViewBookmark,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(epochMillis))
