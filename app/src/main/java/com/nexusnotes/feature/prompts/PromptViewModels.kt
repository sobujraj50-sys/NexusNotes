package com.nexusnotes.feature.prompts

import android.net.Uri
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
import com.nexusnotes.data.repository.PromptRepository
import com.nexusnotes.domain.EditHistory
import com.nexusnotes.domain.EditorSnapshot
import com.nexusnotes.domain.model.Prompt
import com.nexusnotes.domain.model.PromptCategory
import com.nexusnotes.domain.model.PromptCategoryRef
import com.nexusnotes.domain.model.PromptVersion
import com.nexusnotes.domain.model.SaveOutcome
import com.nexusnotes.domain.prompt.PromptExchange
import com.nexusnotes.domain.prompt.PromptTemplate
import com.nexusnotes.feature.editor.SaveState
import com.nexusnotes.navigation.ARG_CATEGORY
import com.nexusnotes.navigation.ARG_PROMPT_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

const val FILTER_ALL = "__all__"
const val FILTER_FAVORITES = "__favorites__"

// =====================================================================================
// Library
// =====================================================================================

data class PromptLibraryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: String = FILTER_ALL,
    val prompts: List<Prompt> = emptyList(),
    val totalCount: Int = 0,
    val categories: List<PromptCategoryRef> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    val favoritesCount: Int = 0,
) {
    fun categoryName(key: String): String = categories.firstOrNull { it.key == key }?.name ?: "General Prompts"
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PromptLibraryViewModel(
    private val repository: PromptRepository,
    private val contentResolver: android.content.ContentResolver,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(FILTER_ALL)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val matching = query.debounce(200).flatMapLatest { q ->
        if (q.isBlank()) repository.observePrompts() else repository.search(q)
    }

    val uiState: StateFlow<PromptLibraryUiState> = combine(
        query,
        filter,
        matching,
        repository.observePrompts(),
        repository.observeCategories(),
    ) { q, f, found, all, categories ->
        val filtered = when (f) {
            FILTER_ALL -> found
            FILTER_FAVORITES -> found.filter { it.isFavorite }
            else -> found.filter { it.categoryKey == f }
        }
        PromptLibraryUiState(
            isLoading = false,
            query = q,
            filter = f,
            prompts = filtered,
            totalCount = all.size,
            categories = categories,
            counts = all.groupingBy { it.categoryKey }.eachCount(),
            favoritesCount = all.count { it.isFavorite },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PromptLibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFilter(key: String) {
        filter.value = key
    }

    fun addExamples() {
        viewModelScope.launch {
            val n = repository.addExamples()
            _messages.send("Added $n example prompts")
        }
    }

    fun createCategory(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val r = repository.createCategory(name)
            r.getOrNull()?.let { filter.value = it }
            onResult(r.exceptionOrNull()?.message)
        }
    }

    fun renameCategory(key: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(repository.renameCategory(key, name).exceptionOrNull()?.message) }
    }

    fun deleteCategory(key: String) {
        viewModelScope.launch {
            repository.deleteCategory(key)
            if (filter.value == key) filter.value = FILTER_ALL
            _messages.send("Category deleted. Its prompts moved to General Prompts.")
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = repository.exportJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the file.")
                }
            }
            _messages.send(
                result.fold(
                    onSuccess = { "Exported ${uiState.value.totalCount} prompt(s)" },
                    onFailure = { "Export failed: ${it.message ?: "unknown error"}" },
                ),
            )
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val reader = input.bufferedReader(Charsets.UTF_8)
                        val buffer = CharArray(PromptExchange.MAX_FILE_CHARS + 1)
                        var total = 0
                        while (total < buffer.size) {
                            val n = reader.read(buffer, total, buffer.size - total)
                            if (n < 0) break
                            total += n
                        }
                        if (total > PromptExchange.MAX_FILE_CHARS) error("The file is too large.")
                        String(buffer, 0, total)
                    } ?: error("Could not open the file.")
                }
                repository.importJson(text).getOrThrow()
            }
            _messages.send(
                result.fold(
                    onSuccess = { r ->
                        buildString {
                            append("Imported ${r.added} prompt(s)")
                            if (r.addedAsCopy > 0) append(", ${r.addedAsCopy} as copies")
                            if (r.skippedSame > 0) append(", ${r.skippedSame} already present")
                            if (r.invalid > 0) append(", ${r.invalid} invalid skipped")
                        }
                    },
                    onFailure = { "Import failed: ${it.message ?: "unknown error"}" },
                ),
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                PromptLibraryViewModel(c.promptRepository, c.contentResolver)
            }
        }
    }
}

// =====================================================================================
// Editor
// =====================================================================================

data class PromptEditorUiState(
    val isLoading: Boolean = true,
    val title: TextFieldValue = TextFieldValue(),
    val template: TextFieldValue = TextFieldValue(),
    val prompt: Prompt? = null,
    val pendingCategory: String = PromptCategory.GENERAL.name,
    val categories: List<PromptCategoryRef> = emptyList(),
    val defaults: Map<String, String> = emptyMap(),
    val versions: List<PromptVersion> = emptyList(),
    val saveState: SaveState = SaveState.NEW,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val isStored: Boolean get() = prompt != null
    val categoryKey: String get() = prompt?.categoryKey ?: pendingCategory
    val categoryName: String get() = categories.firstOrNull { it.key == categoryKey }?.name ?: "General Prompts"
}

sealed interface PromptEditorEvent {
    data object Close : PromptEditorEvent
    data class OpenPrompt(val id: String) : PromptEditorEvent
}

/** Same autosave model as the note editor (600 ms pause, flushed on stop and close). */
class PromptEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PromptRepository,
    private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {

    val promptId: String = savedStateHandle.get<String>(ARG_PROMPT_ID)
        ?: savedStateHandle.get<String>(KEY_DRAFT)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT] = it }

    private val _uiState = MutableStateFlow(
        PromptEditorUiState(
            pendingCategory = savedStateHandle.get<String>(ARG_CATEGORY)
                ?.takeIf { it != FILTER_ALL && it != FILTER_FAVORITES }
                ?: PromptCategory.GENERAL.name,
        ),
    )
    val uiState: StateFlow<PromptEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<PromptEditorEvent>(Channel.BUFFERED)
    val events: Flow<PromptEditorEvent> = _events.receiveAsFlow()

    private val history = EditHistory(EditorSnapshot("", ""))
    private val saveMutex = Mutex()
    private var saveJob: Job? = null

    @Volatile
    private var dirty = false

    init {
        viewModelScope.launch { repository.observeCategories().collect { c -> _uiState.update { it.copy(categories = c) } } }
        viewModelScope.launch { repository.observeVariableDefaults(promptId).collect { d -> _uiState.update { it.copy(defaults = d) } } }
        viewModelScope.launch { repository.observeVersions(promptId).collect { v -> _uiState.update { it.copy(versions = v) } } }
        viewModelScope.launch {
            val stored = repository.getPrompt(promptId)
            loadText(stored?.title.orEmpty(), stored?.template.orEmpty())
            _uiState.update { it.copy(isLoading = false, prompt = stored, saveState = if (stored == null) SaveState.NEW else SaveState.SAVED) }
            repository.observePrompt(promptId).collect { p -> _uiState.update { it.copy(prompt = p) } }
        }
    }

    private fun loadText(title: String, template: String) {
        history.reset(EditorSnapshot(title, template))
        _uiState.update {
            it.copy(
                title = TextFieldValue(title, TextRange(title.length)),
                template = TextFieldValue(template),
                canUndo = false,
                canRedo = false,
            )
        }
    }

    fun onTitleChange(v: TextFieldValue) {
        val old = _uiState.value
        if (old.isLoading) return
        _uiState.update { it.copy(title = v) }
        if (v.text != old.title.text) edited()
    }

    fun onTemplateChange(v: TextFieldValue) {
        val old = _uiState.value
        if (old.isLoading) return
        _uiState.update { it.copy(template = v) }
        if (v.text != old.template.text) edited()
    }

    /** Inserts `{{name}}` at the cursor (replacing any selection). */
    fun insertVariable(name: String) {
        val s = _uiState.value
        if (s.isLoading) return
        val t = s.template
        val insert = "{{$name}}"
        val text = t.text.substring(0, t.selection.min) + insert + t.text.substring(t.selection.max)
        val cursor = t.selection.min + insert.length
        _uiState.update { it.copy(template = TextFieldValue(text, TextRange(cursor))) }
        edited()
    }

    fun undo() {
        history.undo()?.let(::applySnapshot)
    }

    fun redo() {
        history.redo()?.let(::applySnapshot)
    }

    private fun applySnapshot(s: EditorSnapshot) {
        _uiState.update {
            it.copy(
                title = TextFieldValue(s.title, TextRange(s.title.length)),
                template = TextFieldValue(s.body, TextRange(s.body.length)),
            )
        }
        markDirty()
    }

    private fun edited() {
        val s = _uiState.value
        history.record(EditorSnapshot(s.title.text, s.template.text), clock.now())
        markDirty()
    }

    private fun markDirty() {
        dirty = true
        _uiState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo, saveState = SaveState.PENDING) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveNow()
        }
    }

    fun flush() {
        saveJob?.cancel()
        if (dirty) appScope.launch { saveNow() }
    }

    override fun onCleared() {
        flush()
    }

    private suspend fun saveNow() {
        saveMutex.withLock {
            if (!dirty) return
            dirty = false
            val s = _uiState.value
            _uiState.update { it.copy(saveState = SaveState.SAVING) }
            val result = runCatching {
                withContext(NonCancellable) { repository.saveContent(promptId, s.title.text, s.template.text, s.pendingCategory) }
            }
            val state = result.fold(
                onSuccess = { if (it == SaveOutcome.SKIPPED_EMPTY) SaveState.NEW else SaveState.SAVED },
                onFailure = {
                    dirty = true
                    SaveState.ERROR
                },
            )
            _uiState.update { it.copy(saveState = if (dirty && state != SaveState.ERROR) SaveState.PENDING else state) }
        }
    }

    /** Saves now and reports whether the prompt exists (needed before opening the runner). */
    fun saveThen(action: (stored: Boolean) -> Unit) {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            action(repository.getPrompt(promptId) != null)
        }
    }

    fun setCategory(key: String) {
        val p = _uiState.value.prompt
        if (p == null) {
            _uiState.update { it.copy(pendingCategory = key) }
        } else {
            appScope.launch { repository.setCategory(p.id, key) }
        }
    }

    fun createCategory(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val r = repository.createCategory(name)
            r.getOrNull()?.let { setCategory(it) }
            onResult(r.exceptionOrNull()?.message)
        }
    }

    fun toggleFavorite() {
        val p = _uiState.value.prompt ?: return
        appScope.launch { repository.setFavorite(p.id, !p.isFavorite) }
    }

    fun setDefault(name: String, value: String) {
        if (_uiState.value.prompt == null) return
        _uiState.update { it.copy(defaults = it.defaults + (name to value)) }
        appScope.launch { repository.setVariableDefault(promptId, name, value) }
    }

    fun restoreVersion(versionId: String) {
        viewModelScope.launch {
            saveJob?.cancel()
            withContext(NonCancellable) { saveNow() }
            if (repository.restoreVersion(promptId, versionId)) {
                val p = repository.getPrompt(promptId)
                if (p != null) {
                    loadText(p.title, p.template)
                    _uiState.update { it.copy(saveState = SaveState.SAVED) }
                }
            }
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            saveJob?.cancel()
            val id = withContext(NonCancellable) {
                saveNow()
                repository.duplicate(promptId)
            }
            if (id != null) _events.send(PromptEditorEvent.OpenPrompt(id))
        }
    }

    /** Caller confirmed. */
    fun delete() {
        viewModelScope.launch {
            saveJob?.cancel()
            dirty = false
            withContext(NonCancellable) { repository.delete(promptId) }
            _events.send(PromptEditorEvent.Close)
        }
    }

    companion object {
        private const val KEY_DRAFT = "draft_prompt_id"

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                PromptEditorViewModel(createSavedStateHandle(), c.promptRepository, c.applicationScope, c.clock)
            }
        }
    }
}

// =====================================================================================
// Runner
// =====================================================================================

data class PromptRunnerUiState(
    val isLoading: Boolean = true,
    val prompt: Prompt? = null,
    val variables: List<String> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val result: PromptTemplate.FillResult? = null,
)

/** Fills a template. Works on a copy only — the stored prompt is never changed here. */
class PromptRunnerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PromptRepository,
) : ViewModel() {

    private val promptId: String? = savedStateHandle.get<String>(ARG_PROMPT_ID)
    private val _uiState = MutableStateFlow(PromptRunnerUiState())
    val uiState: StateFlow<PromptRunnerUiState> = _uiState.asStateFlow()
    private var defaults: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            val id = promptId
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            defaults = repository.getVariableDefaults(id)
            repository.observePrompt(id).collect { prompt ->
                _uiState.update { state ->
                    val vars = prompt?.let { PromptTemplate.variables(it.template) }.orEmpty()
                    // Keep what the user typed; prefill new variables with their defaults.
                    val values = vars.associateWith { name -> state.values[name] ?: defaults[name].orEmpty() }
                    state.copy(
                        isLoading = false,
                        prompt = prompt,
                        variables = vars,
                        values = values,
                        result = prompt?.let { PromptTemplate.fill(it.template, values) },
                    )
                }
            }
        }
    }

    fun setValue(name: String, value: String) {
        _uiState.update { s ->
            val values = s.values + (name to value)
            s.copy(values = values, result = s.prompt?.let { PromptTemplate.fill(it.template, values) })
        }
    }

    fun resetToDefaults() {
        _uiState.update { s ->
            val values = s.variables.associateWith { defaults[it].orEmpty() }
            s.copy(values = values, result = s.prompt?.let { PromptTemplate.fill(it.template, values) })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PromptRunnerViewModel(createSavedStateHandle(), appContainer().promptRepository) }
        }
    }
}
