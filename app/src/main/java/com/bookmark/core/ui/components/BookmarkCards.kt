package com.bookmark.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.Category
import com.bookmark.core.model.MetadataState
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor
import java.io.File

/**
 * Image-forward grid card. Height is driven by the thumbnail, so the staggered
 * grid falls out naturally; fallback tiles borrow a deterministic height so a
 * grid of them still staggers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkGridCard(
    bookmark: Bookmark,
    category: Category?,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = parseCategoryColor(category?.colorHex)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(BookmarkShapes.card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Open ${bookmark.title}",
                onLongClickLabel = "Bookmark actions",
            ),
        shape = BookmarkShapes.card,
        color = BookmarkTheme.colors.cardSurface,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fallbackTileHeightDp(bookmark.url).dp),
            ) {
                BookmarkThumbnail(
                    bookmark = bookmark,
                    thumbnailFile = thumbnailFile,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 13.dp)) {
                Text(
                    text = bookmark.title,
                    style = BookmarkTheme.text.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // An absent description simply collapses the layout (spec 8.4).
                if (!bookmark.description.isNullOrBlank()) {
                    Text(
                        text = bookmark.description,
                        style = BookmarkTheme.text.cardDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryDot(categoryColor)
                    Text(
                        text = bookmark.siteName.orEmpty(),
                        style = BookmarkTheme.text.siteLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bookmark.metadataState == MetadataState.PENDING) {
                    PendingPill(modifier = Modifier.padding(top = 9.dp))
                }
            }
        }
    }
}

/** Compact row: 64dp thumbnail, title, `site - Category`, trailing pin. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkListRow(
    bookmark: Bookmark,
    category: Category?,
    thumbnailFile: File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = parseCategoryColor(category?.colorHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Open ${bookmark.title}",
                onLongClickLabel = "Bookmark actions",
            )
            .padding(
                horizontal = Dimens.listRowHorizontalPadding,
                vertical = Dimens.listRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookmarkThumbnail(
            bookmark = bookmark,
            thumbnailFile = thumbnailFile,
            monogramFontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp),
            modifier = Modifier
                .size(Dimens.listThumbnail)
                .clip(BookmarkShapes.thumbnailLarge),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.title,
                style = BookmarkTheme.text.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryDot(categoryColor)
                Text(
                    text = listOfNotNull(bookmark.siteName, category?.name).joinToString(" · "),
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Yields width to the pill rather than squeezing it into a wrap.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (bookmark.metadataState == MetadataState.PENDING) {
                    PendingPill()
                }
            }
        }
        if (bookmark.isPinned) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The saved thumbnail if one was downloaded, otherwise the generated tile.
 * There is no third "couldn't load" state -- that is the whole point of the
 * fallback chain (design principle 2).
 */
@Composable
fun BookmarkThumbnail(
    bookmark: Bookmark,
    thumbnailFile: File?,
    modifier: Modifier = Modifier,
    monogramFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(
        34f,
        androidx.compose.ui.unit.TextUnitType.Sp,
    ),
) {
    val accent = bookmark.accentColor?.let { Color(it) }
    if (thumbnailFile != null && thumbnailFile.exists()) {
        AsyncImage(
            model = thumbnailFile,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .background(accent ?: MaterialTheme.colorScheme.surfaceVariant)
                .clearAndSetSemantics { },
        )
    } else {
        MonogramTile(
            url = bookmark.url,
            modifier = modifier,
            fontSize = monogramFontSize,
            accentColor = accent,
        )
    }
}
