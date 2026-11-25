package com.nexusnotes.feature.templates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.data.repository.TemplateRepository
import com.nexusnotes.domain.template.NoteTemplate
import com.nexusnotes.navigation.NexusDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val templates: TemplateRepository) : ViewModel() {
    val all: StateFlow<List<NoteTemplate>> = templates.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _created = Channel<String>(Channel.BUFFERED)
    val created = _created.receiveAsFlow()

    fun use(t: NoteTemplate) {
        viewModelScope.launch { _created.send(templates.createNote(t)) }
    }

    fun delete(t: NoteTemplate) {
        if (!t.builtIn) viewModelScope.launch { templates.delete(t.id) }
    }

    companion object {
        val Factory = viewModelFactory { initializer { TemplatesViewModel(appContainer().templateRepository) } }
    }
}

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<NoteTemplate?>(null) }
    LaunchedEffect(viewModel) { viewModel.created.collect { onOpenNote(it) } }
    val spacing = MaterialTheme.spacing
    NexusScreen(title = NexusDestination.Templates.title, onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = spacing.xl),
        ) {
            val (builtIn, mine) = all.partition { it.builtIn }
            item { SectionHeader("Your templates", Modifier.padding(horizontal = spacing.screen)) }
            if (mine.isEmpty()) {
                item {
                    Text(
                        "None yet. Open any note › ⋮ › Save as template.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.screen),
                    )
                }
            }
            items(mine, key = { it.id }) { t -> TemplateRow(t, onUse = { viewModel.use(t) }, onDelete = { deleting = t }) }
            item { SectionHeader("Built-in", Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm)) }
            items(builtIn, key = { it.id }) { t -> TemplateRow(t, onUse = { viewModel.use(t) }, onDelete = null) }
        }
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = "Delete template?",
            message = "“${t.name}” will be removed. Notes made from it are not affected.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.delete(t); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TemplateRow(t: NoteTemplate, onUse: () -> Unit, onDelete: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(t.name) },
        supportingContent = {
            Text(t.body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(NexusIcons.Note, null) },
        trailingContent = if (onDelete != null) {
            { IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${t.name}") } }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onUse),
    )
}
