package com.nexusnotes.feature.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.navigation.NexusDestination

@Composable
fun AllNotesScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    NexusScreen(
        title = NexusDestination.AllNotes.title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.route) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
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
        CenteredEmptyState(
            padding = padding,
            icon = NexusIcons.Note,
            title = "No notes yet",
            message = "Saved notes, sorting and color labels arrive with the database in Milestone 2.",
            milestone = 2,
        )
    }
}

@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    NexusScreen(title = NexusDestination.Favorites.title, onBack = onBack) { padding ->
        CenteredEmptyState(
            padding = padding,
            icon = Icons.Filled.Star,
            title = "No favorites",
            message = "Star any note and it will be listed here.",
            milestone = 2,
        )
    }
}

@Composable
fun ArchiveScreen(onBack: () -> Unit) {
    NexusScreen(title = NexusDestination.Archive.title, onBack = onBack) { padding ->
        CenteredEmptyState(
            padding = padding,
            icon = NexusIcons.Archive,
            title = "Archive is empty",
            message = "Archived notes are hidden from Home and All Notes but stay searchable and are never deleted.",
            milestone = 2,
        )
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    NexusScreen(title = NexusDestination.Trash.title, onBack = onBack) { padding ->
        CenteredEmptyState(
            padding = padding,
            icon = Icons.Filled.Delete,
            title = "Trash is empty",
            message = "Deleted notes stay here until you restore them or delete them permanently. " +
                "Permanent deletion always asks for confirmation.",
            milestone = 2,
        )
    }
}
