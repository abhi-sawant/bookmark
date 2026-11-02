package com.bookmark.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookmark.core.model.Bookmark
import com.bookmark.core.model.TrashPolicy
import com.bookmark.core.ui.components.SlateIconButton
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.panel

/** Deleted bookmarks, kept for [TrashPolicy.RETENTION_DAYS] days. Restore, delete for good, or empty. */
@Composable
fun TrashScreen(
    items: List<Bookmark>?,
    onBack: () -> Unit,
    onRestore: (Bookmark) -> Unit,
    onDeleteForever: (Bookmark) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlateIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "Trash",
                style = BookmarkTheme.text.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!items.isNullOrEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
            }
        }
        Text(
            text = "Deleted bookmarks stay here for ${TrashPolicy.RETENTION_DAYS} days, then are removed for good.",
            style = BookmarkTheme.text.rowSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        when {
            items == null -> Unit
            items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Trash is empty", style = BookmarkTheme.text.emptyTitle, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Bookmarks you delete show up here so you can bring them back.",
                    style = BookmarkTheme.text.fieldValue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { bookmark ->
                    TrashRow(
                        bookmark = bookmark,
                        daysLeft = TrashPolicy.daysLeft(bookmark.trashedAt ?: now, now),
                        onRestore = { onRestore(bookmark) },
                        onDeleteForever = { onDeleteForever(bookmark) },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("This permanently deletes ${items?.size ?: 0} bookmarks. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onEmptyTrash()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrashRow(
    bookmark: Bookmark,
    daysLeft: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .panel(RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = bookmark.title,
            style = BookmarkTheme.text.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                bookmark.siteName,
                if (daysLeft == 0) "removed soon" else "$daysLeft ${if (daysLeft == 1) "day" else "days"} left",
            ).joinToString(" · "),
            style = BookmarkTheme.text.siteLine,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Restore", BookmarkTheme.colors.accent, onRestore)
            TextAction("Delete forever", MaterialTheme.colorScheme.error, onDeleteForever)
        }
    }
}

@Composable
private fun TextAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = BookmarkTheme.text.rowTitle,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}
