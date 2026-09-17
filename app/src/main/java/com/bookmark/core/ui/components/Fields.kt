package com.bookmark.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.Dimens

/**
 * The outlined field the design uses everywhere: a small tinted label inside
 * the box, the value beneath it, and an optional mono character counter.
 * Hand-built rather than `OutlinedTextField`, whose floating label and 56dp
 * minimum height do not match.
 */
@Composable
fun OutlinedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    showCounter: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BookmarkShapes.field)
            .border(1.dp, borderColor, BookmarkShapes.field)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = BookmarkTheme.text.fieldLabel,
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                BookmarkTheme.colors.monoLabel
            },
        )
        Row(
            modifier = Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = BookmarkTheme.text.fieldValue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        onValueChange(if (maxLength != null) next.take(maxLength) else next)
                    },
                    textStyle = BookmarkTheme.text.fieldValue.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = singleLine,
                    minLines = minLines,
                    interactionSource = interactionSource,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showCounter && maxLength != null) {
                Text(
                    text = "${value.length}/$maxLength",
                    style = BookmarkTheme.text.monoCounter,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Filled action button: 52dp tall, 16dp radius. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Dimens.primaryButtonHeight,
) {
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(BookmarkShapes.primaryButton)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BookmarkTheme.text.buttonLabel,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** Outlined action button, same metrics, primary-coloured label. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.primaryButtonHeight,
) {
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(BookmarkShapes.primaryButton)
            .border(1.dp, MaterialTheme.colorScheme.outline, BookmarkShapes.primaryButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BookmarkTheme.text.buttonLabel,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Text-only dialog action ("Cancel"). */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Box(
        modifier = modifier
            .heightIn(min = Dimens.secondaryButtonHeight)
            .clip(BookmarkShapes.categoryRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BookmarkTheme.text.buttonLabel,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
        )
    }
}

/**
 * The 52x32 switch drawn in Settings. [interactive] is false when a parent row
 * (e.g. `SettingsRow`) already owns the toggle semantics and touch target, so
 * this doesn't end up as a second, disconnected accessibility node.
 */
@Composable
fun DesignSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .then(
                if (interactive) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .padding(horizontal = 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}
