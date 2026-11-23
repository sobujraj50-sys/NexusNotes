package com.nexusnotes.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.FolderNode
import com.nexusnotes.domain.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Folder tree + all folder actions. Results report a user-facing error message or null on success. */
class FoldersViewModel(
    private val organization: OrganizationRepository,
    notes: NoteRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val tree: StateFlow<List<FolderNode>> =
        organization.observeFolderTree().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unfiledCount: StateFlow<Int> =
        notes.observeUnfiled().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun create(name: String, parentId: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(organization.createFolder(name, parentId).exceptionOrNull()?.message) }
    }

    fun rename(id: String, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(organization.renameFolder(id, name).exceptionOrNull()?.message) }
    }

    fun setColor(id: String, colorIndex: Int?) {
        appScope.launch { organization.setFolderColor(id, colorIndex) }
    }

    fun move(id: String, newParentId: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(organization.moveFolder(id, newParentId).exceptionOrNull()?.message) }
    }

    fun delete(id: String) {
        appScope.launch { organization.deleteFolder(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                FoldersViewModel(c.organizationRepository, c.noteRepository, c.applicationScope)
            }
        }
    }
}

data class FolderDetailUiState(
    val isLoading: Boolean = true,
    val folder: Folder? = null,
    val isUnfiled: Boolean = false,
    val notFound: Boolean = false,
    val subfolders: List<FolderNode> = emptyList(),
    val notes: List<Note> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class FolderDetailViewModel(
    folderId: String,
    organization: OrganizationRepository,
    notes: NoteRepository,
) : ViewModel() {

    private val isUnfiled = folderId == com.nexusnotes.navigation.UNFILED_FOLDER_ID

    val uiState: StateFlow<FolderDetailUiState> = if (isUnfiled) {
        notes.observeUnfiled().map { FolderDetailUiState(isLoading = false, isUnfiled = true, notes = it) }
    } else {
        combine(
            organization.observeFolder(folderId),
            organization.observeFolderTree(),
            notes.observeInFolder(folderId),
        ) { folder, tree, list ->
            FolderDetailUiState(
                isLoading = false,
                folder = folder,
                notFound = folder == null,
                subfolders = tree.filter { it.folder.parentId == folderId }.map { it.copy(depth = 0) },
                notes = list,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderDetailUiState())

    companion object {
        fun factory(folderId: String) = viewModelFactory {
            initializer {
                val c = appContainer()
                FolderDetailViewModel(folderId, c.organizationRepository, c.noteRepository)
            }
        }
    }
}
