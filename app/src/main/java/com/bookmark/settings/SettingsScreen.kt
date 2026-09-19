package com.bookmark.settings

import android.os.Build
import android.text.format.DateUtils
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bookmark.account.data.AuthState
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
    authState: AuthState,
    lastSyncedAt: Long?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTrueBlackChange: (Boolean) -> Unit,
    onFetchPreviewsChange: (Boolean) -> Unit,
    onRefreshAll: () -> Unit,
    onClearThumbnails: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
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
            MonoSectionHeader("Sync", modifier = Modifier.padding(top = 8.dp))
            SettingsGroup(modifier = Modifier.padding(horizontal = 14.dp)) {
                when (authState) {
                    AuthState.SignedOut -> SettingsRow(
                        leadingIcon = Icons.Outlined.Sync,
                        title = "Sign in to sync across devices",
                        subtitle = "Bookmarks and categories stay in sync. Fully optional.",
                        enabled = true,
                        onClick = onSignIn,
                    )
                    is AuthState.SignedIn -> {
                        SettingsRow(
                            leadingIcon = Icons.Outlined.Sync,
                            title = authState.email,
                            subtitle = lastSyncedText(lastSyncedAt),
                            enabled = true,
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Sync now",
                            enabled = true,
                            onClick = onSyncNow,
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Sign out",
                            enabled = true,
                            onClick = onSignOut,
                        )
                    }
                }
            }

            MonoSectionHeader("Backup")
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
                    switchChecked = preferences.fetchPreviewsAutomatically,
                    onSwitchCheckedChange = onFetchPreviewsChange,
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
                    switchChecked = preferences.dynamicColor,
                    onSwitchCheckedChange = onDynamicColorChange,
                )
                RowDivider()
                SettingsRow(
                    title = "True black for OLED",
                    switchChecked = preferences.trueBlack,
                    onSwitchCheckedChange = onTrueBlackChange,
                )
            }
        }
    }
}

private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"

private fun lastSyncedText(lastSyncedAt: Long?): String {
    if (lastSyncedAt == null) return "Not synced yet"
    val relative = DateUtils.getRelativeTimeSpanString(
        lastSyncedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "Last synced $relative"
}

/**
 * A settings row is either a plain tappable row ([onClick]) or a switch row
 * ([switchChecked]/[onSwitchCheckedChange]) -- never both. The switch row owns
 * its own [androidx.compose.foundation.selection.toggleable] at the row level
 * so title+subtitle+switch merge into one accessible node and the full row,
 * not just the 52x32dp switch, is the touch target.
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    switchChecked: Boolean? = null,
    onSwitchCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    switchChecked != null && enabled -> Modifier.toggleable(
                        value = switchChecked,
                        role = Role.Switch,
                        onValueChange = { onSwitchCheckedChange?.invoke(it) },
                    )
                    onClick != null && enabled -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
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
        if (switchChecked != null && enabled) {
            DesignSwitch(
                checked = switchChecked,
                onCheckedChange = { onSwitchCheckedChange?.invoke(it) },
                interactive = false,
            )
        }
    }
}
