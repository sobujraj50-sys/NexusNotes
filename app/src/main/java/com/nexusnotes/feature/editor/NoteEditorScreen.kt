package com.nexusnotes.feature.editor

import android.app.Activity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import com.nexusnotes.feature.folders.FolderPickerDialog
import com.nexusnotes.feature.folders.folderTint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexusnotes.domain.markdown.FormatAction
import com.nexusnotes.domain.markdown.MarkdownFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusnotes.core.design.components.ColorLabelDialog
import com.nexusnotes.core.design.components.NexusScreen
import com.nexusnotes.core.design.icons.NexusIcons
import com.nexusnotes.core.design.theme.NoteLabelColors
import com.nexusnotes.core.design.theme.spacing
import com.nexusnotes.domain.NoteText
import java.text.DateFormat
import java.util.Date

const val EDITOR_TITLE_TAG = "editor_title"
const val EDITOR_BODY_TAG = "editor_body"
const val EDITOR_STATUS_TAG = "editor_status"


@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Save immediately when the app goes to the background or the screen is left.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onBack()
                is EditorEvent.OpenNote -> onOpenNote(event.id)
            }
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    var showColorDialog by rememberSaveable { mutableStateOf(false) }
    var showFolderPicker by rememberSaveable { mutableStateOf(false) }
    var showAddTag by rememberSaveable { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val note = state.note
    val matches = remember(state.body.text, findQuery, findOpen) {
        if (findOpen) MarkdownFormatter.findMatches(state.body.text, findQuery) else emptyList()
    }
    val currentMatch = if (matches.isEmpty()) -1 else findIndex.coerceIn(0, matches.lastIndex)
    fun goToMatch(index: Int) {
        if (matches.isEmpty()) return
        val wrapped = ((index % matches.size) + matches.size) % matches.size
        findIndex = wrapped
        viewModel.selectInBody(matches[wrapped])
    }

    if (focusMode && !state.isLoading) {
        FocusModeEditor(
            state = state,
            onBodyChange = viewModel::onBodyChange,
            onExit = { focusMode = false },
        )
        return
    }

    NexusScreen(
        title = "",
        onBack = {
            viewModel.flush()
            onBack()
        },
        actions = {
            if (!state.isReadOnly) {
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(NexusIcons.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(NexusIcons.Redo, contentDescription = "Redo")
                }
            }
            IconButton(
                onClick = { previewMode = !previewMode },
                modifier = Modifier.testTag(EDITOR_PREVIEW_TOGGLE_TAG),
            ) {
                if (previewMode) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                } else {
                    Icon(NexusIcons.Preview, contentDescription = "Preview")
                }
            }
            if (note != null && !state.isReadOnly) {
                IconButton(onClick = viewModel::togglePinned) {
                    Icon(
                        NexusIcons.Pin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (note.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (note.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (note != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.isReadOnly) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { menuOpen = false; viewModel.restore() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete forever") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menuOpen = false; showDeleteDialog = true },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Find in note") },
                                leadingIcon = { Icon(Icons.Filled.Search, null) },
                                onClick = {
                                    menuOpen = false
                                    previewMode = false
                                    findOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Focus mode") },
                                leadingIcon = { Icon(NexusIcons.Fullscreen, null) },
                                onClick = {
                                    menuOpen = false
                                    previewMode = false
                                    findOpen = false
                                    focusMode = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Color label") },
                                leadingIcon = { Icon(NexusIcons.Palette, null) },
                                onClick = { menuOpen = false; showColorDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(NexusIcons.Copy, null) },
                                onClick = { menuOpen = false; viewModel.duplicate() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                                leadingIcon = {
                                    Icon(if (note.isArchived) NexusIcons.Unarchive else NexusIcons.Archive, null)
                                },
                                onClick = { menuOpen = false; viewModel.toggleArchived() },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to Trash") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menuOpen = false; viewModel.moveToTrash() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Note info") },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; showInfoDialog = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@NexusScreen
        }
        EditorContent(
            state = state,
            padding = padding,
            previewMode = previewMode,
            findBar = if (findOpen && !previewMode && !state.isReadOnly) {
                {
                    FindBar(
                        query = findQuery,
                        onQueryChange = {
                            findQuery = it
                            findIndex = 0
                        },
                        matchCount = matches.size,
                        currentIndex = currentMatch,
                        onPrevious = { goToMatch(currentMatch - 1) },
                        onNext = { goToMatch(currentMatch + 1) },
                        onClose = {
                            findOpen = false
                            findQuery = ""
                        },
                    )
                }
            } else {
                null
            },
            highlights = matches,
            currentHighlight = currentMatch,
            onTitleChange = viewModel::onTitleChange,
            onBodyChange = viewModel::onBodyChange,
            onFormat = viewModel::format,
            onToggleTask = viewModel::toggleTask,
            onRestore = viewModel::restore,
            onFolderClick = { showFolderPicker = true },
            onAddTagClick = { showAddTag = true },
            onRemoveTag = viewModel::removeTag,
        )
    }

    if (showColorDialog && note != null) {
        ColorLabelDialog(
            selected = note.colorIndex,
            onSelect = {
                viewModel.setColor(it)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false },
        )
    }
    if (showInfoDialog && note != null) {
        NoteInfoDialog(state = state, onDismiss = { showInfoDialog = false })
    }
    if (showFolderPicker) {
        FolderPickerDialog(
            title = "Move note to",
            tree = state.folderTree,
            selectedId = state.folderId,
            noneLabel = "Unfiled",
            onSelect = {
                viewModel.setFolder(it)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false },
        )
    }
    if (showAddTag) {
        AddTagDialog(
            existing = state.allTags.filter { tag -> state.tags.none { it.id == tag.id } },
            onAdd = { name, done -> viewModel.addTag(name) { err -> done(err) } },
            onDismiss = { showAddTag = false },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Delete forever?") },
            text = { Text("This note will be erased from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePermanently()
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    previewMode: Boolean,
    findBar: (@Composable () -> Unit)?,
    highlights: List<IntRange>,
    currentHighlight: Int,
    onTitleChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onBodyChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onFormat: (FormatAction) -> Unit,
    onToggleTask: (Int) -> Unit,
    onRestore: () -> Unit,
    onFolderClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val bodyText = state.body.text
    val words = remember(bodyText) { NoteText.wordCount(bodyText) }
    val chars = remember(bodyText) { NoteText.characterCount(bodyText) }
    val labelColor = state.note?.colorIndex?.let { NoteLabelColors.getOrNull(it) }
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        findBar?.invoke()
        if (state.isReadOnly) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "This note is in Trash and can't be edited.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRestore) { Text("Restore") }
                }
            }
        }
        if (state.note?.isArchived == true && !state.isReadOnly) {
            Text(
                "Archived",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        TextField(
            value = state.title,
            onValueChange = onTitleChange,
            readOnly = state.isReadOnly,
            placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall,
            maxLines = 3,
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EDITOR_TITLE_TAG),
        )

        Row(
            Modifier.padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labelColor != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(labelColor, CircleShape),
                )
                Spacer(Modifier.width(spacing.sm))
            }
            Text(
                text = "${state.saveState.label} · $words words · $chars characters",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.saveState == SaveState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(EDITOR_STATUS_TAG),
            )
        }
        if (!previewMode) {
            OrganizeChips(
                state = state,
                onFolderClick = onFolderClick,
                onAddTagClick = onAddTagClick,
                onRemoveTag = onRemoveTag,
            )
        }
        HorizontalDivider(Modifier.padding(top = spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)

        val highlightColor = MaterialTheme.colorScheme.secondaryContainer
        val currentColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        val transformation = remember(highlights, currentHighlight, highlightColor, currentColor) {
            if (highlights.isEmpty()) {
                VisualTransformation.None
            } else {
                FindHighlightTransformation(highlights, currentHighlight, highlightColor, currentColor)
            }
        }

        if (previewMode) {
            MarkdownPreview(
                markdown = state.body.text,
                onToggleTask = if (state.isReadOnly) null else onToggleTask,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(PREVIEW_TAG),
            )
        } else {
            TextField(
                value = state.body,
                onValueChange = onBodyChange,
                readOnly = state.isReadOnly,
                placeholder = { Text("Start writing… Markdown works: **bold**, # heading, - list") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = fieldColors,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(EDITOR_BODY_TAG),
            )
        }

        if (!state.isReadOnly && !previewMode) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FormattingToolbar(onFormat = onFormat)
        }
    }
}

private data class ToolbarItem(val action: FormatAction, val label: String, val description: String, val style: SpanStyle? = null)

private val ToolbarItems = listOf(
    ToolbarItem(FormatAction.BOLD, "B", "Bold", SpanStyle(fontWeight = FontWeight.Bold)),
    ToolbarItem(FormatAction.ITALIC, "I", "Italic", SpanStyle(fontStyle = FontStyle.Italic)),
    ToolbarItem(FormatAction.UNDERLINE, "U", "Underline", SpanStyle(textDecoration = TextDecoration.Underline)),
    ToolbarItem(FormatAction.STRIKETHROUGH, "S", "Strikethrough", SpanStyle(textDecoration = TextDecoration.LineThrough)),
    ToolbarItem(FormatAction.H1, "H1", "Heading 1"),
    ToolbarItem(FormatAction.H2, "H2", "Heading 2"),
    ToolbarItem(FormatAction.H3, "H3", "Heading 3"),
    ToolbarItem(FormatAction.BULLET, "•", "Bulleted list"),
    ToolbarItem(FormatAction.NUMBERED, "1.", "Numbered list"),
    ToolbarItem(FormatAction.TASK, "☐", "Checklist"),
    ToolbarItem(FormatAction.QUOTE, "❝", "Quote"),
    ToolbarItem(FormatAction.INLINE_CODE, "<>", "Inline code", SpanStyle(fontFamily = FontFamily.Monospace)),
    ToolbarItem(FormatAction.CODE_BLOCK, "{ }", "Code block", SpanStyle(fontFamily = FontFamily.Monospace)),
    ToolbarItem(FormatAction.LINK, "Link", "Insert link"),
    ToolbarItem(FormatAction.TABLE, "Table", "Insert table"),
)

const val FORMAT_TOOLBAR_TAG = "format_toolbar"
const val EDITOR_PREVIEW_TOGGLE_TAG = "editor_preview_toggle"

@Composable
private fun FormattingToolbar(onFormat: (FormatAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.xs)
            .testTag(FORMAT_TOOLBAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarItems.forEach { item ->
            TextButton(
                onClick = { onFormat(item.action) },
                modifier = Modifier.semantics { contentDescription = item.description },
            ) {
                Text(
                    text = if (item.style != null) {
                        buildAnnotatedString { withStyle(item.style) { append(item.label) } }
                    } else {
                        AnnotatedString(item.label)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Find in note") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag(FIND_FIELD_TAG),
            )
            Text(
                text = when {
                    query.isEmpty() -> ""
                    matchCount == 0 -> "0/0"
                    else -> "${currentIndex + 1}/$matchCount"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(FIND_COUNT_TAG),
            )
            IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close find")
            }
        }
    }
}

const val FIND_FIELD_TAG = "find_field"
const val FIND_COUNT_TAG = "find_count"

/** Highlights find matches inside the editor without changing the stored text. */
private data class FindHighlightTransformation(
    val ranges: List<IntRange>,
    val current: Int,
    val color: Color,
    val currentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            ranges.forEachIndexed { index, range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (end > start) {
                    addStyle(SpanStyle(background = if (index == current) currentColor else color), start, end)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Distraction-free writing: body only, system bars hidden, Back exits. */
@Composable
private fun FocusModeEditor(
    state: EditorUiState,
    onBodyChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(onBack = onExit)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().imePadding()) {
            TextField(
                value = state.body,
                onValueChange = onBodyChange,
                readOnly = state.isReadOnly,
                placeholder = { Text("Write…") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.xxl)
                    .testTag(EDITOR_BODY_TAG),
            )
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MaterialTheme.spacing.sm),
            ) {
                Icon(
                    NexusIcons.FullscreenExit,
                    contentDescription = "Exit focus mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = state.saveState.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(MaterialTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun NoteInfoDialog(state: EditorUiState, onDismiss: () -> Unit) {
    val note = state.note ?: return
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val body = state.body.text
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, null) },
        title = { Text("Note info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                Text("Created: ${format.format(Date(note.createdAt))}")
                Text("Last edited: ${format.format(Date(note.updatedAt))}")
                note.deletedAt?.let { Text("Moved to Trash: ${format.format(Date(it))}") }
                Text("Words: ${NoteText.wordCount(body)}")
                Text("Characters: ${NoteText.characterCount(body)}")
                Text("Stored only on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

const val EDITOR_FOLDER_CHIP_TAG = "editor_folder_chip"
const val EDITOR_ADD_TAG_TAG = "editor_add_tag"

@Composable
private fun OrganizeChips(
    state: EditorUiState,
    onFolderClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val enabled = !state.isReadOnly
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onFolderClick,
            enabled = enabled,
            label = { Text(state.folder?.name ?: "Unfiled", maxLines = 1) },
            leadingIcon = {
                Icon(
                    NexusIcons.Folder,
                    contentDescription = null,
                    tint = folderTint(state.folder?.colorIndex),
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            modifier = Modifier.testTag(EDITOR_FOLDER_CHIP_TAG),
        )
        state.tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = { if (enabled) onRemoveTag(tag.id) },
                enabled = enabled,
                label = { Text("#" + tag.name, maxLines = 1) },
                trailingIcon = {
                    if (enabled) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove tag ${tag.name}",
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    }
                },
            )
        }
        if (enabled && state.isStored) {
            AssistChip(
                onClick = onAddTagClick,
                label = { Text("Tag") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.testTag(EDITOR_ADD_TAG_TAG),
            )
        }
    }
}

/** Add a tag: type a new name or pick an existing tag. */
@Composable
private fun AddTagDialog(
    existing: List<com.nexusnotes.domain.model.Tag>,
    onAdd: (name: String, done: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val suggestions = remember(text, existing) {
        val q = text.trim().removePrefix("#")
        existing.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }.take(8)
    }
    fun submit(name: String) {
        onAdd(name) { err -> if (err == null) onDismiss() else error = err }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text("Tag name") },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.orEmpty()) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit(text) }),
                    modifier = Modifier.focusRequester(focusRequester),
                )
                if (suggestions.isNotEmpty()) {
                    Text("Existing tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    ) {
                        suggestions.forEach { tag ->
                            AssistChip(onClick = { submit(tag.name) }, label = { Text("#" + tag.name) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { submit(text) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
