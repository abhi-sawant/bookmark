package com.bookmark.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import com.bookmark.core.ui.components.DesignSwitch
import com.bookmark.core.ui.components.MonoSectionHeader
import com.bookmark.core.ui.components.ScreenHeader
import com.bookmark.core.ui.components.SegmentedControl
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme

/**
 * Settings, in the order the design puts them: Backup first (with no backend,
 * export is the only disaster-recovery path and should not be buried), then
 * Previews, then Appearance.
 *
 * M0-M2 wires the Appearance controls and the privacy toggle. Backup, refresh
 * and thumbnail clearing arrive with M5/M3 and are shown disabled rather than
 * hidden, so the shape of the screen is already right.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTrueBlackChange: (Boolean) -> Unit,
    onFetchPreviewsChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Settings",
            count = null,
            onSearch = onSearch,
            onOverflow = onOverflow,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
        ) {
            MonoSectionHeader("Backup", modifier = Modifier.padding(top = 8.dp))
            SettingsGroup {
                SettingsRow(
                    title = "Export backup",
                    subtitle = "Arrives in M5 — a single .zip via the system file picker",
                    enabled = false,
                )
                RowDivider()
                SettingsRow(
                    title = "Import backup",
                    subtitle = "Merge or replace, with a preview first",
                    enabled = false,
                )
            }

            MonoSectionHeader("Previews")
            SettingsGroup {
                SettingsRow(
                    title = "Fetch link previews automatically",
                    subtitle = "Requests go straight to the saved site, which sees your IP. " +
                        "No servers of ours are involved.",
                    trailing = {
                        DesignSwitch(
                            checked = preferences.fetchPreviewsAutomatically,
                            onCheckedChange = onFetchPreviewsChange,
                        )
                    },
                )
                RowDivider()
                SettingsRow(
                    title = "Refresh all metadata",
                    subtitle = "Arrives with the metadata engine in M3",
                    enabled = false,
                )
                RowDivider()
                SettingsRow(
                    title = "Clear thumbnails",
                    subtitle = "Re-fetchable, bookmarks untouched",
                    enabled = false,
                )
            }

            MonoSectionHeader("Appearance")
            SettingsGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "Theme",
                        style = BookmarkTheme.text.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 11.dp),
                    )
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = ThemeMode.entries.indexOf(preferences.themeMode),
                        onSelect = { index -> onThemeModeChange(ThemeMode.entries[index]) },
                    )
                }
                RowDivider()
                SettingsRow(
                    title = "Dynamic colour",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Follow the wallpaper palette"
                    } else {
                        "Needs Android 12 or newer"
                    },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    trailing = {
                        DesignSwitch(
                            checked = preferences.dynamicColor,
                            onCheckedChange = onDynamicColorChange,
                        )
                    },
                )
                RowDivider()
                SettingsRow(
                    title = "True black for OLED",
                    trailing = {
                        DesignSwitch(
                            checked = preferences.trueBlack,
                            onCheckedChange = onTrueBlackChange,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = BookmarkShapes.settingsGroup,
        color = BookmarkTheme.colors.cardSurface,
        shadowElevation = 1.dp,
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = BookmarkTheme.text.rowTitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = BookmarkTheme.text.rowSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (trailing != null && enabled) trailing()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
