package com.nexusnotes.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NameDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.domain.model.Tag
import com.nexusnotes.domain.model.TagWithCount
import com.nexusnotes.navigation.NexusDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagsViewModel(
    private val organization: OrganizationRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** Null while loading. */
    val tags: StateFlow<List<TagWithCount>?> = organization.observeTagsWithCounts()
        .map<List<TagWithCount>, List<TagWithCount>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(id: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(organization.renameTag(id, name).exceptionOrNull()?.message) }
    }

    fun delete(id: String) {
        appScope.launch { organization.deleteTag(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                TagsViewModel(c.organizationRepository, c.applicationScope)
            }
        }
    }
}

private sealed interface TagDialog {
    data class Rename(val tag: Tag) : TagDialog
    data class Delete(val tag: Tag, val count: Int) : TagDialog
}

@Composable
fun TagsScreen(
    onOpenTag: (tagId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: TagsViewModel = viewModel(factory = TagsViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TagDialog?>(null) }

    NexusScreen(title = NexusDestination.Tags.title, onBack = onBack) { padding ->
        val list = tags
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> CenteredEmptyState(
                padding = padding,
                icon = NexusIcons.Label,
                title = "No tags yet",
                message = "Open a note and tap “+ Tag” to add one. A note can have many tags.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(list, key = { it.tag.id }) { item ->
                    TagRow(
                        item = item,
                        onClick = { onOpenTag(item.tag.id) },
                        onRename = { dialog = TagDialog.Rename(item.tag) },
                        onDelete = { dialog = TagDialog.Delete(item.tag, item.noteCount) },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is TagDialog.Rename -> {
            var error by remember(d) { mutableStateOf<String?>(null) }
            NameDialog(
                title = "Rename tag",
                initial = d.tag.name,
                confirmLabel = "Rename",
                error = error,
                onConfirm = { name -> viewModel.rename(d.tag.id, name) { err -> if (err == null) dialog = null else error = err } },
                onDismiss = { dialog = null },
            )
        }
        is TagDialog.Delete -> ConfirmDialog(
            title = "Delete tag “${d.tag.name}”?",
            message = "It will be removed from ${d.count} note(s). The notes themselves are not deleted.",
            confirmLabel = "Delete tag",
            onConfirm = {
                viewModel.delete(d.tag.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun TagRow(item: TagWithCount, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val spacing = MaterialTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(NexusIcons.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "#" + item.tag.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = spacing.md)
                    .weight(1f),
            )
            Text(
                "${item.noteCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Tag options for ${item.tag.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
