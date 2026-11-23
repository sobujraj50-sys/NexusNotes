package com.nexusnotes.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.data.repository.PromptRepository
import com.nexusnotes.data.repository.SettingsRepository
import com.nexusnotes.domain.NoteText
import com.nexusnotes.domain.model.FolderNode
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.Prompt
import com.nexusnotes.domain.model.SearchFilter
import com.nexusnotes.domain.model.TagWithCount
import com.nexusnotes.domain.search.SearchQuery
import com.nexusnotes.navigation.ARG_FOLDER_ID
import com.nexusnotes.navigation.ARG_TAG_ID
import com.nexusnotes.navigation.UNFILED_FOLDER_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchResult(
    val note: Note,
    val title: String,
    val titleHighlights: List<IntRange>,
    val snippet: SearchQuery.Snippet,
)

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val isLoading: Boolean = true,
    val results: List<SearchResult> = emptyList(),
    val folders: List<FolderNode> = emptyList(),
    val tags: List<TagWithCount> = emptyList(),
    val history: List<String> = emptyList(),
    val saveHistory: Boolean = false,
    val prompts: List<Prompt> = emptyList(),
) {
    val hasCriteria: Boolean
        get() = SearchQuery.terms(query).isNotEmpty() || filter.folderId != null || filter.unfiledOnly || filter.tagId != null

    /** Folders and tags whose names match the typed text — shown as quick filters. */
    val matchingFolders: List<FolderNode>
        get() = if (query.isBlank()) emptyList() else folders.filter { it.folder.name.contains(query.trim(), ignoreCase = true) }.take(5)
    val matchingTags: List<TagWithCount>
        get() = if (query.isBlank()) emptyList() else tags.filter { it.tag.name.contains(query.trim().removePrefix("#"), ignoreCase = true) }.take(8)
}

private data class SearchInputs(
    val query: String,
    val filter: SearchFilter,
    val results: List<SearchResult>,
    val prompts: List<Prompt>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    organization: OrganizationRepository,
    prompts: PromptRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(
        run {
            val folderArg = savedStateHandle.get<String>(ARG_FOLDER_ID)
            SearchFilter(
                folderId = folderArg?.takeIf { it != UNFILED_FOLDER_ID },
                unfiledOnly = folderArg == UNFILED_FOLDER_ID,
                tagId = savedStateHandle.get<String>(ARG_TAG_ID),
            )
        },
    )

    private val results = combine(query.debounce(200).map { it.trim() }.distinctUntilChanged(), filter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            val terms = SearchQuery.terms(q)
            val hasCriteria = terms.isNotEmpty() || f.folderId != null || f.unfiledOnly || f.tagId != null
            if (!hasCriteria) {
                kotlinx.coroutines.flow.flowOf(emptyList<SearchResult>())
            } else {
                notes.search(q, f).map { list -> list.map { toResult(it, terms) } }
            }
        }

    /** Prompts are searched only for plain text searches (folder/tag filters apply to notes). */
    private val promptResults = combine(query.debounce(200).map { it.trim() }.distinctUntilChanged(), filter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            if (q.isBlank() || f.folderId != null || f.unfiledOnly || f.tagId != null) {
                kotlinx.coroutines.flow.flowOf(emptyList<Prompt>())
            } else {
                prompts.search(q, limit = 20)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        combine(query, filter, results, promptResults) { q, f, r, p -> SearchInputs(q, f, r, p) },
        organization.observeFolderTree(),
        organization.observeTagsWithCounts(),
        settings.settings,
    ) { input, folders, tags, appSettings ->
        SearchUiState(
            query = input.query,
            filter = input.filter,
            isLoading = false,
            results = input.results,
            prompts = input.prompts,
            folders = folders,
            tags = tags,
            history = if (appSettings.saveSearchHistory) appSettings.searchHistory else emptyList(),
            saveHistory = appSettings.saveSearchHistory,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFolderFilter(folderId: String?, unfiledOnly: Boolean = false) {
        filter.value = filter.value.copy(folderId = folderId, unfiledOnly = unfiledOnly)
    }

    fun setTagFilter(tagId: String?) {
        filter.value = filter.value.copy(tagId = tagId)
    }

    fun setIncludeArchived(include: Boolean) {
        filter.value = filter.value.copy(includeArchived = include)
    }

    /** Remembers the query (only if the user enabled search history). */
    fun commitQuery() {
        val q = query.value
        viewModelScope.launch { settings.recordSearch(q) }
    }

    fun clearHistory() {
        viewModelScope.launch { settings.clearSearchHistory() }
    }

    private fun toResult(note: Note, terms: List<String>): SearchResult {
        val title = NoteText.displayTitle(note.title, note.body)
        return SearchResult(
            note = note,
            title = title,
            titleHighlights = SearchQuery.highlightRanges(title, terms),
            snippet = SearchQuery.snippet(note.body, terms),
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SearchViewModel(createSavedStateHandle(), c.noteRepository, c.organizationRepository, c.promptRepository, c.settingsRepository)
            }
        }
    }
}
