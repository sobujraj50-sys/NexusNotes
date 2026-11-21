package com.nexusnotes.feature.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusListRow
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.PlannedFeatureCard
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination
import com.nexusnotes.navigation.UNFILED_FOLDER_ID

@Composable
fun FoldersScreen(onNavigate: (String) -> Unit) {
    val spacing = MaterialTheme.spacing
    NexusScreen(
        title = NexusDestination.Folders.title,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.route) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "system_header") { SectionHeader("Library") }
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
                    supporting = "Notes that are not in a folder",
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
            item(key = "my_header") { SectionHeader("My folders", Modifier.padding(top = spacing.md)) }
            item(key = "planned") {
                PlannedFeatureCard(
                    icon = NexusIcons.Folder,
                    title = "Folders",
                    description = "Create nested folders with colors. Moving a folder inside one of its own " +
                        "sub-folders will be blocked, so the folder tree can never loop.",
                    milestone = 4,
                )
            }
        }
    }
}

@Composable
fun FolderDetailScreen(
    folderId: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val isUnfiled = folderId == UNFILED_FOLDER_ID
    NexusScreen(
        title = if (isUnfiled) "Unfiled" else NexusDestination.FolderDetail.title,
        onBack = onBack,
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
            icon = NexusIcons.Folder,
            title = "No notes here",
            message = if (isUnfiled) {
                "Notes without a folder will be listed here once notes can be saved."
            } else {
                "This folder has no notes yet."
            },
            milestone = 2,
        )
    }
}
