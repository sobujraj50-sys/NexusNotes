package com.nexusnotes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.common.WallClock
import com.nexusnotes.core.database.NexusDatabase
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OfflineNoteRepository
import com.nexusnotes.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manual dependency container (one instance per process, owned by [NexusNotesApp]).
 * Chosen over Hilt for now: fewer build plugins, and the dependency graph is small.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = WallClock

    /** Outlives screens, so writes (e.g. the last autosave) finish after the user leaves a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: NexusDatabase by lazy { NexusDatabase.build(appContext) }

    val noteRepository: NoteRepository by lazy { OfflineNoteRepository(database.noteDao(), clock) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext.settingsDataStore) }
}

/** Access the container from a ViewModel factory initializer. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NexusNotesApp).container
