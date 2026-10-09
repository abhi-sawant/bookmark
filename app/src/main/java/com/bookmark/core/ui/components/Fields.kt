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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bookmark.core.ui.theme.BookmarkShapes
import com.bookmark.core.ui.theme.BookmarkTheme
import com.bookmark.core.ui.theme.accentFill
import com.bookmark.core.ui.theme.panel
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onImeAction: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .panel(BookmarkShapes.field)
            .then(
                if (focused) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, BookmarkShapes.field)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = BookmarkTheme.text.fieldLabel,
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                BookmarkTheme.colors.mutedLabel
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
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onAny = { onImeAction?.invoke() },
                    ),
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showCounter && maxLength != null) {
                Text(
                    text = "${value.length}/$maxLength",
                    style = BookmarkTheme.text.counter,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Filled action button: a solid accent rounded rectangle. */
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
            .then(
                if (enabled) {
                    Modifier
                        .accentFill(BookmarkShapes.primaryButton)
                } else {
                    Modifier
                        .clip(BookmarkShapes.primaryButton)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BookmarkTheme.text.buttonLabel,
            color = if (enabled) {
                BookmarkTheme.colors.onAccent
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** Outlined button, same metrics, ink label. */
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
            .panel(BookmarkShapes.primaryButton)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BookmarkTheme.text.buttonLabel,
            color = MaterialTheme.colorScheme.onSurface,
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
 * The 46x28 switch drawn in Settings. [interactive] is false when a parent row
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
    val track = CircleShape
    val colors = BookmarkTheme.colors
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(track)
            .background(if (checked) colors.accent else colors.hairline, track)
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
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else MaterialTheme.colorScheme.surface),
        )
    }
}
