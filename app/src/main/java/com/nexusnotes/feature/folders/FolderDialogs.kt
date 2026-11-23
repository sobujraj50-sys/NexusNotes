package com.nexusnotes.feature.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusnotes.core.design.components.ColorLabelDialog
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NameDialog
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.NoteLabelColors
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.FolderTree
import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.FolderNode

sealed interface FolderDialog {
    data class Create(val parentId: String?) : FolderDialog
    data class Rename(val folder: Folder) : FolderDialog
    data class Color(val folder: Folder) : FolderDialog
    data class Move(val folder: Folder) : FolderDialog
    data class Delete(val folder: Folder) : FolderDialog
}

/** Shows whichever folder dialog is active and runs its action. */
@Composable
fun FolderDialogsHost(
    dialog: FolderDialog?,
    tree: List<FolderNode>,
    actions: FoldersViewModel,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onDeleted: (String) -> Unit = {},
) {
    when (dialog) {
        null -> Unit
        is FolderDialog.Create -> {
            var error by remember(dialog) { mutableStateOf<String?>(null) }
            NameDialog(
                title = if (dialog.parentId == null) "New folder" else "New sub-folder",
                initial = "",
                confirmLabel = "Create",
                error = error,
                onConfirm = { name -> actions.create(name, dialog.parentId) { err -> if (err == null) onDismiss() else error = err } },
                onDismiss = onDismiss,
            )
        }
        is FolderDialog.Rename -> {
            var error by remember(dialog) { mutableStateOf<String?>(null) }
            NameDialog(
                title = "Rename folder",
                initial = dialog.folder.name,
                confirmLabel = "Rename",
                error = error,
                onConfirm = { name -> actions.rename(dialog.folder.id, name) { err -> if (err == null) onDismiss() else error = err } },
                onDismiss = onDismiss,
            )
        }
        is FolderDialog.Color -> ColorLabelDialog(
            selected = dialog.folder.colorIndex,
            title = "Folder color",
            onSelect = {
                actions.setColor(dialog.folder.id, it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is FolderDialog.Move -> FolderPickerDialog(
            title = "Move “${dialog.folder.name}” to",
            tree = tree,
            selectedId = dialog.folder.parentId,
            noneLabel = "Top level",
            isEnabled = { targetId ->
                targetId != dialog.folder.parentId &&
                    FolderTree.checkMove(tree.map { it.folder }, dialog.folder.id, targetId) == FolderTree.MoveCheck.Ok
            },
            onSelect = { targetId ->
                actions.move(dialog.folder.id, targetId) { err -> if (err != null) onMessage(err) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is FolderDialog.Delete -> {
            val subCount = FolderTree.subtreeIds(tree.map { it.folder }, dialog.folder.id).size - 1
            ConfirmDialog(
                title = "Delete “${dialog.folder.name}”?",
                message = (if (subCount > 0) "Its $subCount sub-folder(s) will be deleted too. " else "") +
                    "Notes inside are kept and moved to Unfiled.",
                confirmLabel = "Delete folder",
                onConfirm = {
                    actions.delete(dialog.folder.id)
                    onDeleted(dialog.folder.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Pick a folder from the tree. [noneLabel] is the first option (Unfiled / Top level, id = null).
 * Options for which [isEnabled] is false are shown greyed out.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    tree: List<FolderNode>,
    selectedId: String?,
    noneLabel: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    isEnabled: (String?) -> Boolean = { true },
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "none") {
                    PickerRow(noneLabel, depth = 0, colorIndex = null, selected = selectedId == null, enabled = isEnabled(null)) {
                        onSelect(null)
                    }
                }
                items(tree, key = { it.folder.id }) { node ->
                    PickerRow(
                        node.folder.name,
                        node.depth,
                        node.folder.colorIndex,
                        selected = selectedId == node.folder.id,
                        enabled = isEnabled(node.folder.id),
                    ) { onSelect(node.folder.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerRow(name: String, depth: Int, colorIndex: Int?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((depth * 16).dp))
        Icon(
            NexusIcons.Folder,
            contentDescription = null,
            tint = folderTint(colorIndex).copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
        Text(
            name,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun folderTint(colorIndex: Int?) =
    colorIndex?.let { NoteLabelColors.getOrNull(it) } ?: MaterialTheme.colorScheme.primary

/** One folder in a list, with its own menu of actions. */
@Composable
fun FolderRow(
    node: FolderNode,
    onClick: () -> Unit,
    onAction: (FolderDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 16).dp),
    ) {
        Row(
            Modifier.padding(start = spacing.lg, top = spacing.xs, bottom = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(NexusIcons.Folder, contentDescription = null, tint = folderTint(node.folder.colorIndex))
            Text(
                node.folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                node.noteCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options for ${node.folder.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val f = node.folder
                    listOf(
                        "New sub-folder" to FolderDialog.Create(f.id),
                        "Rename" to FolderDialog.Rename(f),
                        "Color" to FolderDialog.Color(f),
                        "Move" to FolderDialog.Move(f),
                        "Delete" to FolderDialog.Delete(f),
                    ).forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menuOpen = false
                            onAction(action)
                        })
                    }
                }
            }
        }
    }
}
