package com.nexusnotes.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.SettingsRepository
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.NoteSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteListType { ALL, FAVORITES, ARCHIVE, TRASH }

data class NoteListUiState(
    val isLoading: Boolean = true,
    val notes: List<Note> = emptyList(),
    val sort: NoteSort = NoteSort.MODIFIED,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModel(
    type: NoteListType,
    private val repository: NoteRepository,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val uiState: StateFlow<NoteListUiState> = when (type) {
        NoteListType.ALL -> settings.settings
            .map { it.noteSort }
            .distinctUntilChanged()
            .flatMapLatest { sort -> repository.observeActive(sort).toState(sort) }
        NoteListType.FAVORITES -> repository.observeFavorites().toState()
        NoteListType.ARCHIVE -> repository.observeArchived().toState()
        NoteListType.TRASH -> repository.observeTrash().toState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListUiState())

    private fun Flow<List<Note>>.toState(sort: NoteSort = NoteSort.MODIFIED): Flow<NoteListUiState> =
        map { NoteListUiState(isLoading = false, notes = it, sort = sort) }

    fun setSort(sort: NoteSort) {
        viewModelScope.launch { settings.setNoteSort(sort) }
    }

    /** Permanently deletes everything in Trash. The screen asks for confirmation first. */
    fun emptyTrash() {
        appScope.launch { repository.emptyTrash() }
    }

    companion object {
        fun factory(type: NoteListType) = viewModelFactory {
            initializer {
                val container = appContainer()
                NoteListViewModel(
                    type = type,
                    repository = container.noteRepository,
                    settings = container.settingsRepository,
                    appScope = container.applicationScope,
                )
            }
        }
    }
}
