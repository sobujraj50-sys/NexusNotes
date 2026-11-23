package com.nexusnotes.feature.prompts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.common.ShareUtils
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NameDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.model.Prompt
import com.nexusnotes.domain.model.PromptCategoryRef
import com.nexusnotes.domain.prompt.PromptTemplate
import com.nexusnotes.navigation.NexusDestination
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val PROMPT_CARD_TAG = "prompt_card"

private sealed interface CategoryDialog {
    data object Create : CategoryDialog
    data class Rename(val ref: PromptCategoryRef) : CategoryDialog
    data class Delete(val ref: PromptCategoryRef) : CategoryDialog
}

@Composable
fun PromptLibraryScreen(
    onNavigate: (String) -> Unit,
    viewModel: PromptLibraryViewModel = viewModel(factory = PromptLibraryViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }
    val selectedCustom = state.categories.firstOrNull { it.key == state.filter && it.isCustom }

    NexusScreen(
        title = NexusDestination.PromptLibrary.title,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("New category") }, onClick = { menuOpen = false; dialog = CategoryDialog.Create })
                    if (selectedCustom != null) {
                        DropdownMenuItem(text = { Text("Rename “${selectedCustom.name}”") }, onClick = { menuOpen = false; dialog = CategoryDialog.Rename(selectedCustom) })
                        DropdownMenuItem(text = { Text("Delete “${selectedCustom.name}”") }, onClick = { menuOpen = false; dialog = CategoryDialog.Delete(selectedCustom) })
                    }
                    DropdownMenuItem(
                        text = { Text("Export all prompts (JSON)") },
                        enabled = state.totalCount > 0,
                        onClick = {
                            menuOpen = false
                            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            exportLauncher.launch("nexus-prompts-$stamp.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import prompts (JSON)") },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        },
                    )
                    if (state.totalCount == 0) {
                        DropdownMenuItem(text = { Text("Add example prompts") }, onClick = { menuOpen = false; viewModel.addExamples() })
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(NexusDestination.PromptEditor.createRoute(category = state.filter)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New prompt") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text("Search prompts and templates") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.sm),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FilterChip(selected = state.filter == FILTER_ALL, onClick = { viewModel.setFilter(FILTER_ALL) }, label = { Text("All · ${state.totalCount}") })
                FilterChip(
                    selected = state.filter == FILTER_FAVORITES,
                    onClick = { viewModel.setFilter(FILTER_FAVORITES) },
                    label = { Text("Favorites · ${state.favoritesCount}") },
                    leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) },
                )
                state.categories.forEach { ref ->
                    FilterChip(
                        selected = state.filter == ref.key,
                        onClick = { viewModel.setFilter(ref.key) },
                        label = { Text("${ref.name} · ${state.counts[ref.key] ?: 0}") },
                    )
                }
            }
            when {
                state.isLoading -> Unit
                state.totalCount == 0 -> EmptyLibrary(onAddExamples = viewModel::addExamples)
                state.prompts.isEmpty() -> CenteredEmptyState(
                    padding = PaddingValues(),
                    icon = NexusIcons.Sparkle,
                    title = if (state.query.isBlank()) "No prompts here yet" else "No matching prompts",
                    message = if (state.query.isBlank()) "Tap New prompt to add one to this category." else "Try other words.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.prompts, key = { it.id }) { prompt ->
                        PromptCard(
                            prompt = prompt,
                            categoryName = state.categoryName(prompt.categoryKey),
                            onClick = { onNavigate(NexusDestination.PromptEditor.createRoute(prompt.id)) },
                            onRun = { onNavigate(NexusDestination.PromptRunner.createRoute(prompt.id)) },
                            onCopy = { ShareUtils.copy(context, prompt.title.ifBlank { "Prompt" }, prompt.template) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        CategoryDialog.Create -> {
            var error by remember { mutableStateOf<String?>(null) }
            NameDialog(
                title = "New category",
                initial = "",
                confirmLabel = "Create",
                error = error,
                onConfirm = { name -> viewModel.createCategory(name) { err -> if (err == null) dialog = null else error = err } },
                onDismiss = { dialog = null },
            )
        }
        is CategoryDialog.Rename -> {
            var error by remember(d) { mutableStateOf<String?>(null) }
            NameDialog(
                title = "Rename category",
                initial = d.ref.name,
                confirmLabel = "Rename",
                error = error,
                onConfirm = { name -> viewModel.renameCategory(d.ref.key, name) { err -> if (err == null) dialog = null else error = err } },
                onDismiss = { dialog = null },
            )
        }
        is CategoryDialog.Delete -> ConfirmDialog(
            title = "Delete “${d.ref.name}”?",
            message = "Its ${state.counts[d.ref.key] ?: 0} prompt(s) are kept and moved to General Prompts.",
            confirmLabel = "Delete category",
            onConfirm = {
                viewModel.deleteCategory(d.ref.key)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun EmptyLibrary(onAddExamples: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        Modifier.fillMaxWidth().padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(NexusIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("Your prompt library is empty", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save prompts with {{variables}} such as {{subject}} or {{style}}, then fill them in the Template Runner. " +
                "Want a head start? Add 6 example prompts (image, video, character, Android, Claude).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddExamples) { Text("Add example prompts") }
    }
}

@Composable
private fun PromptCard(
    prompt: Prompt,
    categoryName: String,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onCopy: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val variables = remember(prompt.template) { PromptTemplate.variables(prompt.template) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag(PROMPT_CARD_TAG),
    ) {
        Column(Modifier.padding(start = spacing.lg, top = spacing.md, bottom = spacing.xs, end = spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prompt.title.ifBlank { "Untitled prompt" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (prompt.isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                categoryName + if (variables.isNotEmpty()) " · ${variables.size} variable(s)" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                prompt.template,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = spacing.md),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onCopy) {
                    Icon(NexusIcons.Copy, null, Modifier.size(18.dp))
                    Text("Copy", Modifier.padding(start = spacing.xs))
                }
                if (variables.isNotEmpty()) {
                    androidx.compose.material3.TextButton(onClick = onRun) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                        Text("Fill & run", Modifier.padding(start = spacing.xs))
                    }
                }
            }
        }
    }
}
