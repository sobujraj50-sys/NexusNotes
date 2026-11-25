package com.nexusnotes.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.entity.BackupHistoryEntity
import com.nexusnotes.data.repository.BackupRepository
import com.nexusnotes.data.repository.BackupSource
import com.nexusnotes.data.repository.ExportRepository
import com.nexusnotes.data.repository.VaultRepository
import com.nexusnotes.domain.backup.ExportFormat
import com.nexusnotes.domain.backup.Manifest
import com.nexusnotes.domain.backup.RestoreMode
import com.nexusnotes.domain.backup.VaultBackupKey
import com.nexusnotes.domain.vault.UnlockResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A finished operation to show in a dialog. */
data class BackupReportUi(val title: String, val lines: List<String>, val success: Boolean)

data class BackupUiState(
    /** Non-null while working; text shown with a progress indicator. */
    val busy: String? = null,
    /** A verified backup waiting for the user to confirm the restore. */
    val inspected: Manifest? = null,
    val inspectedIsSafetyCopy: Boolean = false,
    val report: BackupReportUi? = null,
    val hasSafetyCopy: Boolean = false,
)

class BackupViewModel(
    private val backups: BackupRepository,
    private val exports: ExportRepository,
    private val vault: VaultRepository,
    private val settings: com.nexusnotes.data.repository.SettingsRepository,
    private val scheduler: com.nexusnotes.core.backup.AutoBackupScheduler,
    private val resolver: android.content.ContentResolver,
) : ViewModel() {

    val appSettings: StateFlow<com.nexusnotes.domain.model.AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nexusnotes.domain.model.AppSettings())

    /** Folder picked for automatic backups: keep access across restarts, then turn it on. */
    fun setAutoBackupFolder(tree: Uri) {
        viewModelScope.launch {
            runCatching {
                resolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val s = appSettings.value
            settings.setAutoBackup(true, tree.toString(), s.autoBackupIntervalDays, s.autoBackupKeep)
            scheduler.syncWithSettings()
            report("Automatic backup is on", true, "Folder: " + (backups.displayName(tree) ?: tree.lastPathSegment ?: "chosen folder"),
                "Runs about every ${if (s.autoBackupIntervalDays == 1) "day" else "${s.autoBackupIntervalDays} days"} when the battery isn't low.",
                "Vault notes are not included in automatic backups (they need your backup password).")
        }
    }

    fun setAutoBackupOptions(enabled: Boolean, intervalDays: Int, keep: Int) {
        viewModelScope.launch {
            val s = appSettings.value
            settings.setAutoBackup(enabled, s.autoBackupFolder, intervalDays, keep)
            scheduler.syncWithSettings()
        }
    }

    fun runAutoBackupNow() {
        val folder = appSettings.value.autoBackupFolder ?: return
        run("Creating automatic backup…") {
            backups.createAutoBackup(Uri.parse(folder), appSettings.value.autoBackupKeep)
                .onSuccess { report("Backup saved", true, it, "Older automatic backups beyond ${appSettings.value.autoBackupKeep} were removed.") }
                .onFailure { report("Automatic backup failed", false, it.message ?: "Unknown error") }
        }
    }
    private val _state = MutableStateFlow(BackupUiState(hasSafetyCopy = backups.hasSafetyCopy()))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val history: StateFlow<List<BackupHistoryEntity>> =
        backups.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vaultSetUp: StateFlow<Boolean?> = vault.isSetUp
    val vaultUnlocked: StateFlow<Boolean> = vault.unlocked

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { vault.refresh() }
    }

    /** Leaving the app for a system file picker shouldn't lock the vault instantly. */
    fun beforePicker() = vault.notePickerOpening()

    fun dismissReport() = _state.update { it.copy(report = null) }

    fun backupPasswordProblem(password: String, confirm: String): String? =
        VaultBackupKey.passwordProblem(password.toCharArray()) ?: if (password != confirm) "The two passwords don't match." else null

    suspend fun unlockVault(pin: String): String? = when (val r = vault.unlock(pin.toCharArray())) {
        is UnlockResult.Success -> null
        is UnlockResult.WrongPin -> "Wrong PIN."
        is UnlockResult.LockedOut -> "Too many wrong attempts. Try again later."
        UnlockResult.KeysLost -> "The vault can't be opened on this phone."
        UnlockResult.NotSetUp -> "There is no vault."
    }

    fun createBackup(target: Uri, vaultPassword: String?) {
        run("Creating and checking backup…") {
            val pw = vaultPassword?.toCharArray()
            backups.createBackup(target, pw)
                .onSuccess { r ->
                    report(
                        "Backup saved and verified", true,
                        r.fileName,
                        "${r.manifest.counts["notes"]} notes, ${r.manifest.counts["prompts"]} prompts, ${r.manifest.counts["attachments"]} attachments",
                        if (r.manifest.includesVault) "Vault: ${r.manifest.counts["vaultNotes"]} notes, still encrypted (needs the backup password)" else "Vault: not included",
                        if (r.skippedFiles > 0) "${r.skippedFiles} attachment(s) skipped: their file was missing on this phone" else null,
                        "Keep this file somewhere safe (computer, cloud drive, SD card).",
                    )
                }
                .onFailure { report("Backup failed", false, it.message ?: "Unknown error", "Nothing on your phone was changed.") }
            pw?.fill(' ')
        }
    }

    fun verify(uri: Uri) {
        run("Checking backup…") {
            backups.verify(uri)
                .onSuccess { m -> report("Backup is OK", true, *describe(m).toTypedArray()) }
                .onFailure { report("Backup can't be used", false, it.message ?: "Unknown error") }
        }
    }

    fun inspect(uri: Uri) = inspectSource(BackupSource.Document(uri), false)

    fun inspectSafetyCopy() = inspectSource(BackupSource.SafetyCopy, true)

    private fun inspectSource(source: BackupSource, safety: Boolean) {
        run("Reading and verifying backup…") {
            backups.inspect(source)
                .onSuccess { m -> _state.update { it.copy(inspected = m, inspectedIsSafetyCopy = safety) } }
                .onFailure { report("Backup can't be used", false, it.message ?: "Unknown error", "Nothing on your phone was changed.") }
        }
    }

    fun cancelRestore() {
        backups.discardInspected()
        _state.update { it.copy(inspected = null) }
    }

    fun restore(mode: RestoreMode, vaultPassword: String?) {
        _state.update { it.copy(inspected = null) }
        run("Restoring…") {
            val pw = vaultPassword?.toCharArray()
            backups.restore(mode, pw)
                .onSuccess { r ->
                    val s = r.stats
                    report(
                        "Restore finished", r.vaultError == null,
                        "Notes: ${s.notesAdded} added" + (if (s.notesCopied > 0) ", ${s.notesCopied} added as “(restored)” copies" else "") +
                            if (s.notesSkipped > 0) ", ${s.notesSkipped} already here (skipped)" else "",
                        "Prompts: ${s.promptsAdded} added" + (if (s.promptsCopied > 0) ", ${s.promptsCopied} copies" else "") +
                            if (s.promptsSkipped > 0) ", ${s.promptsSkipped} skipped" else "",
                        "Folders: ${s.foldersAdded} · Tags: ${s.tagsAdded} · Attachments: ${s.attachmentsAdded}",
                        when {
                            r.vaultError != null -> "Vault: not restored — ${r.vaultError}"
                            r.vaultNotesAdded > 0 -> "Vault: ${r.vaultNotesAdded} notes added (encrypted)"
                            else -> null
                        },
                        if (mode == RestoreMode.REPLACE) "A safety copy of the previous data was kept (Undo last restore)." else null,
                    )
                }
                .onFailure { report("Restore failed", false, it.message ?: "Unknown error", "Nothing was changed — the restore is all-or-nothing.") }
            pw?.fill(' ')
        }
    }

    fun export(format: ExportFormat, target: Uri, fileName: String) {
        run("Exporting…") {
            exports.export(format, target, fileName)
                .onSuccess { report("Export finished", true, "$it notes exported as ${format.name}.", "Vault notes and Trash are never exported.") }
                .onFailure { report("Export failed", false, it.message ?: "Unknown error") }
        }
    }

    fun describe(m: Manifest): List<String> = listOf(
        "Made: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt)),
        "App version: ${m.appVersion}",
        "${m.counts["notes"]} notes · ${m.counts["prompts"]} prompts · ${m.counts["folders"]} folders · ${m.counts["tags"]} tags · ${m.counts["attachments"]} attachments",
        if (m.includesVault) "Vault: ${m.counts["vaultNotes"]} encrypted notes (password needed)" else "Vault: not included",
        "All ${m.files.size} parts passed the integrity check.",
    )

    private fun report(title: String, success: Boolean, vararg lines: String?) {
        _state.update { it.copy(report = BackupReportUi(title, lines.filterNotNull(), success)) }
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = label) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = null, hasSafetyCopy = backups.hasSafetyCopy()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                BackupViewModel(c.backupRepository, c.exportRepository, c.vaultRepository, c.settingsRepository, c.autoBackupScheduler, c.contentResolver)
            }
        }
    }
}
