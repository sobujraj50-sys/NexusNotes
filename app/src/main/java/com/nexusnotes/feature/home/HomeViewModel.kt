package com.nexusnotes.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.domain.model.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val pinned: List<Note> = emptyList(),
    val recent: List<Note> = emptyList(),
    val favorites: List<Note> = emptyList(),
)

class HomeViewModel(repository: NoteRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observePinned(),
        repository.observeRecent(RECENT_QUERY_LIMIT),
        repository.observeFavorites(),
    ) { pinned, recent, favorites ->
        HomeUiState(
            isLoading = false,
            pinned = pinned.take(SECTION_LIMIT),
            // Pinned notes already have their own section above.
            recent = recent.filterNot { it.isPinned }.take(SECTION_LIMIT),
            favorites = favorites.take(SECTION_LIMIT),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        private const val SECTION_LIMIT = 5
        private const val RECENT_QUERY_LIMIT = 15

        val Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().noteRepository) }
        }
    }
}
