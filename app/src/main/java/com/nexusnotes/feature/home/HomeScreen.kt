package com.nexusnotes.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.common.greetingForHour
import com.nexusnotes.core.design.components.InlineEmpty
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SearchLauncher
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.model.PromptCategory
import com.nexusnotes.feature.notes.NoteCard
import com.nexusnotes.navigation.NexusDestination
import java.time.LocalTime

const val HOME_FAB_TAG = "home_fab_quick_note"

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenPrompts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openNote: (String) -> Unit = { id -> onNavigate(NexusDestination.NoteEditor.createRoute(id)) }
    val greeting = remember { greetingForHour(LocalTime.now().hour) }

    NexusScreen(
        title = NexusDestination.Home.title,
        modifier = modifier,
        actions = {
            IconButton(onClick = { onNavigate(NexusDestination.Search.route) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(NexusDestination.NoteEditor.createRoute()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Quick note") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(HOME_FAB_TAG),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screen,
                end = spacing.screen,
                top = spacing.sm,
                bottom = spacing.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "greeting") {
                Column {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your private, offline notebook.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "search") {
                SearchLauncher(
                    placeholder = "Search notes and prompts",
                    onClick = { onNavigate(NexusDestination.Search.route) },
                )
            }
            item(key = "quick_actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    QuickAction(
                        icon = NexusIcons.Note,
                        label = "New note",
                        onClick = { onNavigate(NexusDestination.NoteEditor.createRoute()) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon = NexusIcons.Sparkle,
                        label = "New prompt",
                        onClick = { onNavigate(NexusDestination.PromptEditor.createRoute()) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon = Icons.Filled.Lock,
                        label = "Vault",
                        onClick = { onNavigate(NexusDestination.Vault.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "prompt_card") { PromptLibraryCard(onClick = onOpenPrompts) }
            if (!state.isLoading && state.pinned.isNotEmpty()) {
                item(key = "pinned_header") { SectionHeader("Pinned") }
                items(state.pinned, key = { "pinned_" + it.id }) { note ->
                    NoteCard(note = note, onClick = { openNote(note.id) })
                }
            }
            item(key = "recent_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Recent notes", Modifier.weight(1f))
                    TextButton(onClick = { onNavigate(NexusDestination.AllNotes.route) }) { Text("See all") }
                }
            }
            if (!state.isLoading && state.recent.isEmpty() && state.pinned.isEmpty()) {
                item(key = "recent_empty") {
                    InlineEmpty(NexusIcons.History, "No notes yet. Tap Quick note to write your first one.")
                }
            }
            items(state.recent, key = { "recent_" + it.id }) { note ->
                NoteCard(note = note, onClick = { openNote(note.id) })
            }
            if (!state.isLoading && state.favorites.isNotEmpty()) {
                item(key = "favorites_header") { SectionHeader("Favorites") }
                items(state.favorites, key = { "fav_" + it.id }) { note ->
                    NoteCard(note = note, onClick = { openNote(note.id) })
                }
            }
            item(key = "collections") {
                DashboardSection("Collections") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        CollectionChip("All notes", NexusIcons.Note) { onNavigate(NexusDestination.AllNotes.route) }
                        CollectionChip("Unfiled", NexusIcons.Folder) {
                            onNavigate(NexusDestination.FolderDetail.createRoute(com.nexusnotes.navigation.UNFILED_FOLDER_ID))
                        }
                        CollectionChip("Favorites", Icons.Filled.Star) { onNavigate(NexusDestination.Favorites.route) }
                        CollectionChip("Tags", NexusIcons.Label) { onNavigate(NexusDestination.Tags.route) }
                        CollectionChip("Archive", NexusIcons.Archive) { onNavigate(NexusDestination.Archive.route) }
                        CollectionChip("Trash", Icons.Filled.Delete) { onNavigate(NexusDestination.Trash.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CollectionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun PromptLibraryCard(onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(spacing.xl),
        ) {
            Icon(NexusIcons.Sparkle, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(spacing.md))
            Text("Prompt Library", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "${PromptCategory.entries.size} categories for image, video, coding and automation prompts.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}
