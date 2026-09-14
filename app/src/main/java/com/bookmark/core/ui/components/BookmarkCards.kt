package com.bookmark.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
            // A real thumbnail sizes to its own aspect ratio, which is what
            // makes the masonry stagger meaningful. Only a fallback tile, which
            // has no intrinsic ratio, borrows the domain-hash height.
            val ratio = bookmark.thumbnailAspectRatio()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (ratio != null) {
                            Modifier.aspectRatio(ratio)
                        } else {
                            Modifier.height(fallbackTileHeightDp(bookmark.url).dp)
                        },
                    ),
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
    monogramFontSize: TextUnit = 34.sp,
) {
    ThumbnailSurface(
        url = bookmark.url,
        thumbnailFile = thumbnailFile,
        accentColor = bookmark.accentColor?.let { Color(it) },
        modifier = modifier,
        monogramFontSize = monogramFontSize,
    )
}

/**
 * The single thumbnail decision point: the stored image, a live remote
 * candidate, a shimmer while a fetch is in flight, or the generated tile.
 * There is no fifth "couldn't load" state -- that is the whole point of the
 * fallback chain (design principle 2).
 *
 * Taking the URL rather than a [Bookmark] lets the add and quick-save sheets,
 * which are previewing something not yet saved, share it. Before M3 the same
 * three-way branch was written out in three places and had already drifted.
 *
 * [previewModel] is the pre-save live preview's only option: a candidate the
 * engine just found (a remote URL) or an image the user just picked from the
 * device (a local `Uri`) has no bookmark-owned file yet -- that only exists
 * once [ThumbnailPipeline][com.bookmark.metadata.image.ThumbnailPipeline]
 * stores it against a real bookmark id -- so it is loaded directly by Coil
 * for the preview only, never persisted from here. Coil's `AsyncImage`
 * accepts either shape as its `model`.
 */
@Composable
fun ThumbnailSurface(
    url: String,
    thumbnailFile: File?,
    accentColor: Color?,
    modifier: Modifier = Modifier,
    monogramFontSize: TextUnit = 34.sp,
    fetching: Boolean = false,
    previewModel: Any? = null,
) {
    when {
        fetching -> ShimmerBox(modifier = modifier)

        // Deliberately not File.exists(): this runs on the composition thread for
        // every visible card. thumbnailPath is only ever set once the file is on
        // disk, and Coil degrades to the background tint if it has since gone.
        thumbnailFile != null -> AsyncImage(
            model = thumbnailFile,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .background(accentColor ?: MaterialTheme.colorScheme.surfaceVariant)
                .clearAndSetSemantics { },
        )

        previewModel != null -> AsyncImage(
            model = previewModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .background(accentColor ?: MaterialTheme.colorScheme.surfaceVariant)
                .clearAndSetSemantics { },
        )

        else -> MonogramTile(
            url = url,
            modifier = modifier,
            fontSize = monogramFontSize,
            accentColor = accentColor,
        )
    }
}
