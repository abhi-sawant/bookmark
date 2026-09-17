package com.bookmark.categories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bookmark.categories.data.DeleteStrategy
import com.bookmark.core.model.Category
import com.bookmark.core.model.CategoryWithCount
import com.bookmark.core.ui.components.CategoryDot
import com.bookmark.core.ui.components.OutlinedField
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.TextActionButton
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.CategorySwatchHex
import com.bookmark.core.ui.theme.CategorySwatches
import com.bookmark.core.ui.theme.Dimens
import com.bookmark.core.ui.theme.parseCategoryColor

private val ICON_KEYS = listOf("play", "edit", "star", "home")
private val ICON_GLYPHS: Map<String, ImageVector> = mapOf(
    "play" to Icons.Outlined.PlayCircle,
    "edit" to Icons.Outlined.Edit,
    "star" to Icons.Outlined.Star,
    "home" to Icons.Outlined.Home,
)

@Composable
fun CategoryEditDialog(
    existing: Category?,
    defaultColorHex: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String, iconKey: String?) -> Unit,
    /** Null when the category cannot be deleted (the seeded fallback, or the default). */
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: defaultColorHex) }
    var iconKey by remember { mutableStateOf(existing?.iconKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BookmarkShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (existing == null) "New category" else "Edit category",
                style = BookmarkTheme.text.dialogTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                OutlinedField(
                    label = "Name",
                    value = name,
                    onValueChange = { name = it },
                    maxLength = Category.NAME_MAX_LENGTH,
                    showCounter = true,
                    imeAction = ImeAction.Done,
                )

                Text(
                    text = "Colour",
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategorySwatches.forEachIndexed { index, swatch ->
                        val hex = CategorySwatchHex[index]
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { colorHex = hex }
                                .semantics {
                                    contentDescription =
                                        if (selected) "Colour $index, selected" else "Colour $index"
                                },
                        )
                    }
                }

                Text(
                    text = "Icon (optional)",
                    style = BookmarkTheme.text.siteLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ICON_KEYS.forEach { key ->
                        IconTile(
                            icon = ICON_GLYPHS.getValue(key),
                            description = key,
                            selected = iconKey == key,
                            onClick = { iconKey = if (iconKey == key) null else key },
                        )
                    }
                    IconTile(
                        icon = Icons.Outlined.Block,
                        description = "No icon",
                        selected = iconKey == null,
                        onClick = { iconKey = null },
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (existing == null) "Create" else "Save",
                onClick = { onConfirm(name, colorHex, iconKey) },
                enabled = name.isNotBlank(),
                height = Dimens.secondaryButtonHeight,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextActionButton(
                        text = "Delete",
                        onClick = onDelete,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextActionButton(text = "Cancel", onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun IconTile(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(BookmarkShapes.field)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.field)
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Deleting a non-empty category forces an explicit choice (spec 5.4). There is
 * no silent default here -- the bookmarks either move or go with it.
 */
@Composable
fun DeleteCategoryDialog(
    category: Category,
    bookmarkCount: Int,
    otherCategories: List<CategoryWithCount>,
    onDismiss: () -> Unit,
    onConfirm: (DeleteStrategy) -> Unit,
) {
    var moveSelected by remember { mutableStateOf(true) }
    var targetId by remember {
        mutableStateOf(
            otherCategories.firstOrNull { it.category.id == Category.UNSORTED_ID }?.category?.id
                ?: otherCategories.firstOrNull()?.category?.id
                ?: Category.UNSORTED_ID,
        )
    }
    var pickerOpen by remember { mutableStateOf(false) }
    val target = otherCategories.firstOrNull { it.category.id == targetId }?.category

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BookmarkShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Delete “${category.name}”?",
                style = BookmarkTheme.text.dialogTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                Text(
                    text = when (bookmarkCount) {
                        0 -> "This category is empty."
                        1 -> "1 bookmark is in this category. Choose what happens to it."
                        else -> "$bookmarkCount bookmarks are in this category. " +
                            "Choose what happens to them."
                    },
                    style = BookmarkTheme.text.rowTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (bookmarkCount > 0) {
                    OptionCard(
                        selected = moveSelected,
                        label = "Move bookmarks to",
                        onClick = { moveSelected = true },
                        modifier = Modifier.padding(top = 18.dp),
                    ) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .padding(start = 32.dp, top = 12.dp)
                                    .fillMaxWidth()
                                    .height(Dimens.secondaryButtonHeight)
                                    .clip(BookmarkShapes.field)
                                    .background(BookmarkTheme.colors.cardSurface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.field)
                                    .clickable { pickerOpen = true }
                                    .padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                CategoryDot(parseCategoryColor(target?.colorHex), size = 8.dp)
                                Text(
                                    text = target?.name ?: Category.UNSORTED_NAME,
                                    style = BookmarkTheme.text.rowSubtitle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "▾",
                                    style = BookmarkTheme.text.rowSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = pickerOpen,
                                onDismissRequest = { pickerOpen = false },
                            ) {
                                otherCategories.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text(entry.category.name) },
                                        onClick = {
                                            targetId = entry.category.id
                                            moveSelected = true
                                            pickerOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    OptionCard(
                        selected = !moveSelected,
                        label = if (bookmarkCount == 1) {
                            "Delete category and its bookmark"
                        } else {
                            "Delete category and its $bookmarkCount bookmarks"
                        },
                        onClick = { moveSelected = false },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Delete",
                onClick = {
                    onConfirm(
                        if (bookmarkCount == 0 || moveSelected) {
                            DeleteStrategy.MoveTo(targetId)
                        } else {
                            DeleteStrategy.DeleteBookmarks
                        },
                    )
                },
                height = Dimens.secondaryButtonHeight,
            )
        },
        dismissButton = { TextActionButton(text = "Cancel", onClick = onDismiss) },
    )
}

@Composable
private fun OptionCard(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BookmarkShapes.previewCard)
            .background(
                if (selected) BookmarkTheme.colors.selectedOptionContainer else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = BookmarkShapes.previewCard,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioDot(selected = selected)
            Text(
                text = label,
                style = BookmarkTheme.text.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        content?.invoke()
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .border(
                width = if (selected) 0.dp else 2.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}
