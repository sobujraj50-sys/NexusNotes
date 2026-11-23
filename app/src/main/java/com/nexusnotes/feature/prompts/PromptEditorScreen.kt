package com.nexusnotes.feature.prompts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.common.ShareUtils
import com.nexusnotes.core.design.components.ConfirmDialog
import com.nexusnotes.core.design.components.NameDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.components.SectionHeader
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.model.PromptVersion
import com.nexusnotes.domain.prompt.PromptTemplate
import com.nexusnotes.feature.editor.SaveState
import java.text.DateFormat
import java.util.Date

const val PROMPT_TITLE_TAG = "prompt_title"
const val PROMPT_TEMPLATE_TAG = "prompt_template"

private val CommonVariables = listOf("subject", "style", "camera", "lighting", "aspect_ratio", "duration", "negative_prompt")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptEditorScreen(
    onBack: () -> Unit,
    onOpenPrompt: (String) -> Unit,
    onRun: (String) -> Unit,
    viewModel: PromptEditorViewModel = viewModel(factory = PromptEditorViewModel.Factory),
) {
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showCustomVariable by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                PromptEditorEvent.Close -> onBack()
                is PromptEditorEvent.OpenPrompt -> onOpenPrompt(e.id)
            }
        }
    }

    val variables = remember(state.template.text) { PromptTemplate.variables(state.template.text) }
    val prompt = state.prompt
    val highlight = MaterialTheme.colorScheme.primary

    NexusScreen(
        title = "",
        onBack = {
            viewModel.flush()
            onBack()
        },
        actions = {
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) { Icon(NexusIcons.Undo, contentDescription = "Undo") }
            IconButton(onClick = viewModel::redo, enabled = state.canRedo) { Icon(NexusIcons.Redo, contentDescription = "Redo") }
            if (prompt != null) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (prompt.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (prompt.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (prompt.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy template") },
                            leadingIcon = { Icon(NexusIcons.Copy, null) },
                            onClick = { menuOpen = false; ShareUtils.copy(context, state.title.text.ifBlank { "Prompt" }, state.template.text) },
                        )
                        DropdownMenuItem(
                            text = { Text("Share template") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menuOpen = false; ShareUtils.shareText(context, state.title.text.ifBlank { "Prompt" }, state.template.text) },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(NexusIcons.Copy, null) },
                            onClick = { menuOpen = false; viewModel.duplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Version history (${state.versions.size})") },
                            leadingIcon = { Icon(NexusIcons.History, null) },
                            onClick = { menuOpen = false; showHistory = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; showDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@NexusScreen
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag(PROMPT_TITLE_TAG),
            )

            Box {
                AssistChip(
                    onClick = { categoryMenu = true },
                    label = { Text(state.categoryName) },
                    leadingIcon = { Icon(NexusIcons.Folder, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    state.categories.forEach { ref ->
                        DropdownMenuItem(text = { Text(ref.name) }, onClick = { categoryMenu = false; viewModel.setCategory(ref.key) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("New category…") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { categoryMenu = false; showNewCategory = true },
                    )
                }
            }

            OutlinedTextField(
                value = state.template,
                onValueChange = viewModel::onTemplateChange,
                label = { Text("Prompt template") },
                placeholder = { Text("A cinematic shot of {{subject}} in {{style}} style…") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = remember(highlight) { VariableHighlight(highlight) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).testTag(PROMPT_TEMPLATE_TAG),
            )

            Text(
                "${state.saveState.label} · ${state.template.text.length} characters",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.saveState == SaveState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Insert variable")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CommonVariables.forEach { name ->
                    AssistChip(onClick = { viewModel.insertVariable(name) }, label = { Text("{{$name}}", fontFamily = FontFamily.Monospace) })
                }
                AssistChip(
                    onClick = { showCustomVariable = true },
                    label = { Text("Custom…") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }

            SectionHeader("Variables in this prompt (${variables.size})")
            if (variables.isEmpty()) {
                Text(
                    "Type {{name}} in the template, or tap a chip above. Each variable becomes a field in the Template Runner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (prompt == null) {
                    Text(
                        "Default values can be set once the prompt is saved (write a title or template first).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                variables.forEach { name ->
                    OutlinedTextField(
                        value = state.defaults[name].orEmpty(),
                        onValueChange = { viewModel.setDefault(name, it) },
                        enabled = prompt != null,
                        label = { Text("Default for {{$name}}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = { viewModel.saveThen { stored -> if (stored) onRun(viewModel.promptId) } },
                enabled = state.template.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (variables.isEmpty()) "Open in Template Runner" else "Fill variables & run", Modifier.padding(start = spacing.sm))
            }
            Text(
                "The Template Runner fills a copy. This template is never changed by running it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showNewCategory) {
        var error by remember { mutableStateOf<String?>(null) }
        NameDialog(
            title = "New category",
            initial = "",
            confirmLabel = "Create",
            error = error,
            onConfirm = { name -> viewModel.createCategory(name) { err -> if (err == null) showNewCategory = false else error = err } },
            onDismiss = { showNewCategory = false },
        )
    }
    if (showCustomVariable) {
        var error by remember { mutableStateOf<String?>(null) }
        NameDialog(
            title = "Insert variable",
            initial = "",
            label = "Variable name (letters, digits, _)",
            confirmLabel = "Insert",
            error = error,
            onConfirm = { raw ->
                val name = raw.trim().replace(' ', '_')
                if (PromptTemplate.variables("{{$name}}") == listOf(name)) {
                    viewModel.insertVariable(name)
                    showCustomVariable = false
                } else {
                    error = "Use letters, digits and _ only, starting with a letter."
                }
            },
            onDismiss = { showCustomVariable = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete this prompt?",
            message = "The prompt, its variable defaults and its version history will be erased from this phone.",
            confirmLabel = "Delete",
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showHistory) {
        VersionHistoryDialog(
            versions = state.versions,
            currentTemplate = state.template.text,
            currentTitle = state.title.text,
            onRestore = { id ->
                viewModel.restoreVersion(id)
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
}

@Composable
private fun VersionHistoryDialog(
    versions: List<PromptVersion>,
    currentTitle: String,
    currentTemplate: String,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var preview by remember { mutableStateOf<PromptVersion?>(null) }
    val selected = preview
    if (selected != null) {
        val isCurrent = selected.template == currentTemplate && selected.title == currentTitle
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(format.format(Date(selected.createdAt))) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(selected.title.ifBlank { "Untitled prompt" }, style = MaterialTheme.typography.titleMedium)
                    Text(selected.template, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { onRestore(selected.id) }, enabled = !isCurrent) {
                    Text(if (isCurrent) "Current version" else "Restore this version")
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Back") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version history") },
        text = {
            if (versions.isEmpty()) {
                Text("No saved versions yet.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(versions, key = { it.id }) { v ->
                        Surface(onClick = { preview = v }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(format.format(Date(v.createdAt)), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    v.template.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Shows {{variables}} in bold accent color while editing (display only). */
private data class VariableHighlight(val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            PromptTemplate.tokens(text.text).forEach { token ->
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), token.range.first, token.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
