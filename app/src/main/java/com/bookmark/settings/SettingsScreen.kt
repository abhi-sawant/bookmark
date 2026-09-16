package com.bookmark.settings

import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import com.bookmark.core.ui.components.DesignSwitch
import com.bookmark.core.ui.components.MonoSectionHeader
import com.bookmark.core.ui.components.RowDivider
import com.bookmark.core.ui.components.ScreenHeader
import com.bookmark.core.ui.components.SegmentedControl
import com.bookmark.core.ui.components.SettingsGroup
import com.bookmark.core.ui.theme.BookmarkTheme

/**
 * Settings, in the order the design puts them: Backup first (with no backend,
 * export is the only disaster-recovery path and should not be buried), then
 * Previews, then Appearance.
 *
 * Every row is live: Appearance, the privacy toggle, refresh-all,
 * clear-thumbnails, and export/import (backed by [com.bookmark.backup.BackupRepository]).
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    summary: BackupSummary,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTrueBlackChange: (Boolean) -> Unit,
    onFetchPreviewsChange: (Boolean) -> Unit,
    onRefreshAll: () -> Unit,
    onClearThumbnails: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onSearch: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
            SettingsGroup(modifier = Modifier.padding(horizontal = 14.dp)) {
                SettingsRow(
                    leadingIcon = Icons.Outlined.FileDownload,
                    title = "Export backup",
                    subtitle = "${plural(summary.bookmarkCount, "bookmark")} · " +
                        "${plural(summary.categoryCount, "category", "categories")} · " +
                        "${Formatter.formatShortFileSize(context, summary.estimatedZipBytes)} zip",
                    enabled = true,
                    onClick = onExportBackup,
                )
                RowDivider()
                SettingsRow(
                    leadingIcon = Icons.Outlined.FileUpload,
                    title = "Import backup",
                    subtitle = "Merge or replace, with a preview first",
                    enabled = true,
                    onClick = onImportBackup,
                )
            }

            MonoSectionHeader("Previews")
            SettingsGroup(modifier = Modifier.padding(horizontal = 14.dp)) {
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
                    subtitle = "${plural(summary.eligibleRefreshCount, "bookmark")} eligible",
                    enabled = true,
                    onClick = onRefreshAll,
                )
                RowDivider()
                SettingsRow(
                    title = "Clear thumbnails",
                    subtitle = "${Formatter.formatShortFileSize(context, summary.thumbnailBytes)} · " +
                        "re-fetchable, bookmarks untouched",
                    enabled = true,
                    onClick = onClearThumbnails,
                )
            }

            MonoSectionHeader("Appearance")
            SettingsGroup(modifier = Modifier.padding(horizontal = 14.dp)) {
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

private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
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
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        }
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
