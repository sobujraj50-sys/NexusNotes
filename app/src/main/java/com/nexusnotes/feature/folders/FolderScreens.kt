package com.nexusnotes.feature.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.InlineEmpty
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.feature.notes.NoteCard
import com.nexusnotes.navigation.NexusDestination
import com.nexusnotes.navigation.UNFILED_FOLDER_ID
import kotlinx.coroutines.launch

@Composable
fun FoldersScreen(
    onNavigate: (String) -> Unit,
    viewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val tree by viewModel.tree.collectAsStateWithLifecycle()
    val unfiledCount by viewModel.unfiledCount.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    NexusScreen(
        title = NexusDestination.Folders.title,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.createRoute()) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = FolderDialog.Create(parentId = null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New folder") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.fabClearance),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "library") { SectionHeader("Library") }
            item(key = "all") {
                NexusListRow(
                    icon = NexusIcons.Note,
                    title = "All notes",
                    supporting = "Every note outside Archive and Trash",
                    onClick = { onNavigate(NexusDestination.AllNotes.route) },
                )
            }
            item(key = "unfiled") {
                NexusListRow(
                    icon = NexusIcons.Folder,
                    title = "Unfiled",
                    supporting = "$unfiledCount note(s) not in a folder",
                    onClick = { onNavigate(NexusDestination.FolderDetail.createRoute(UNFILED_FOLDER_ID)) },
                )
            }
            item(key = "tags") {
                NexusListRow(
                    icon = NexusIcons.Label,
                    title = "Tags",
                    supporting = "Browse notes by tag",
                    onClick = { onNavigate(NexusDestination.Tags.route) },
                )
            }
            item(key = "mine") { SectionHeader("My folders", Modifier.padding(top = spacing.md)) }
            if (tree.isEmpty()) {
                item(key = "empty") { InlineEmpty(NexusIcons.Folder, "No folders yet. Tap New folder to create one.") }
            }
            items(tree, key = { it.folder.id }) { node ->
                FolderRow(
                    node = node,
                    onClick = { onNavigate(NexusDestination.FolderDetail.createRoute(node.folder.id)) },
                    onAction = { dialog = it },
                )
            }
        }
    }

    FolderDialogsHost(
        dialog = dialog,
        tree = tree,
        actions = viewModel,
        onDismiss = { dialog = null },
        onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
    )
}

@Composable
fun FolderDetailScreen(
    folderId: String,
    onOpenNote: (String) -> Unit,
    onNewNote: (folderId: String?) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val viewModel: FolderDetailViewModel = viewModel(factory = FolderDetailViewModel.factory(folderId))
    val actions: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tree by actions.tree.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val folder = state.folder

    NexusScreen(
        title = when {
            state.isUnfiled -> "Unfiled"
            folder != null -> folder.name
            else -> NexusDestination.FolderDetail.title
        },
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                onNavigate(NexusDestination.Search.createRoute(folderId = if (state.isUnfiled) UNFILED_FOLDER_ID else folderId))
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Search in this folder")
            }
            if (folder != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf(
                            "New sub-folder" to FolderDialog.Create(folder.id),
                            "Rename" to FolderDialog.Rename(folder),
                            "Color" to FolderDialog.Color(folder),
                            "Move" to FolderDialog.Move(folder),
                            "Delete" to FolderDialog.Delete(folder),
                        ).forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                menuOpen = false
                                dialog = action
                            })
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!state.notFound) {
                FloatingActionButton(
                    onClick = { onNewNote(if (state.isUnfiled) null else folderId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "New note in this folder") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notFound -> CenteredEmptyState(
                padding = padding,
                icon = NexusIcons.Folder,
                title = "Folder not found",
                message = "It may have been deleted. Its notes were moved to Unfiled.",
            )
            state.subfolders.isEmpty() && state.notes.isEmpty() -> CenteredEmptyState(
                padding = padding,
                icon = NexusIcons.Folder,
                title = if (state.isUnfiled) "No unfiled notes" else "This folder is empty",
                message = if (state.isUnfiled) "Every note is in a folder." else "Tap + to write a note here, or use ⋮ to add a sub-folder.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.fabClearance),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (state.subfolders.isNotEmpty()) {
                    item(key = "sub_header") { SectionHeader("Sub-folders") }
                    items(state.subfolders, key = { "f_" + it.folder.id }) { node ->
                        FolderRow(
                            node = node,
                            onClick = { onNavigate(NexusDestination.FolderDetail.createRoute(node.folder.id)) },
                            onAction = { dialog = it },
                        )
                    }
                }
                if (state.notes.isNotEmpty()) {
                    item(key = "notes_header") { SectionHeader("Notes", Modifier.padding(top = spacing.sm)) }
                    items(state.notes, key = { "n_" + it.id }) { note ->
                        NoteCard(note = note, onClick = { onOpenNote(note.id) })
                    }
                }
            }
        }
    }

    FolderDialogsHost(
        dialog = dialog,
        tree = tree,
        actions = actions,
        onDismiss = { dialog = null },
        onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
        onDeleted = { deletedId -> if (deletedId == folderId) onBack() },
    )
}
