package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.glass

/** Kept in sync with `macrobenchmark/.../BaselineProfileGenerator.kt` (M6). */
const val SEARCH_BUTTON_TEST_TAG = "search_button"

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
    /** What [count] counts, for TalkBack (e.g. "bookmarks", "categories"). */
    countItemName: String = title.lowercase(),
    /** Rendered anchored to the overflow button, so menus open beside it. */
    overflowMenu: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.screenHeaderHeight)
            .padding(start = Dimens.headerStartPadding, end = Dimens.headerEndPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                modifier = Modifier
                    .glass(RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics { contentDescription = "$count $countItemName" },
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        GlassIconButton(
            onClick = onSearch,
            enabled = searchEnabled,
            modifier = Modifier.testTag(SEARCH_BUTTON_TEST_TAG),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search bookmarks",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (searchEnabled) 1f else 0.35f),
            )
        }
        Box(contentAlignment = Alignment.Center) {
            GlassIconButton(onClick = onOverflow, highlighted = overflowHighlighted) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            overflowMenu()
        }
    }
}

/** The rounded-square frosted button used for header actions. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(Dimens.iconSlot)
            .glass(shape)
            .then(if (highlighted) Modifier.background(BookmarkTheme.colors.glassHover) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** One of the three bottom-bar destinations. */
data class BottomDestination(
    val label: String,
    val icon: ImageVector,
)

/**
 * The three-destination bar: a floating frosted capsule rather than a docked
 * strip, with the active tab lifted onto a violet glow.
 */
@Composable
fun BookmarkBottomBar(
    destinations: List<BottomDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Edge-to-edge: the bar floats above the system navigation bar.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                val shape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .then(
                            if (selected) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(
                                            BookmarkTheme.colors.accentStart.copy(alpha = 0.34f),
                                            BookmarkTheme.colors.accentStart.copy(alpha = 0.10f),
                                        ),
                                    ),
                                    shape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = destination.label,
                        style = BookmarkTheme.text.rowSubtitle.copy(fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
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
