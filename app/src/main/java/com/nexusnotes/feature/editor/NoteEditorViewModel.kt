package com.nexusnotes.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.domain.EditHistory
import com.nexusnotes.domain.EditorSnapshot
import com.nexusnotes.domain.markdown.FormatAction
import com.nexusnotes.domain.markdown.MarkdownFormatter
import com.nexusnotes.domain.markdown.TextEdit
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.SaveOutcome
import com.nexusnotes.navigation.ARG_NOTE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SaveState(val label: String) {
    NEW("Not saved yet"),
    PENDING("Editing…"),
    SAVING("Saving…"),
    SAVED("Saved"),
    ERROR("Couldn't save — will retry"),
}

data class EditorUiState(
    val isLoading: Boolean = true,
    val title: TextFieldValue = TextFieldValue(),
    val body: TextFieldValue = TextFieldValue(),
    /** The stored note (flags, timestamps). Null until the note exists in the database. */
    val note: Note? = null,
    val saveState: SaveState = SaveState.NEW,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val isReadOnly: Boolean get() = note?.isInTrash == true
    val isStored: Boolean get() = note != null
}

sealed interface EditorEvent {
    data object Close : EditorEvent
    data class OpenNote(val id: String) : EditorEvent
}

/**
 * Editor logic.
 *
 * Autosave: every text change restarts a short timer; when typing pauses for
 * [AUTOSAVE_DELAY_MS] the note is written. Pending text is also written immediately when
 * the screen stops (app backgrounded, screen closed) via [flush] and [onCleared].
 * Writes run in the application scope so leaving the screen cannot cancel them.
 *
 * A new note gets its id up front (kept in SavedStateHandle), but the row is only
 * created once the title or body contains text — empty notes are never stored.
 */
class NoteEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    val noteId: String = savedStateHandle.get<String>(ARG_NOTE_ID)
        ?: savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT_ID] = it }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val history = EditHistory(EditorSnapshot("", ""))
    private val saveMutex = Mutex()
    private var saveJob: Job? = null

    @Volatile
    private var hasUnsavedChanges = false

    init {
        viewModelScope.launch {
            val stored = repository.getNote(noteId)
            val title = stored?.title.orEmpty()
            val body = stored?.body.orEmpty()
            history.reset(EditorSnapshot(title, body))
            _uiState.update {
                it.copy(
                    isLoading = false,
                    title = TextFieldValue(title, TextRange(title.length)),
                    body = TextFieldValue(body),
                    note = stored,
                    saveState = if (stored == null) SaveState.NEW else SaveState.SAVED,
                )
            }
            // Keep flags and timestamps live (pin, favorite, color, trash...).
            repository.observeNote(noteId).collect { note -> _uiState.update { it.copy(note = note) } }
        }
    }

    fun onTitleChange(value: TextFieldValue) {
        val old = _uiState.value
        if (old.isLoading || old.isReadOnly) return
        _uiState.update { it.copy(title = value) }
        if (value.text != old.title.text) onTextEdited()
    }

    fun onBodyChange(value: TextFieldValue) {
        val old = _uiState.value
        if (old.isLoading || old.isReadOnly) return
        // Pressing Enter inside a list continues the list (or ends it on an empty item).
        val continued = MarkdownFormatter.continueListOnEnter(old.body.toEdit(), value.toEdit())
        val newValue = continued?.toFieldValue() ?: value
        _uiState.update { it.copy(body = newValue) }
        if (newValue.text != old.body.text) onTextEdited()
    }

    /** Applies a formatting toolbar action to the body at its current selection. */
    fun format(action: FormatAction) {
        val state = _uiState.value
        if (state.isLoading || state.isReadOnly) return
        val result = MarkdownFormatter.apply(state.body.toEdit(), action)
        _uiState.update { it.copy(body = result.toFieldValue()) }
        if (result.text != state.body.text) onTextEdited()
    }

    /** Toggles a checklist item from the preview. [sourceLine] is the 0-based line in the body. */
    fun toggleTask(sourceLine: Int) {
        val state = _uiState.value
        if (state.isLoading || state.isReadOnly) return
        val newText = MarkdownFormatter.toggleTask(state.body.text, sourceLine) ?: return
        val selection = TextRange(
            state.body.selection.start.coerceAtMost(newText.length),
            state.body.selection.end.coerceAtMost(newText.length),
        )
        _uiState.update { it.copy(body = TextFieldValue(newText, selection)) }
        onTextEdited()
    }

    /** Moves the body selection to [range] (used by Find). Does not change the text. */
    fun selectInBody(range: IntRange) {
        _uiState.update {
            val length = it.body.text.length
            val start = range.first.coerceIn(0, length)
            val end = (range.last + 1).coerceIn(start, length)
            it.copy(body = it.body.copy(selection = TextRange(start, end)))
        }
    }

    fun undo() {
        if (_uiState.value.isReadOnly) return
        history.undo()?.let(::applySnapshot)
    }

    fun redo() {
        if (_uiState.value.isReadOnly) return
        history.redo()?.let(::applySnapshot)
    }

    /** Write pending text now (called when the screen stops). */
    fun flush() {
        saveJob?.cancel()
        if (hasUnsavedChanges) appScope.launch { saveNow() }
    }

    override fun onCleared() {
        flush()
    }

    // ---- Note actions (only offered once the note is stored) ----

    fun togglePinned() = withStoredNote { repository.setPinned(it.id, !it.isPinned) }

    fun toggleFavorite() = withStoredNote { repository.setFavorite(it.id, !it.isFavorite) }

    fun setColor(colorIndex: Int?) = withStoredNote { repository.setColor(it.id, colorIndex) }

    fun toggleArchived() = withStoredNote { repository.setArchived(it.id, !it.isArchived) }

    fun restore() = withStoredNote { repository.restore(it.id) }

    fun moveToTrash() {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) {
                saveNow()
                if (_uiState.value.isStored) repository.moveToTrash(noteId)
            }
            _events.send(EditorEvent.Close)
        }
    }

    /** Caller must have confirmed with the user. Works only for notes already in Trash. */
    fun deletePermanently() {
        viewModelScope.launch {
            withContext(NonCancellable) { repository.deletePermanently(noteId) }
            _events.send(EditorEvent.Close)
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            saveJob?.cancel()
            val newId = withContext(NonCancellable) {
                saveNow()
                repository.duplicate(noteId)
            }
            if (newId != null) _events.send(EditorEvent.OpenNote(newId))
        }
    }

    // ---- Internals ----

    private fun TextFieldValue.toEdit() = TextEdit(text, selection.min, selection.max)

    private fun TextEdit.toFieldValue() = TextFieldValue(text, TextRange(selectionStart, selectionEnd))

    private fun withStoredNote(action: suspend (Note) -> Unit) {
        val note = _uiState.value.note ?: return
        appScope.launch { action(note) }
    }

    private fun onTextEdited() {
        val state = _uiState.value
        history.record(EditorSnapshot(state.title.text, state.body.text), clock.now())
        markDirty()
    }

    private fun applySnapshot(snapshot: EditorSnapshot) {
        _uiState.update {
            it.copy(
                title = TextFieldValue(snapshot.title, TextRange(snapshot.title.length)),
                body = TextFieldValue(snapshot.body, TextRange(snapshot.body.length)),
            )
        }
        markDirty()
    }

    private fun markDirty() {
        hasUnsavedChanges = true
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo, saveState = SaveState.PENDING)
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Saves the latest text. Serialized by a mutex; always writes the newest state. */
    private suspend fun saveNow() {
        saveMutex.withLock {
            if (!hasUnsavedChanges) return
            hasUnsavedChanges = false
            val state = _uiState.value
            _uiState.update { it.copy(saveState = SaveState.SAVING) }
            val result = runCatching {
                withContext(NonCancellable) {
                    repository.saveContent(noteId, state.title.text, state.body.text)
                }
            }
            val newState = result.fold(
                onSuccess = { outcome ->
                    if (outcome == SaveOutcome.SKIPPED_EMPTY) SaveState.NEW else SaveState.SAVED
                },
                onFailure = {
                    hasUnsavedChanges = true
                    SaveState.ERROR
                },
            )
            // If the user typed again while saving, keep showing "Editing…".
            _uiState.update { it.copy(saveState = if (hasUnsavedChanges && newState != SaveState.ERROR) SaveState.PENDING else newState) }
        }
    }

    companion object {
        const val AUTOSAVE_DELAY_MS = 600L
        private const val KEY_DRAFT_ID = "draft_note_id"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                NoteEditorViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = container.noteRepository,
                    appScope = container.applicationScope,
                    clock = container.clock,
                )
            }
        }
    }
}
