package com.bookmark.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.TextActionButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme

@Composable
fun UpdateDialog(
    update: AvailableUpdate,
    onUpdate: () -> Unit,
    onNotNow: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        shape = BookmarkShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = "Update available",
                    style = BookmarkTheme.text.dialogTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Version ${update.version}",
                    style = BookmarkTheme.text.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        text = {
            Text(
                text = changelogText(update.changelog),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton(text = "Update", onClick = onUpdate, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextActionButton(text = "Skip this version", onClick = onSkip)
                    TextActionButton(text = "Not now", onClick = onNotNow)
                }
            }
        },
    )
}

/** Release notes are Markdown; show them as readable plain text. */
private fun changelogText(body: String): String {
    if (body.isBlank()) return "No release notes provided."
    return body.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^(\\s*)[-*]\\s+"), "$1• ")
            .replace("**", "")
            .replace("`", "")
    }
}
