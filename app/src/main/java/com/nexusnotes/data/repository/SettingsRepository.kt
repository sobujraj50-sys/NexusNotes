package com.nexusnotes.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexusnotes.domain.model.AppSettings
import com.nexusnotes.domain.model.NoteSort
import com.nexusnotes.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App preferences stored with Jetpack DataStore (file: settings.preferences_pb). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = enumOrDefault(prefs[THEME_MODE], ThemeMode.SYSTEM),
                dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
                noteSort = enumOrDefault(prefs[NOTE_SORT], NoteSort.MODIFIED),
                saveSearchHistory = prefs[SAVE_SEARCH_HISTORY] ?: false,
                searchHistory = prefs[SEARCH_HISTORY]?.split(HISTORY_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setNoteSort(sort: NoteSort) {
        dataStore.edit { it[NOTE_SORT] = sort.name }
    }

    /** Off by default: search terms are only remembered if the user turns this on. */
    suspend fun setSaveSearchHistory(enabled: Boolean) {
        dataStore.edit {
            it[SAVE_SEARCH_HISTORY] = enabled
            if (!enabled) it.remove(SEARCH_HISTORY)
        }
    }

    suspend fun recordSearch(query: String) {
        val clean = query.replace(Regex("\\s+"), " ").trim().take(80)
        if (clean.isEmpty()) return
        dataStore.edit { prefs ->
            if (prefs[SAVE_SEARCH_HISTORY] != true) return@edit
            val existing = prefs[SEARCH_HISTORY]?.split(HISTORY_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_HISTORY)
            prefs[SEARCH_HISTORY] = updated.joinToString(HISTORY_SEPARATOR)
        }
    }

    suspend fun clearSearchHistory() {
        dataStore.edit { it.remove(SEARCH_HISTORY) }
    }

    private companion object {
        val SAVE_SEARCH_HISTORY = booleanPreferencesKey("save_search_history")
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
        const val HISTORY_SEPARATOR = "\u001F"
        const val MAX_HISTORY = 10

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTE_SORT = stringPreferencesKey("note_sort")

        inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
    }
}
