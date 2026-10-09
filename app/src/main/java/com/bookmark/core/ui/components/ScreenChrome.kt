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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.bookmark.core.ui.theme.BookmarkShapes

/** Kept in sync with `macrobenchmark/.../BaselineProfileGenerator.kt` (M6). */
const val SEARCH_BUTTON_TEST_TAG = "search_button"

/**
 * The single-line header every top-level screen uses: lowercase title, live
 * count, then search. This replaces the large collapsing app
 * bar the written spec describes -- the design supersedes it deliberately.
 */
@Composable
fun ScreenHeader(
    title: String,
    count: Int?,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    /** What [count] counts, for TalkBack (e.g. "bookmarks", "categories"). */
    countItemName: String = title.lowercase(),
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
            text = title.lowercase(),
            style = BookmarkTheme.text.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = BookmarkTheme.text.screenCount,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(BookmarkShapes.countPill)
                    .background(BookmarkTheme.colors.hairline)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
                    .semantics { contentDescription = "$count $countItemName" },
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        SlateIconButton(
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
    }
}

/** The rounded-square button used for header actions: no fill until pressed or active. */
@Composable
fun SlateIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = BookmarkShapes.iconButton
    Box(
        modifier = modifier
            .size(Dimens.iconSlot)
            .clip(shape)
            .then(if (highlighted) Modifier.background(BookmarkTheme.colors.hover) else Modifier)
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
 * The three-destination bar: a flat strip docked to the bottom edge, one
 * hairline above it, with the active tab marked by a soft accent wash behind
 * its icon.
 */
@Composable
fun BookmarkBottomBar(
    destinations: List<BottomDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BookmarkTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.raisedSurface),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Edge-to-edge: the bar sits above the system navigation bar.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .then(if (selected) Modifier.background(colors.accentSoft) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (selected) colors.onAccentSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = destination.label,
                        style = BookmarkTheme.text.rowSubtitle.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
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
