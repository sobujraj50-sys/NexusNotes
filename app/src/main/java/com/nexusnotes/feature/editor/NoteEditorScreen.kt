package com.nexusnotes.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.MilestoneBadge
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.NoteLabelColors
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.NoteText
import java.text.DateFormat
import java.util.Date

const val EDITOR_TITLE_TAG = "editor_title"
const val EDITOR_BODY_TAG = "editor_body"
const val EDITOR_STATUS_TAG = "editor_status"

private val ColorNames = listOf("Violet", "Cyan", "Emerald", "Amber", "Rose", "Sky", "Grey")

@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Save immediately when the app goes to the background or the screen is left.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onBack()
                is EditorEvent.OpenNote -> onOpenNote(event.id)
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showColorDialog by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val note = state.note

    NexusScreen(
        title = "",
        onBack = {
            viewModel.flush()
            onBack()
        },
        actions = {
            if (!state.isReadOnly) {
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(NexusIcons.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(NexusIcons.Redo, contentDescription = "Redo")
                }
            }
            if (note != null && !state.isReadOnly) {
                IconButton(onClick = viewModel::togglePinned) {
                    Icon(
                        NexusIcons.Pin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (note.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (note.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (note != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.isReadOnly) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { menuOpen = false; viewModel.restore() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete forever") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menuOpen = false; showDeleteDialog = true },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Color label") },
                                leadingIcon = { Icon(NexusIcons.Palette, null) },
                                onClick = { menuOpen = false; showColorDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(NexusIcons.Copy, null) },
                                onClick = { menuOpen = false; viewModel.duplicate() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                                leadingIcon = {
                                    Icon(if (note.isArchived) NexusIcons.Unarchive else NexusIcons.Archive, null)
                                },
                                onClick = { menuOpen = false; viewModel.toggleArchived() },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Trash") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menuOpen = false; viewModel.moveToTrash() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Note info") },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; showInfoDialog = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@NexusScreen
        }
        EditorContent(
            state = state,
            padding = padding,
            onTitleChange = viewModel::onTitleChange,
            onBodyChange = viewModel::onBodyChange,
            onRestore = viewModel::restore,
        )
    }

    if (showColorDialog && note != null) {
        ColorLabelDialog(
            selected = note.colorIndex,
            onSelect = {
                viewModel.setColor(it)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }
    if (showInfoDialog && note != null) {
        NoteInfoDialog(state = state, onDismiss = { showInfoDialog = false })
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Delete forever?") },
            text = { Text("This note will be erased from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePermanently()
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onTitleChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onBodyChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onRestore: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val bodyText = state.body.text
    val words = remember(bodyText) { NoteText.wordCount(bodyText) }
    val chars = remember(bodyText) { NoteText.characterCount(bodyText) }
    val labelColor = state.note?.colorIndex?.let { NoteLabelColors.getOrNull(it) }
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        if (state.isReadOnly) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "This note is in Trash and can't be edited.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRestore) { Text("Restore") }
                }
            }
        }
        if (state.note?.isArchived == true && !state.isReadOnly) {
            Text(
                "Archived",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        TextField(
            value = state.title,
            onValueChange = onTitleChange,
            readOnly = state.isReadOnly,
            placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EDITOR_TITLE_TAG),
        )

        Row(
            Modifier.padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelColor != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(labelColor, CircleShape),
                )
                Spacer(Modifier.width(spacing.sm))
            }
            Text(
                text = "${state.saveState.label} · $words words · $chars characters",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.saveState == SaveState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(EDITOR_STATUS_TAG),
            )
        }
        HorizontalDivider(Modifier.padding(top = spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)

        TextField(
            value = state.body,
            onValueChange = onBodyChange,
            readOnly = state.isReadOnly,
            placeholder = { Text("Start writing…") },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(EDITOR_BODY_TAG),
        )

        if (!state.isReadOnly) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MilestoneBadge(3, Modifier.padding(horizontal = spacing.sm))
                listOf("B", "I", "U", "S", "H1", "H2", "•", "1.", "☐", "❝", "</>").forEach {
                    TextButton(onClick = {}, enabled = false) { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun ColorLabelDialog(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color label") },
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
    val name = index?.let { ColorNames.getOrElse(it) { "Color ${it + 1}" } } ?: "No color"
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

@Composable
private fun NoteInfoDialog(state: EditorUiState, onDismiss: () -> Unit) {
    val note = state.note ?: return
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val body = state.body.text
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, null) },
        title = { Text("Note info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                Text("Created: ${format.format(Date(note.createdAt))}")
                Text("Last edited: ${format.format(Date(note.updatedAt))}")
                note.deletedAt?.let { Text("Moved to Trash: ${format.format(Date(it))}") }
                Text("Words: ${NoteText.wordCount(body)}")
                Text("Characters: ${NoteText.characterCount(body)}")
                Text("Stored only on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
