package com.nexusnotes.feature.vault

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.security.VaultSession
import com.nexusnotes.data.repository.VaultRepository
import com.nexusnotes.domain.vault.VaultAttachment
import com.nexusnotes.navigation.ARG_VAULT_ITEM_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

data class VaultEditorState(
    val loading: Boolean = true,
    val title: String = "",
    val body: String = "",
    val isStored: Boolean = false,
    val saveLabel: String = "",
    val attachments: List<VaultAttachment> = emptyList(),
    val missing: Set<String> = emptySet(),
    val attaching: Boolean = false,
    val busy: Boolean = false,
)

sealed interface VaultEditorEvent {
    data object Close : VaultEditorEvent
    data class OpenNote(val noteId: String) : VaultEditorEvent
    data class Message(val text: String) : VaultEditorEvent
}

class VaultEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val vault: VaultRepository,
    private val session: VaultSession,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val itemId: String = savedStateHandle.get<String>(ARG_VAULT_ITEM_ID)
        ?: savedStateHandle.get<String>(KEY_DRAFT)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT] = it }

    private val _state = MutableStateFlow(VaultEditorState())
    val state: StateFlow<VaultEditorState> = _state.asStateFlow()
    val unlocked: StateFlow<Boolean> = session.unlocked
    private val _events = Channel<VaultEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val mutex = Mutex()
    private var saveJob: Job? = null
    @Volatile private var dirty = false
    @Volatile private var gone = false // deleted or moved out: never save again

    /** Runs when the vault locks: saves the last edit with the key before it is wiped. */
    private val beforeLock: suspend (ByteArray) -> Unit = { key ->
        if (dirty && !gone) {
            val s = _state.value
            mutex.withLock {
                dirty = false
                vault.saveWithKey(key, itemId, s.title, s.body)
            }
        }
    }

    init {
        session.addBeforeLockHook(beforeLock)
        viewModelScope.launch {
            val note = runCatching { vault.getNote(itemId) }.getOrElse {
                _events.send(VaultEditorEvent.Close)
                return@launch
            }
            _state.update {
                it.copy(loading = false, title = note?.title.orEmpty(), body = note?.body.orEmpty(), isStored = note != null)
            }
        }
        viewModelScope.launch {
            vault.observeAttachments(itemId).collect { list ->
                _state.update { s -> s.copy(attachments = list, missing = list.filter(vault::isFileMissing).map { it.id }.toSet()) }
            }
        }
    }

    fun onTitle(v: String) = edit { it.copy(title = v) }
    fun onBody(v: String) = edit { it.copy(body = v) }

    private fun edit(change: (VaultEditorState) -> VaultEditorState) {
        if (gone || _state.value.loading) return
        _state.update { change(it).copy(saveLabel = "Editing…") }
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            save()
        }
    }

    private suspend fun save() {
        mutex.withLock {
            if (!dirty || gone) return
            dirty = false
            val s = _state.value
            val ok = runCatching { withContext(NonCancellable) { vault.saveNote(itemId, s.title, s.body) } }
            _state.update {
                it.copy(
                    isStored = it.isStored || ok.getOrDefault(false),
                    saveLabel = when {
                        ok.isFailure -> "Not saved (vault locked)"
                        dirty -> "Editing…"
                        ok.getOrDefault(false) -> "Encrypted & saved"
                        else -> ""
                    },
                )
            }
        }
    }

    fun flush() {
        saveJob?.cancel()
        if (dirty && !gone) appScope.launch { save() }
    }

    fun beforePicker() = vault.notePickerOpening()

    fun attach(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(attaching = true) }
            saveJob?.cancel()
            save()
            val s = _state.value
            vault.addAttachment(itemId, uri, s.title, s.body)
                .onSuccess { _state.update { it.copy(isStored = true) } }
                .onFailure { e -> _events.send(VaultEditorEvent.Message(e.message ?: "Could not add the file.")) }
            _state.update { it.copy(attaching = false) }
        }
    }

    suspend fun readBytes(a: VaultAttachment): ByteArray? = runCatching { vault.readAttachment(a) }.getOrNull()

    fun export(a: VaultAttachment, target: Uri) {
        viewModelScope.launch {
            vault.exportAttachment(a, target)
                .onSuccess { _events.send(VaultEditorEvent.Message("Saved an unencrypted copy of “${a.displayName}”.")) }
                .onFailure { _events.send(VaultEditorEvent.Message("Could not save the copy.")) }
        }
    }

    fun removeAttachment(a: VaultAttachment) {
        appScope.launch { vault.removeAttachment(a) }
    }

    fun moveOut() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            saveJob?.cancel()
            val result = withContext(NonCancellable) {
                save()
                gone = true
                vault.moveOutOfVault(itemId)
            }
            result.onSuccess { _events.send(VaultEditorEvent.OpenNote(it)) }
                .onFailure {
                    gone = false
                    _events.send(VaultEditorEvent.Message(it.message ?: "Could not move the note."))
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            saveJob?.cancel()
            gone = true
            withContext(NonCancellable) { vault.deleteNote(itemId) }
            _events.send(VaultEditorEvent.Close)
        }
    }

    override fun onCleared() {
        flush()
        session.removeBeforeLockHook(beforeLock)
    }

    companion object {
        private const val KEY_DRAFT = "vault_draft_id"

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                VaultEditorViewModel(createSavedStateHandle(), c.vaultRepository, c.vaultSession, c.applicationScope)
            }
        }
    }
}
