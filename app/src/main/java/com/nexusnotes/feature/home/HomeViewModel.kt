package com.nexusnotes.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.domain.model.FolderNode
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
    val topFolders: List<FolderNode> = emptyList(),
)

class HomeViewModel(repository: NoteRepository, organization: OrganizationRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observePinned(),
        repository.observeRecent(RECENT_QUERY_LIMIT),
        repository.observeFavorites(),
        organization.observeFolderTree(),
    ) { pinned, recent, favorites, folders ->
        HomeUiState(
            topFolders = folders.filter { it.depth == 0 }.take(8),
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
            initializer {
                val c = appContainer()
                HomeViewModel(c.noteRepository, c.organizationRepository)
            }
        }
    }
}
