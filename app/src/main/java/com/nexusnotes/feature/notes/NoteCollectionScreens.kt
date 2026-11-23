package com.nexusnotes.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.model.NoteSort
import com.nexusnotes.navigation.NexusDestination

@Composable
fun AllNotesScreen(
    onOpenNote: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    title: String = NexusDestination.AllNotes.title,
) {
    val viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.factory(NoteListType.ALL))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    NexusScreen(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.createRoute()) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(NexusIcons.Sort, contentDescription = "Sort notes")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    NoteSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            trailingIcon = {
                                if (sort == state.sort) Icon(Icons.Filled.Check, contentDescription = "Selected")
                            },
                            onClick = {
                                sortMenuOpen = false
                                viewModel.setSort(sort)
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(NexusDestination.NoteEditor.createRoute()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        NoteListContent(
            state = state,
            padding = padding,
            onOpenNote = onOpenNote,
            emptyIcon = NexusIcons.Note,
            emptyTitle = "No notes yet",
            emptyMessage = "Tap + to write your first note. Notes are saved automatically on this phone.",
            hint = "Pinned notes are listed first. Sorted by ${state.sort.label.lowercase()}.",
        )
    }
}

@Composable
fun FavoritesScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.factory(NoteListType.FAVORITES))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NexusScreen(title = NexusDestination.Favorites.title, onBack = onBack) { padding ->
        NoteListContent(
            state = state,
            padding = padding,
            onOpenNote = onOpenNote,
            emptyIcon = Icons.Filled.Star,
            emptyTitle = "No favorites",
            emptyMessage = "Open a note and tap the star to add it here.",
        )
    }
}

@Composable
fun ArchiveScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.factory(NoteListType.ARCHIVE))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NexusScreen(title = NexusDestination.Archive.title, onBack = onBack) { padding ->
        NoteListContent(
            state = state,
            padding = padding,
            onOpenNote = onOpenNote,
            emptyIcon = NexusIcons.Archive,
            emptyTitle = "Archive is empty",
            emptyMessage = "Archived notes are hidden from Home and All Notes but are never deleted.",
        )
    }
}

@Composable
fun TrashScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: NoteListViewModel = viewModel(factory = NoteListViewModel.factory(NoteListType.TRASH))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    NexusScreen(
        title = NexusDestination.Trash.title,
        onBack = onBack,
        actions = {
            if (state.notes.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Empty") }
            }
        },
    ) { padding ->
        NoteListContent(
            state = state,
            padding = padding,
            onOpenNote = onOpenNote,
            emptyIcon = Icons.Filled.Delete,
            emptyTitle = "Trash is empty",
            emptyMessage = "Deleted notes stay here until you restore them or delete them forever.",
            hint = "Open a note to restore it or delete it forever.",
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Empty Trash?") },
            text = { Text("${state.notes.size} note(s) will be erased from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.emptyTrash()
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteListContent(
    state: NoteListUiState,
    padding: PaddingValues,
    onOpenNote: (String) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    hint: String? = null,
) {
    val spacing = MaterialTheme.spacing
    when {
        state.isLoading -> Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.notes.isEmpty() -> CenteredEmptyState(
            padding = padding,
            icon = emptyIcon,
            title = emptyTitle,
            message = emptyMessage,
        )

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.sm,
                bottom = spacing.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (hint != null) {
                item(key = "hint") {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.notes, key = { it.id }) { note ->
                NoteCard(note = note, onClick = { onOpenNote(note.id) })
            }
        }
    }
}
