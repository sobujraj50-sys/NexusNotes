package com.nexusnotes.feature.search

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.CenteredEmptyState
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.navigation.NexusDestination

const val SEARCH_FIELD_TAG = "search_field"
const val SEARCH_RESULT_TAG = "search_result"

@Composable
fun SearchScreen(
    onOpenNote: (String) -> Unit,
    onOpenPrompt: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!state.hasCriteria) focusRequester.requestFocus()
    }

    NexusScreen(title = NexusDestination.Search.title, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                placeholder = { Text("Search titles, text, tags, folders") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.commitQuery() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screen, vertical = spacing.sm)
                    .focusRequester(focusRequester)
                    .testTag(SEARCH_FIELD_TAG),
            )

            FilterRow(state, viewModel)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = spacing.screen, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (state.matchingTags.isNotEmpty() || state.matchingFolders.isNotEmpty()) {
                    item(key = "suggestions") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            state.matchingFolders.forEach { node ->
                                AssistChip(
                                    onClick = {
                                        viewModel.setFolderFilter(node.folder.id)
                                        viewModel.onQueryChange("")
                                    },
                                    label = { Text(node.folder.name) },
                                    leadingIcon = { Icon(NexusIcons.Folder, null, Modifier.size(18.dp)) },
                                )
                            }
                            state.matchingTags.forEach { item ->
                                AssistChip(
                                    onClick = {
                                        viewModel.setTagFilter(item.tag.id)
                                        viewModel.onQueryChange("")
                                    },
                                    label = { Text("#" + item.tag.name) },
                                )
                            }
                        }
                    }
                }

                if (!state.hasCriteria) {
                    if (state.history.isNotEmpty()) {
                        item(key = "history_header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Recent searches", Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                            }
                        }
                        items(state.history, key = { "h_$it" }) { q ->
                            Surface(
                                onClick = { viewModel.onQueryChange(q) },
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.padding(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(NexusIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(q, Modifier.padding(start = spacing.md))
                                }
                            }
                        }
                    }
                    if (state.tags.isNotEmpty()) {
                        item(key = "tags_header") { SectionHeader("Browse by tag", Modifier.padding(top = spacing.sm)) }
                        item(key = "tags") {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                state.tags.take(20).forEach { item ->
                                    AssistChip(onClick = { viewModel.setTagFilter(item.tag.id) }, label = { Text("#${item.tag.name} · ${item.noteCount}") })
                                }
                            }
                        }
                    }
                    item(key = "info") {
                        Text(
                            "Search runs entirely on this phone. Words match from their start, so “নোট” also finds “নোটের”. " +
                                "Vault notes are never searchable." +
                                if (!state.saveHistory) " Search history is off (Settings)." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.md),
                        )
                    }
                } else if (state.results.isEmpty() && state.prompts.isEmpty() && !state.isLoading) {
                    item(key = "none") {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            CenteredEmptyState(
                                padding = PaddingValues(),
                                icon = Icons.Filled.Search,
                                title = "No matching notes",
                                message = "Try fewer words, or remove a filter. Trash is not searched.",
                            )
                        }
                    }
                } else {
                    item(key = "count") {
                        Text(
                            "${state.results.size} note(s)" + if (state.prompts.isNotEmpty()) " · ${state.prompts.size} prompt(s)" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.results, key = { it.note.id }) { result ->
                        ResultCard(result) {
                            viewModel.commitQuery()
                            onOpenNote(result.note.id)
                        }
                    }
                    if (state.prompts.isNotEmpty()) {
                        item(key = "prompts_header") { SectionHeader("Prompts", Modifier.padding(top = spacing.sm)) }
                        items(state.prompts, key = { "p_" + it.id }) { prompt ->
                            val terms = remember(state.query) { com.nexusnotes.domain.search.SearchQuery.terms(state.query) }
                            ResultCard(
                                SearchResult(
                                    note = com.nexusnotes.domain.model.Note(
                                        prompt.id, prompt.title, prompt.template, null, false, false, false, null, prompt.createdAt, prompt.updatedAt,
                                    ),
                                    title = prompt.title.ifBlank { "Untitled prompt" },
                                    titleHighlights = com.nexusnotes.domain.search.SearchQuery.highlightRanges(prompt.title, terms),
                                    snippet = com.nexusnotes.domain.search.SearchQuery.snippet(prompt.template, terms),
                                ),
                            ) {
                                viewModel.commitQuery()
                                onOpenPrompt(prompt.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: SearchUiState, viewModel: SearchViewModel) {
    val spacing = MaterialTheme.spacing
    var folderMenu by remember { mutableStateOf(false) }
    var tagMenu by remember { mutableStateOf(false) }
    val f = state.filter
    val folderLabel = when {
        f.unfiledOnly -> "Unfiled"
        f.folderId != null -> state.folders.firstOrNull { it.folder.id == f.folderId }?.folder?.name ?: "Folder"
        else -> "Any folder"
    }
    val tagLabel = f.tagId?.let { id -> state.tags.firstOrNull { it.tag.id == id }?.tag?.name?.let { "#$it" } } ?: "Any tag"

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box {
            FilterChip(
                selected = f.folderId != null || f.unfiledOnly,
                onClick = { folderMenu = true },
                label = { Text(folderLabel) },
                leadingIcon = { Icon(NexusIcons.Folder, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            )
            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                DropdownMenuItem(text = { Text("Any folder") }, onClick = { folderMenu = false; viewModel.setFolderFilter(null) })
                DropdownMenuItem(text = { Text("Unfiled") }, onClick = { folderMenu = false; viewModel.setFolderFilter(null, unfiledOnly = true) })
                state.folders.forEach { node ->
                    DropdownMenuItem(
                        text = { Text("  ".repeat(node.depth) + node.folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { folderMenu = false; viewModel.setFolderFilter(node.folder.id) },
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = f.tagId != null,
                onClick = { tagMenu = true },
                label = { Text(tagLabel) },
                leadingIcon = { Icon(NexusIcons.Label, null, Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            )
            DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                DropdownMenuItem(text = { Text("Any tag") }, onClick = { tagMenu = false; viewModel.setTagFilter(null) })
                state.tags.forEach { item ->
                    DropdownMenuItem(text = { Text("#" + item.tag.name) }, onClick = { tagMenu = false; viewModel.setTagFilter(item.tag.id) })
                }
            }
        }
        FilterChip(
            selected = f.includeArchived,
            onClick = { viewModel.setIncludeArchived(!f.includeArchived) },
            label = { Text("Include archived") },
            leadingIcon = { Icon(NexusIcons.Archive, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun ResultCard(result: SearchResult, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.secondaryContainer,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SEARCH_RESULT_TAG),
    ) {
        Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            Text(
                highlighted(result.title, result.titleHighlights, highlight),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.snippet.text.isNotEmpty()) {
                Spacer(Modifier.height(spacing.xs))
                Text(
                    highlighted(result.snippet.text, result.snippet.highlights, highlight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result.note.isArchived) {
                Text(
                    "Archived",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

private fun highlighted(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            val start = r.first.coerceIn(0, text.length)
            val end = (r.last + 1).coerceIn(start, text.length)
            if (end > start) addStyle(style, start, end)
        }
    }
