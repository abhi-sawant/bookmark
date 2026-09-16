package com.bookmark.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens

/**
 * The live preview card in the add and quick-save sheets. It renders exactly as
 * a list item will, so what the user approves is what they get.
 *
 * While [fetching], the thumbnail shimmers and the description becomes two
 * skeleton bars -- the image area only, never the text, which already shows its
 * fallbacks (spec 8.1, FETCHING).
 */
@Composable
fun PreviewCard(
    title: String,
    description: String?,
    siteName: String?,
    url: String,
    thumbnailPath: String?,
    fetching: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    /** A candidate URL or local `Uri` to preview before anything is saved -- see [ThumbnailSurface]. */
    previewModel: Any? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BookmarkShapes.previewCard,
        color = BookmarkTheme.colors.cardSurface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.previewThumbnail)
                    .clip(BookmarkShapes.thumbnailLarge),
            ) {
                ThumbnailSurface(
                    url = url,
                    thumbnailPath = thumbnailPath,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize(),
                    monogramFontSize = 26.sp,
                    fetching = fetching,
                    previewModel = previewModel,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = BookmarkTheme.text.cardTitle.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fetching) {
                    SkeletonLine(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    SkeletonLine(modifier = Modifier.fillMaxWidth(0.62f).padding(top = 6.dp))
                } else if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = BookmarkTheme.text.cardDescription,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!siteName.isNullOrBlank()) {
                    Text(
                        text = siteName,
                        style = BookmarkTheme.text.siteLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (fetching) 9.dp else 7.dp),
                    )
                }
            }
        }
    }
}
