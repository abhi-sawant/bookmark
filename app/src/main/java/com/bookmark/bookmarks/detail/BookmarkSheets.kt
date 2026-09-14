package com.bookmark.bookmarks.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.MetadataState
import com.bookmark.core.ui.components.BookmarkThumbnail
import com.bookmark.core.ui.components.CategoryDot
import com.bookmark.core.ui.components.MonogramTile
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Long-press actions (spec 5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkContextSheet(
    bookmark: Bookmark,
    category: Category?,
    thumbnailFile: File?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onChangeCategory: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    /**
     * Null unless [bookmark] is [MetadataState.FALLBACK] -- that's the only
     * state whose retry affordance lives here rather than the detail sheet
     * (spec 8.1: FAILED gets a card on the detail sheet, FALLBACK is not an
     * error state at all and only offers retry from the overflow menu).
     */
    onRetryFetch: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BookmarkShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.sheetHorizontalPadding)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookmarkThumbnail(
                bookmark = bookmark,
                thumbnailFile = thumbnailFile,
                monogramFontSize = 17.sp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(BookmarkShapes.thumbnailMedium),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = BookmarkTheme.text.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(bookmark.siteName, category?.name).joinToString(" · "),
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 18.dp),
        ) {
            ContextRow(Icons.Outlined.Edit, "Edit", onEdit)
            ContextRow(Icons.Outlined.ViewList, "Change category", onChangeCategory)
            ContextRow(Icons.Outlined.ContentCopy, "Copy link", onCopyLink)
            ContextRow(Icons.Outlined.Share, "Share", onShare)
            ContextRow(
                icon = Icons.Outlined.Flag,
                label = if (bookmark.isPinned) "Unpin" else "Pin to top",
                onClick = onTogglePin,
            )
            if (onRetryFetch != null) {
                ContextRow(Icons.Outlined.Refresh, "Retry fetch", onRetryFetch)
            }
            ContextRow(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, style = BookmarkTheme.text.fieldValue, color = tint)
    }
}

/**
 * Detail sheet. The failure card only appears for a hard [MetadataState.FAILED];
 * PARTIAL and FALLBACK look like any other complete bookmark (spec 8.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailSheet(
    bookmark: Bookmark,
    category: Category?,
    thumbnailFile: File?,
    failureMessage: String?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onRetryFetch: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BookmarkShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.detailHeroHeight),
        ) {
            BookmarkThumbnail(
                bookmark = bookmark,
                thumbnailFile = thumbnailFile,
                modifier = Modifier.fillMaxSize(),
                monogramFontSize = 56.sp,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = Dimens.sheetHorizontalPadding,
                    end = Dimens.sheetHorizontalPadding,
                    top = 18.dp,
                    bottom = 22.dp,
                ),
        ) {
            Text(
                text = bookmark.title,
                style = BookmarkTheme.text.detailTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CategoryDot(parseCategoryColor(category?.colorHex), size = 8.dp)
                Text(
                    text = listOfNotNull(
                        bookmark.siteName,
                        category?.name,
                        "saved ${formatSavedDate(bookmark.createdAt)}",
                    ).joinToString(" · "),
                    style = BookmarkTheme.text.screenCount,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!bookmark.description.isNullOrBlank()) {
                Text(
                    text = bookmark.description,
                    style = BookmarkTheme.text.cardDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (bookmark.metadataState == MetadataState.FAILED && failureMessage != null) {
                FailureCard(
                    headline = failureMessage,
                    attempts = bookmark.fetchAttempts,
                    onRetry = onRetryFetch,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = "Open",
                    onClick = onOpen,
                    height = 50.dp,
                    modifier = Modifier.weight(1f),
                )
                IconAction(Icons.Outlined.Share, "Share", onShare)
                IconAction(Icons.Outlined.Edit, "Edit", onEdit)
                IconAction(
                    icon = Icons.Outlined.Flag,
                    description = if (bookmark.isPinned) "Unpin" else "Pin",
                    onClick = onTogglePin,
                    tint = if (bookmark.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(BookmarkShapes.primaryButton)
            .border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.primaryButton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FailureCard(
    headline: String,
    attempts: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BookmarkShapes.categoryRow)
            .background(BookmarkTheme.colors.failureContainer)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            text = headline,
            style = BookmarkTheme.text.rowSubtitle,
            color = BookmarkTheme.colors.onFailureContainer,
        )
        Text(
            text = "Preview not fetched after $attempts attempts. The bookmark itself is safe.",
            style = BookmarkTheme.text.rowSubtitle,
            color = BookmarkTheme.colors.onFailureContainerVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 11.dp)
                .height(34.dp)
                .clip(BookmarkShapes.smallButton)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Retry fetch",
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private fun formatSavedDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
