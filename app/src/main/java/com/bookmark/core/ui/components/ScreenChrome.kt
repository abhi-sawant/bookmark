package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens

/**
 * The single-line header the 2a design uses on every top-level screen: title,
 * live count, then search and overflow. This replaces the large collapsing app
 * bar the written spec describes -- the design supersedes it deliberately.
 */
@Composable
fun ScreenHeader(
    title: String,
    count: Int?,
    onSearch: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    overflowHighlighted: Boolean = false,
    /** Rendered anchored to the overflow button, so menus open beside it. */
    overflowMenu: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.screenHeaderHeight)
            .padding(start = Dimens.headerStartPadding, end = Dimens.headerEndPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = BookmarkTheme.text.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = BookmarkTheme.text.screenCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { },
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onSearch,
            enabled = searchEnabled,
            modifier = Modifier.size(Dimens.iconSlot),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search bookmarks",
                tint = if (searchEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                },
            )
        }
        Box(
            modifier = Modifier
                .size(Dimens.iconSlot)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (overflowHighlighted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onOverflow, modifier = Modifier.size(Dimens.iconSlot)) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = if (overflowHighlighted) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            overflowMenu()
        }
    }
}

/** One of the three bottom-bar destinations. */
data class BottomDestination(
    val label: String,
    val icon: ImageVector,
)

/**
 * The three-destination bar the design uses in place of FAB-only navigation.
 * Hand-built rather than M3 `NavigationBar` so the 64x32 selection pill, the
 * hairline top border and the exact padding match the mockups.
 */
@Composable
fun BookmarkBottomBar(
    destinations: List<BottomDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Edge-to-edge: the bar sits under the system navigation bar, so
                // it has to inset its own content rather than let it be covered.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 10.dp, bottom = 14.dp),
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = destination.label,
                        style = BookmarkTheme.text.rowSubtitle,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
