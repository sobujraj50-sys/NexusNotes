package com.nexusnotes.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.database.NexusDatabase
import com.nexusnotes.core.storage.AttachmentStore
import com.nexusnotes.data.entity.AttachmentEntity
import com.nexusnotes.data.entity.BackupHistoryEntity
import com.nexusnotes.data.entity.FolderEntity
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.NoteTagCrossRef
import com.nexusnotes.data.entity.NoteTemplateEntity
import com.nexusnotes.data.entity.ReminderEntity
import com.nexusnotes.data.entity.PromptCategoryEntity
import com.nexusnotes.data.entity.PromptEntity
import com.nexusnotes.data.entity.PromptVariableEntity
import com.nexusnotes.data.entity.PromptVersionEntity
import com.nexusnotes.data.entity.TagEntity
import com.nexusnotes.domain.attachment.AttachmentPolicy
import com.nexusnotes.domain.backup.AttachmentRec
import com.nexusnotes.domain.backup.AutoBackupPolicy
import com.nexusnotes.domain.backup.BackupData
import com.nexusnotes.domain.backup.BackupInvalidException
import com.nexusnotes.domain.backup.BackupPaths
import com.nexusnotes.domain.backup.BackupReader
import com.nexusnotes.domain.backup.BackupWriter
import com.nexusnotes.domain.backup.CategoryRec
import com.nexusnotes.domain.backup.ExistingData
import com.nexusnotes.domain.backup.FolderRec
import com.nexusnotes.domain.backup.Manifest
import com.nexusnotes.domain.backup.NoteRec
import com.nexusnotes.domain.backup.NoteTagRec
import com.nexusnotes.domain.backup.PromptRec
import com.nexusnotes.domain.backup.ReminderRec
import com.nexusnotes.domain.backup.TemplateRec
import com.nexusnotes.domain.backup.RestoreMode
import com.nexusnotes.domain.backup.RestorePlanner
import com.nexusnotes.domain.backup.RestoreStats
import com.nexusnotes.domain.backup.StagedBackup
import com.nexusnotes.domain.backup.TagRec
import com.nexusnotes.domain.backup.VariableRec
import com.nexusnotes.domain.backup.VersionRec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

data class BackupResult(val manifest: Manifest, val fileName: String, val sizeBytes: Long, val skippedFiles: Int)

data class RestoreReport(val stats: RestoreStats, val vaultNotesAdded: Int, val vaultError: String?)

/** Where a backup being inspected came from. */
sealed interface BackupSource {
    data class Document(val uri: Uri) : BackupSource
    data object SafetyCopy : BackupSource
}

/**
 * Backup, verify and restore. Backups go only to a place the user picks (Storage Access Framework),
 * are read back and verified after writing, and a restore never changes anything until the
 * whole file has been verified and the user confirmed.
 */
class BackupRepository(
    context: Context,
    private val database: NexusDatabase,
    private val attachmentStore: AttachmentStore,
    private val attachments: AttachmentRepository,
    private val vault: VaultRepository,
    private val clock: Clock,
    private val appVersion: String,
    /** Re-arms reminder alarms after a restore. */
    private val afterRestore: suspend () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val dao get() = database.backupDao()
    private val mutex = Mutex()

    private val stagingDir get() = File(appContext.cacheDir, "restore-staging")
    private val safetyFile get() = File(File(appContext.filesDir, "safety").apply { mkdirs() }, "before-last-restore.zip")

    private var staged: StagedBackup? = null

    fun observeHistory(): Flow<List<BackupHistoryEntity>> = dao.observeHistory()

    fun hasSafetyCopy(): Boolean = safetyFile.isFile

    // ---- backup -----------------------------------------------------------------------

    suspend fun createBackup(target: Uri, vaultPassword: CharArray?): Result<BackupResult> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val name = displayName(target) ?: "backup.zip"
            val result = runCatching {
                val out = resolver.openOutputStream(target, "wt") ?: error("Could not write to that location.")
                val r = out.use { writeBackup(it, vaultPassword) }
                // Read the file back and verify every byte before calling it a success.
                verifyDocument(target)
                r.copy(fileName = name, sizeBytes = sizeOf(target))
            }
            record("BACKUP", name, result.getOrNull()?.sizeBytes ?: 0, result.fold(
                { "${it.manifest.counts["notes"]} notes, ${it.manifest.counts["attachments"]} files" + if (it.manifest.includesVault) ", vault (encrypted)" else "" },
                { it.message ?: "Failed" },
            ), result.isSuccess)
            result
        }
    }

    private suspend fun writeBackup(out: java.io.OutputStream, vaultPassword: CharArray?): BackupResult {
        var data = snapshot()
        // Attachments whose file vanished can't be backed up; leave them out so the backup stays valid.
        val present = data.attachments.filter { attachmentStore.exists(it.fileName) && BackupPaths.isSafeFileName(it.fileName) }
        val skipped = data.attachments.size - present.size
        data = data.copy(attachments = present)
        var vaultFiles: Map<String, File> = emptyMap()
        if (vaultPassword != null) {
            val part = vault.backupPart(vaultPassword)
            data = data.copy(vaultItems = part.items, vaultAttachments = part.attachments, vaultKey = part.key)
            vaultFiles = part.files
        }
        val writer = BackupWriter(out)
        writer.writeData(data)
        data.attachments.forEach { a ->
            attachmentStore.file(a.fileName).inputStream().buffered().use { writer.writeFile(BackupPaths.ATTACHMENTS + a.fileName, it) }
        }
        data.vaultAttachments.forEach { a ->
            vaultFiles.getValue(a.fileName).inputStream().buffered().use { writer.writeFile(BackupPaths.VAULT + a.fileName, it) }
        }
        val manifest = writer.finish(appVersion, NexusDatabase.VERSION, clock.now(), data)
        return BackupResult(manifest, "", 0, skipped)
    }

    private suspend fun snapshot(): BackupData = BackupData(
        notes = dao.notes().map { NoteRec(it.id, it.title, it.body, it.bodyFormatVersion, it.colorIndex, it.isPinned, it.isFavorite, it.isArchived, it.deletedAt, it.createdAt, it.updatedAt, it.folderId) },
        folders = dao.folders().map { FolderRec(it.id, it.name, it.parentId, it.colorIndex, it.createdAt) },
        tags = dao.tags().map { TagRec(it.id, it.name) },
        noteTags = dao.noteTags().map { NoteTagRec(it.noteId, it.tagId) },
        prompts = dao.prompts().map { PromptRec(it.id, it.title, it.template, it.categoryKey, it.isFavorite, it.createdAt, it.updatedAt) },
        categories = dao.categories().map { CategoryRec(it.id, it.name, it.createdAt) },
        variables = dao.variables().map { VariableRec(it.promptId, it.name, it.defaultValue) },
        versions = dao.versions().map { VersionRec(it.id, it.promptId, it.title, it.template, it.createdAt) },
        attachments = dao.attachments().map {
            AttachmentRec(it.id, it.noteId, it.kind, it.fileName, it.displayName, it.mimeType, it.sizeBytes, it.sha256, it.durationMs, it.createdAt)
        },
        templates = dao.templates().map { TemplateRec(it.id, it.name, it.title, it.body, it.createdAt) },
        reminders = dao.reminders().map { ReminderRec(it.noteId, it.triggerAt, it.repeat, it.anchorAt, it.createdAt) },
    )

    /**
     * Automatic backup into a folder the user picked once (persisted permission). Vault notes are not
     * included (they need the backup password). Old automatic copies beyond [keep] are deleted;
     * manual backups in the same folder are never touched.
     */
    suspend fun createAutoBackup(tree: Uri, keep: Int): Result<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            var created: Uri? = null
            val name = AutoBackupPolicy.fileName(clock.now(), java.time.ZoneId.systemDefault())
            val result = runCatching {
                val treeId = DocumentsContract.getTreeDocumentId(tree)
                val dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
                val doc = DocumentsContract.createDocument(resolver, dir, "application/zip", name)
                    ?: error("The backup folder is not available any more. Choose it again in Backup settings.")
                created = doc
                resolver.openOutputStream(doc, "wt")!!.use { writeBackup(it, null) }
                verifyDocument(doc)
                // Retention: list the folder, delete the oldest automatic backups.
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
                val found = mutableMapOf<String, String>()
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
                    while (c.moveToNext()) found[c.getString(0)] = c.getString(1)
                }
                AutoBackupPolicy.toDelete(found.keys.toList(), keep).forEach { old ->
                    runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, found.getValue(old))) }
                }
                displayName(doc) ?: name
            }.onFailure {
                created?.let { doc -> runCatching { DocumentsContract.deleteDocument(resolver, doc) } }
            }
            record("AUTO BACKUP", name, created?.let { sizeOf(it) } ?: 0, result.fold({ "Saved $it" }, { it.message ?: "Failed" }), result.isSuccess)
            result
        }
    }

    // ---- verify / inspect -------------------------------------------------------------

    private fun open(source: BackupSource): InputStream = when (source) {
        is BackupSource.Document -> resolver.openInputStream(source.uri) ?: error("Could not open the file.")
        BackupSource.SafetyCopy -> safetyFile.inputStream()
    }

    private fun verifyDocument(uri: Uri) {
        val dir = File(appContext.cacheDir, "verify-" + UUID.randomUUID())
        try {
            open(BackupSource.Document(uri)).buffered().use { BackupReader.read(it, dir) }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Checks a backup file completely without restoring anything. */
    suspend fun verify(uri: Uri): Result<Manifest> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = File(appContext.cacheDir, "verify-" + UUID.randomUUID())
            val r = runCatching { open(BackupSource.Document(uri)).buffered().use { BackupReader.read(it, dir).manifest } }
            dir.deleteRecursively()
            record("VERIFY", displayName(uri) ?: "backup", sizeOf(uri), r.fold({ "OK — ${it.counts["notes"]} notes" }, { it.message ?: "Failed" }), r.isSuccess)
            r
        }
    }

    /** Reads and verifies a backup, keeping it ready for [restore]. Nothing is changed yet. */
    suspend fun inspect(source: BackupSource): Result<Manifest> = mutex.withLock {
        withContext(Dispatchers.IO) {
            clearStaging()
            runCatching { open(source).buffered().use { BackupReader.read(it, stagingDir) } }
                .onSuccess { staged = it }
                .onFailure { clearStaging() }
                .map { it.manifest }
        }
    }

    fun discardInspected() {
        staged = null
        clearStaging()
    }

    private fun clearStaging() {
        staged = null
        stagingDir.deleteRecursively()
    }

    // ---- restore ----------------------------------------------------------------------

    /**
     * Restores the inspected backup. [vaultPassword] (if given) also restores the encrypted vault
     * notes into this phone's vault. REPLACE first saves a safety copy of the current data.
     */
    suspend fun restore(mode: RestoreMode, vaultPassword: CharArray?): Result<RestoreReport> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val backup = staged ?: return@withContext Result.failure<RestoreReport>(IllegalStateException("Choose the backup file again."))
            val result = runCatching {
                if (mode == RestoreMode.REPLACE) {
                    // Error recovery: keep what is on the phone now, so "replace" can be undone.
                    val temp = File(safetyFile.parentFile, "safety.tmp")
                    temp.outputStream().use { writeBackup(it, null) }
                    if (!temp.renameTo(safetyFile)) error("Could not save the safety copy; nothing was changed.")
                }
                val existing = existingData()
                val plan = RestorePlanner { UUID.randomUUID().toString() }.plan(backup.data, existing, mode)

                // 1) Copy files first (new names only, never overwriting).
                val copied = mutableListOf<String>()
                try {
                    plan.files.forEach { f ->
                        val src = backup.file(f.archivePath) ?: throw BackupInvalidException("A file is missing from the backup.")
                        val target = attachmentStore.file(f.targetName)
                        if (target.exists()) throw IllegalStateException("File name clash; nothing was changed.")
                        val temp = File(target.parentFile, "pending-restore-" + f.targetName)
                        src.copyTo(temp, overwrite = true)
                        if (!temp.renameTo(target)) error("Could not save a restored file.")
                        copied += f.targetName
                    }
                    // 2) All rows in one transaction.
                    val d = plan.insert
                    dao.applyRestore(
                        replace = mode == RestoreMode.REPLACE,
                        notes = d.notes.map { NoteEntity(it.id, it.title, it.body, it.bodyFormatVersion, it.colorIndex, it.isPinned, it.isFavorite, it.isArchived, it.deletedAt, it.createdAt, it.updatedAt, it.folderId) },
                        folders = d.folders.map { FolderEntity(it.id, it.name, it.parentId, it.colorIndex, it.createdAt) },
                        tags = d.tags.map { TagEntity(it.id, it.name) },
                        noteTags = d.noteTags.map { NoteTagCrossRef(it.noteId, it.tagId) },
                        categories = d.categories.map { PromptCategoryEntity(it.id, it.name, it.createdAt) },
                        prompts = d.prompts.map { PromptEntity(it.id, it.title, it.template, it.categoryKey, it.isFavorite, it.createdAt, it.updatedAt) },
                        variables = d.variables.map { PromptVariableEntity(it.promptId, it.name, it.defaultValue) },
                        versions = d.versions.map { PromptVersionEntity(it.id, it.promptId, it.title, it.template, it.createdAt) },
                        attachments = d.attachments.map {
                            AttachmentEntity(it.id, it.noteId, it.kind, it.fileName, AttachmentPolicy.safeDisplayName(it.displayName, "file"), it.mimeType, it.sizeBytes, it.sha256, it.durationMs, it.createdAt)
                        },
                        templates = d.templates.map { NoteTemplateEntity(it.id, it.name.take(80), it.title, it.body, it.createdAt) },
                        reminders = d.reminders.map { ReminderEntity(it.noteId, it.triggerAt, it.repeat, it.anchorAt, it.createdAt) },
                    )
                } catch (e: Throwable) {
                    copied.forEach { attachmentStore.delete(it) }
                    throw e
                }
                if (mode == RestoreMode.REPLACE) attachments.cleanUpFiles()
                afterRestore()

                // 3) Vault (optional, separate: normal notes are already safely restored).
                var vaultAdded = 0
                var vaultError: String? = null
                val key = backup.data.vaultKey
                if (vaultPassword != null && key != null) {
                    vault.restoreFromBackup(backup.data.vaultItems, backup.data.vaultAttachments, key, vaultPassword) { name ->
                        backup.file(BackupPaths.VAULT + name)
                    }.onSuccess { vaultAdded = it }.onFailure { vaultError = it.message ?: "Vault notes could not be restored." }
                }
                RestoreReport(plan.stats, vaultAdded, vaultError)
            }
            record(
                "RESTORE", "backup (${mode.name.lowercase()})", 0,
                result.fold({ "${it.stats.notesAdded + it.stats.notesCopied} notes restored" }, { it.message ?: "Failed" }),
                result.isSuccess,
            )
            if (result.isSuccess) clearStaging()
            result
        }
    }

    private suspend fun existingData(): ExistingData {
        val atts = dao.attachments()
        return ExistingData(
            notes = dao.notes().associate { it.id to NoteRec(it.id, it.title, it.body, it.bodyFormatVersion, it.colorIndex, it.isPinned, it.isFavorite, it.isArchived, it.deletedAt, it.createdAt, it.updatedAt, it.folderId) },
            folderIds = dao.folders().map { it.id }.toSet(),
            tags = dao.tags().map { TagRec(it.id, it.name) },
            prompts = dao.prompts().associate { it.id to PromptRec(it.id, it.title, it.template, it.categoryKey, it.isFavorite, it.createdAt, it.updatedAt) },
            categoryIds = dao.categories().map { it.id }.toSet(),
            attachmentIds = atts.map { it.id }.toSet(),
            attachmentFileNames = atts.map { it.fileName }.toSet() + attachmentStore.directory.list().orEmpty().toSet(),
            versionIds = dao.versions().map { it.id }.toSet(),
            templateIds = dao.templates().map { it.id }.toSet(),
            reminderNoteIds = dao.reminders().map { it.noteId }.toSet(),
        )
    }

    // ---- health -----------------------------------------------------------------------

    /** SQLite integrity + foreign-key check and attachment files. Read-only; changes nothing. */
    suspend fun checkHealth(): List<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val db = database.openHelper.writableDatabase
            val lines = mutableListOf<String>()
            db.query("PRAGMA integrity_check").use { c ->
                val results = generateSequence { if (c.moveToNext()) c.getString(0) else null }.take(20).toList()
                lines += if (results == listOf("ok")) "Database structure: OK" else "Database problems: " + results.joinToString("; ")
            }
            db.query("PRAGMA foreign_key_check").use { c ->
                lines += if (c.count == 0) "Links between notes, tags and files: OK" else "Broken links found: ${c.count}"
            }
            val atts = dao.attachments()
            val missing = atts.count { !attachmentStore.exists(it.fileName) }
            lines += if (missing == 0) "Attachment files: all ${atts.size} present" else "Attachment files: $missing of ${atts.size} missing (shown as “Missing” in notes)"
            lines += "Notes: ${dao.notes().size} · Prompts: ${dao.prompts().size} · DB version ${NexusDatabase.VERSION}"
            record("CHECK", "database", 0, lines.first(), lines.none { it.contains("problems") || it.contains("Broken") })
            lines
        }
    }

    // ---- helpers ----------------------------------------------------------------------

    suspend fun record(kind: String, fileName: String, size: Long, summary: String, success: Boolean) {
        runCatching {
            dao.insertHistory(BackupHistoryEntity(UUID.randomUUID().toString(), clock.now(), kind, fileName.take(120), size, summary.take(200), success))
            dao.trimHistory()
        }
    }

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }.getOrNull()

    private fun sizeOf(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}
