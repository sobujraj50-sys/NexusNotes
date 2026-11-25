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
import com.nexusnotes.data.repository.AttachmentRepository
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.domain.EditHistory
import com.nexusnotes.domain.EditorSnapshot
import com.nexusnotes.domain.markdown.FormatAction
import com.nexusnotes.domain.markdown.MarkdownFormatter
import com.nexusnotes.domain.markdown.TextEdit
import com.nexusnotes.domain.model.Attachment
import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.FolderNode
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.Tag
import com.nexusnotes.domain.model.SaveOutcome
import com.nexusnotes.navigation.ARG_FOLDER_ID
import com.nexusnotes.navigation.ARG_NOTE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** Folder chosen for a note that is not stored yet (applied on first save). */
    val pendingFolderId: String? = null,
    val folderTree: List<FolderNode> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    /** Attachments whose file is no longer on the phone. */
    val missingAttachments: Set<String> = emptySet(),
    val isAttaching: Boolean = false,
) {
    val folderId: String? get() = if (note != null) note.folderId else pendingFolderId
    val folder: Folder? get() = folderId?.let { id -> folderTree.firstOrNull { it.folder.id == id }?.folder }

    val isReadOnly: Boolean get() = note?.isInTrash == true
    val isStored: Boolean get() = note != null
}

sealed interface EditorEvent {
    data object Close : EditorEvent
    data class OpenNote(val id: String) : EditorEvent
    data class Message(val text: String) : EditorEvent
    data class CloseWithMessage(val text: String) : EditorEvent
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
    private val organization: OrganizationRepository,
    private val attachments: AttachmentRepository,
    private val vault: com.nexusnotes.data.repository.VaultRepository,
    private val exports: com.nexusnotes.data.repository.ExportRepository,
    private val reminders: com.nexusnotes.data.repository.ReminderRepository,
    private val templates: com.nexusnotes.data.repository.TemplateRepository,
    settings: com.nexusnotes.data.repository.SettingsRepository,
    private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    val noteId: String = savedStateHandle.get<String>(ARG_NOTE_ID)
        ?: savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT_ID] = it }

    private val _uiState = MutableStateFlow(
        EditorUiState(pendingFolderId = savedStateHandle.get<String>(ARG_FOLDER_ID)),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val history = EditHistory(EditorSnapshot("", ""))
    private val saveMutex = Mutex()
    private var saveJob: Job? = null

    @Volatile
    private var hasUnsavedChanges = false

    /** Set once the note was moved into the vault: the plaintext must never be written again. */
    @Volatile private var movedToVault = false

    init {
        viewModelScope.launch {
            organization.observeFolderTree().collect { tree -> _uiState.update { it.copy(folderTree = tree) } }
        }
        viewModelScope.launch {
            organization.observeTagsForNote(noteId).collect { tags -> _uiState.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            organization.observeTags().collect { tags -> _uiState.update { it.copy(allTags = tags) } }
        }
        viewModelScope.launch {
            attachments.observeForNote(noteId).collect { list ->
                val missing = attachments.missingIds(list)
                _uiState.update { it.copy(attachments = list, missingAttachments = missing) }
            }
        }
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

    /** Moves the note to [folderId] (null = Unfiled). For an unsaved note the choice is applied on first save. */
    fun setFolder(folderId: String?) {
        val note = _uiState.value.note
        if (note == null) {
            _uiState.update { it.copy(pendingFolderId = folderId) }
        } else {
            appScope.launch { repository.setFolder(note.id, folderId) }
        }
    }

    /** Adds a tag by name (created if new). Reports a user-facing error or null. */
    fun addTag(name: String, onResult: (String?) -> Unit) {
        val note = _uiState.value.note
        if (note == null) {
            onResult("Write something first — tags can be added once the note is saved.")
            return
        }
        viewModelScope.launch { onResult(organization.addTagToNote(note.id, name).exceptionOrNull()?.message) }
    }

    fun removeTag(tagId: String) = withStoredNote { organization.removeTagFromNote(it.id, tagId) }

    // ---- Attachments ----

    /** Attaches a picked image or file. A new note is saved first so the attachment has a home. */
    fun attach(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAttaching = true) }
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            val result = withContext(NonCancellable) { attachments.addFromUri(noteId, uri, _uiState.value.pendingFolderId) }
            _uiState.update { it.copy(isAttaching = false) }
            result.exceptionOrNull()?.let { _events.send(EditorEvent.Message(it.message ?: "Could not attach the file.")) }
        }
    }

    fun newRecording(): Pair<String, java.io.File> = attachments.newRecording()

    fun saveRecording(id: String, file: java.io.File, durationMs: Long) {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            val result = withContext(NonCancellable) {
                attachments.addRecording(noteId, id, file, durationMs, _uiState.value.pendingFolderId)
            }
            result.exceptionOrNull()?.let { _events.send(EditorEvent.Message(it.message ?: "Could not save the recording.")) }
        }
    }

    fun removeAttachment(id: String) {
        appScope.launch { attachments.remove(id) }
    }

    fun fileFor(attachment: Attachment): java.io.File = attachments.fileFor(attachment)

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

    // ---- Reminder, template, AI ----

    val reminder: StateFlow<com.nexusnotes.domain.reminder.Reminder?> =
        reminders.observe(noteId).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    val aiToolsEnabled: StateFlow<Boolean> = settings.settings.map { it.aiToolsEnabled }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    val exactAlarmsAllowed: Boolean get() = reminders.exactAlarmsAllowed

    fun setReminder(at: Long, repeat: com.nexusnotes.domain.reminder.ReminderRepeat) {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            if (repository.getNote(noteId) == null) {
                _events.send(EditorEvent.Message("Write something first, then set a reminder."))
                return@launch
            }
            runCatching { reminders.set(noteId, at, repeat) }
                .onSuccess { _events.send(EditorEvent.Message("Reminder set.")) }
                .onFailure { _events.send(EditorEvent.Message(it.message ?: "Could not set the reminder.")) }
        }
    }

    fun removeReminder() {
        appScope.launch { reminders.remove(noteId) }
    }

    fun saveAsTemplate(name: String) {
        viewModelScope.launch {
            val s = _uiState.value
            templates.saveFromNote(name, s.title.text, s.body.text)
            _events.send(EditorEvent.Message("Saved as template “${name.trim().ifBlank { "My template" }}”."))
        }
    }

    // ---- Export ----

    fun exportNote(format: com.nexusnotes.domain.backup.ExportFormat, target: android.net.Uri) {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            exports.export(format, target, "note", setOf(noteId))
                .onSuccess { _events.send(EditorEvent.Message("Exported.")) }
                .onFailure { _events.send(EditorEvent.Message(it.message ?: "Export failed.")) }
        }
    }

    // ---- Vault ----

    val vaultSetUp = vault.isSetUp
    val vaultUnlocked = vault.unlocked

    /** Returns an error message, or null when unlocked. */
    suspend fun unlockVault(pin: String): String? {
        val chars = pin.toCharArray()
        val r = vault.unlock(chars)
        chars.fill(' ')
        return when (r) {
            is com.nexusnotes.domain.vault.UnlockResult.Success -> null
            is com.nexusnotes.domain.vault.UnlockResult.WrongPin -> if (r.lockedUntil > 0) "Wrong PIN. Too many attempts — wait and try again." else "Wrong PIN."
            is com.nexusnotes.domain.vault.UnlockResult.LockedOut -> "Too many wrong attempts. Try again later."
            com.nexusnotes.domain.vault.UnlockResult.KeysLost -> "The vault can't be opened on this phone. Open Private Vault for details."
            com.nexusnotes.domain.vault.UnlockResult.NotSetUp -> "Set up the vault first."
        }
    }

    /** Encrypts this note into the vault and deletes the plaintext original. Vault must be unlocked. */
    fun moveToVault() {
        viewModelScope.launch {
            saveJob?.cancel()
            val result = withContext(NonCancellable) {
                saveNow()
                if (!_uiState.value.isStored) return@withContext Result.failure<String>(IllegalStateException("Write something first."))
                movedToVault = true
                hasUnsavedChanges = false
                vault.moveNoteToVault(noteId)
            }
            result.onSuccess {
                _events.send(EditorEvent.CloseWithMessage("Moved to the vault and encrypted."))
            }.onFailure {
                movedToVault = false
                _events.send(EditorEvent.Message(it.message ?: "Could not move the note."))
            }
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
        if (movedToVault) return
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
            if (!hasUnsavedChanges || movedToVault) return
            hasUnsavedChanges = false
            val state = _uiState.value
            _uiState.update { it.copy(saveState = SaveState.SAVING) }
            val result = runCatching {
                withContext(NonCancellable) {
                    repository.saveContent(noteId, state.title.text, state.body.text, state.pendingFolderId)
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
                    organization = container.organizationRepository,
                    attachments = container.attachmentRepository,
                    vault = container.vaultRepository,
                    exports = container.exportRepository,
                    reminders = container.reminderRepository,
                    templates = container.templateRepository,
                    settings = container.settingsRepository,
                    appScope = container.applicationScope,
                    clock = container.clock,
                )
            }
        }
    }
}
