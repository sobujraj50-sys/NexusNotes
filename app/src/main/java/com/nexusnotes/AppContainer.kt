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
import com.nexusnotes.core.storage.AttachmentStore
import com.nexusnotes.data.repository.AttachmentRepository
import com.nexusnotes.data.repository.BackupRepository
import com.nexusnotes.data.repository.ExportRepository
import com.nexusnotes.data.repository.NoteRepository
import com.nexusnotes.data.repository.OfflineNoteRepository
import com.nexusnotes.data.repository.OrganizationRepository
import com.nexusnotes.data.repository.PromptRepository
import com.nexusnotes.data.repository.SettingsRepository
import com.nexusnotes.data.repository.ReminderRepository
import com.nexusnotes.data.repository.TemplateRepository
import com.nexusnotes.core.backup.AutoBackupScheduler
import com.nexusnotes.core.reminders.ReminderAlarms
import com.nexusnotes.core.widget.NotesWidget
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import com.nexusnotes.data.repository.VaultRepository
import com.nexusnotes.core.security.BiometricKey
import com.nexusnotes.core.security.FileVaultMetaStore
import com.nexusnotes.core.security.KeystoreDeviceKey
import com.nexusnotes.core.security.VaultFileStore
import com.nexusnotes.core.security.VaultSession
import com.nexusnotes.domain.vault.VaultKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val noteRepository: NoteRepository by lazy {
        OfflineNoteRepository(database.noteDao(), clock) { attachmentRepository }
    }

    val attachmentStore: AttachmentStore by lazy { AttachmentStore(appContext) }

    val attachmentRepository: AttachmentRepository by lazy {
        AttachmentRepository(database.attachmentDao(), database.noteDao(), attachmentStore, clock)
    }

    val organizationRepository: OrganizationRepository by lazy {
        OrganizationRepository(database.folderDao(), database.tagDao(), clock)
    }

    val promptRepository: PromptRepository by lazy { PromptRepository(database.promptDao(), clock) }

    /** For reading/writing files the user picks (import/export). */
    val contentResolver: android.content.ContentResolver get() = appContext.contentResolver

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext.settingsDataStore) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(appContext, database, attachmentStore, attachmentRepository, vaultRepository, clock, BuildConfig.VERSION_NAME) {
            reminderRepository.rearmAll()
        }
    }

    val exportRepository: ExportRepository by lazy {
        ExportRepository(database, attachmentStore, appContext.contentResolver, clock) { kind, name, summary, ok ->
            backupRepository.record(kind, name, 0, summary, ok)
        }
    }

    val reminderAlarms: ReminderAlarms by lazy { ReminderAlarms(appContext) }

    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao(), reminderAlarms, clock) }

    val templateRepository: TemplateRepository by lazy { TemplateRepository(database.templateDao(), noteRepository, clock) }

    val autoBackupScheduler: AutoBackupScheduler by lazy { AutoBackupScheduler(appContext, settingsRepository) }

    /** Keeps the home-screen widget in step with notes (only does work if a widget exists). */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    internal fun startWidgetUpdates() {
        if (widgetJob != null) return
        widgetJob = applicationScope.launch {
            combine(noteRepository.observePinned(), noteRepository.observeRecent(8)) { pinned, recent ->
                (pinned + recent).distinctBy { it.id }.take(4)
            }.distinctUntilChanged().debounce(500).collect { notes ->
                runCatching { NotesWidget.render(appContext, notes) }
            }
        }
    }
    private var widgetJob: kotlinx.coroutines.Job? = null

    /** In-memory vault key holder. Locked at process start. */
    val vaultSession = VaultSession(applicationScope)

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(
            database = database,
            keys = VaultKeyManager(KeystoreDeviceKey(), FileVaultMetaStore(appContext), clock::now),
            biometric = BiometricKey(),
            files = VaultFileStore(appContext),
            session = vaultSession,
            notes = noteRepository,
            attachments = attachmentRepository,
            resolver = appContext.contentResolver,
            clock = clock,
            deleteDatabaseBackups = { com.nexusnotes.core.database.DatabaseSafety.deleteBackups(appContext) },
        )
    }
}

/**
 * Opens (and if needed upgrades) the database off the main thread before any screen uses it.
 * A failure is returned instead of crashing, so the app can show what went wrong; the
 * pre-upgrade backup copy keeps the notes safe.
 */
suspend fun AppContainer.openDatabase(): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        database.openHelper.writableDatabase
        Unit
    }.onSuccess {
        // Housekeeping: remove attachment files left behind by deleted notes.
        applicationScope.launch {
            runCatching { attachmentRepository.cleanUpFiles() }
            vaultRepository.cleanUpFiles()
            vaultRepository.refresh()
            runCatching { autoBackupScheduler.syncWithSettings() }
        }
        startWidgetUpdates()
    }
}

/** Access the container from a ViewModel factory initializer. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NexusNotesApp).container
