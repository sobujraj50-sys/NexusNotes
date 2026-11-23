package com.nexusnotes.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.theme.NoteLabelColors
import com.nexusnotes.core.design.theme.spacing

val LabelColorNames = listOf("Violet", "Cyan", "Emerald", "Amber", "Rose", "Sky", "Grey")

/** Pick a label color (or none) for a note or folder. */
@Composable
fun ColorLabelDialog(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Color label",
) {
    val spacing = MaterialTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                val options: List<Int?> = listOf<Int?>(null) + NoteLabelColors.indices.toList()
                options.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        row.forEach { index -> ColorSwatch(index, selected == index) { onSelect(index) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ColorSwatch(index: Int?, isSelected: Boolean, onClick: () -> Unit) {
    val color = index?.let { NoteLabelColors[it] }
    val name = index?.let { LabelColorNames.getOrElse(it) { "Color ${it + 1}" } } ?: "No color"
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .padding(4.dp)
            .background(color ?: Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isSelected) "$name, selected" else name },
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Single text field dialog used to create or rename folders and tags. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "Name",
    error: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Simple yes/no confirmation for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
