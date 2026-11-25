package com.nexusnotes.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.database.NexusDatabase
import com.nexusnotes.core.security.BiometricKey
import com.nexusnotes.core.security.VaultFileStore
import com.nexusnotes.core.security.VaultSession
import com.nexusnotes.data.entity.VaultAttachmentEntity
import com.nexusnotes.data.entity.VaultItemEntity
import com.nexusnotes.domain.attachment.AttachmentPolicy
import com.nexusnotes.domain.backup.VaultAttachmentRec
import com.nexusnotes.domain.backup.VaultBackupKey
import com.nexusnotes.domain.backup.VaultItemRec
import com.nexusnotes.domain.backup.VaultKeyRec
import com.nexusnotes.domain.vault.UnlockResult
import com.nexusnotes.domain.vault.VaultAttachment
import com.nexusnotes.domain.vault.VaultCodec
import com.nexusnotes.domain.vault.VaultCrypto
import com.nexusnotes.domain.vault.VaultFileTooLargeException
import com.nexusnotes.domain.vault.VaultKeyManager
import com.nexusnotes.domain.vault.VaultNote
import com.nexusnotes.domain.vault.VaultPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

class VaultLockedException : IllegalStateException("The vault is locked.")

/** Decrypted vault list. [damaged] counts records that failed authentication (corrupted). */
data class VaultList(val notes: List<VaultNote>, val damaged: Int)

/**
 * The only place that encrypts or decrypts vault data. Everything else sees plaintext only
 * in memory, through these functions, while the vault is unlocked.
 */
class VaultRepository(
    private val database: NexusDatabase,
    private val keys: VaultKeyManager,
    private val biometric: BiometricKey,
    private val files: VaultFileStore,
    private val session: VaultSession,
    private val notes: NoteRepository,
    private val attachments: AttachmentRepository,
    private val resolver: ContentResolver,
    private val clock: Clock,
    /** Deletes old database copies that could still contain a moved note as plain text. */
    private val deleteDatabaseBackups: () -> Unit = {},
) {
    private val dao get() = database.vaultDao()

    private val _isSetUp = MutableStateFlow<Boolean?>(null)
    /** null until checked. */
    val isSetUp: StateFlow<Boolean?> = _isSetUp.asStateFlow()
    val unlocked: StateFlow<Boolean> get() = session.unlocked

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _isSetUp.value = runCatching { keys.isSetUp() }.getOrDefault(false)
    }

    // ---- keys -------------------------------------------------------------------------

    suspend fun create(pin: CharArray): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val key = keys.create(pin)
            session.open(key)
            _isSetUp.value = true
        }
    }

    suspend fun unlock(pin: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val r = keys.unlock(pin)
        if (r is UnlockResult.Success) session.open(r.key)
        r
    }

    suspend fun changePin(old: CharArray, new: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val r = keys.changePin(old, new)
        if (r is UnlockResult.Success) r.key.fill(0)
        r
    }

    suspend fun lockoutUntil(): Long = withContext(Dispatchers.IO) { keys.meta()?.lockoutUntil ?: 0 }

    suspend fun biometricEnabled(): Boolean = withContext(Dispatchers.IO) { keys.meta()?.biometricKey != null }

    suspend fun autoLockMs(): Long = withContext(Dispatchers.IO) { keys.meta()?.autoLockMs ?: VaultPolicy.DEFAULT_AUTO_LOCK_MS }

    /** Fast read used by the auto-lock (called on the main thread; tiny file). */
    fun autoLockMsBlocking(): Long = runCatching { keys.meta()?.autoLockMs }.getOrNull() ?: VaultPolicy.DEFAULT_AUTO_LOCK_MS

    suspend fun setAutoLock(ms: Long) = withContext(Dispatchers.IO) { keys.setAutoLock(ms) }

    /** Cipher for the biometric prompt to unlock. Throws BiometricKeyInvalidatedException (biometrics turned off). */
    suspend fun biometricUnlockCipher(): Cipher = withContext(Dispatchers.IO) {
        val blob = keys.meta()?.biometricKey ?: throw IllegalStateException("Biometric unlock is off.")
        try {
            biometric.decryptCipher(blob)
        } catch (e: Exception) {
            keys.setBiometricKey(null)
            throw e
        }
    }

    suspend fun completeBiometricUnlock(cipher: Cipher): Boolean = withContext(Dispatchers.Default) {
        val blob = keys.meta()?.biometricKey ?: return@withContext false
        runCatching { biometric.open(cipher, blob) }.getOrNull()?.let {
            keys.recordBiometricSuccess()
            session.open(it)
            true
        } ?: false
    }

    fun biometricEnableCipher(): Cipher = biometric.encryptCipher()

    suspend fun completeBiometricEnable(cipher: Cipher) = withContext(Dispatchers.Default) {
        val key = session.keyCopy() ?: throw VaultLockedException()
        try {
            keys.setBiometricKey(biometric.seal(cipher, key))
        } finally {
            key.fill(0)
        }
    }

    suspend fun disableBiometric() = withContext(Dispatchers.IO) {
        keys.setBiometricKey(null)
        biometric.delete()
    }

    fun lock() = session.lock()

    /** Pickers take the user out of the app briefly; don't auto-lock instantly for that. */
    fun notePickerOpening() {
        session.pickerOpenUntil = clock.now() + VaultPolicy.PICKER_GRACE_MS
    }

    /** Permanently deletes the vault: keys, encrypted rows and files. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        session.lockNow()
        dao.deleteAllAttachments()
        dao.deleteAllItems()
        files.deleteAll()
        biometric.delete()
        keys.destroy()
        checkpoint()
        _isSetUp.value = false
    }

    // ---- notes ------------------------------------------------------------------------

    private inline fun <T> withKey(block: (ByteArray) -> T): T {
        val key = session.keyCopy() ?: throw VaultLockedException()
        try {
            return block(key)
        } finally {
            key.fill(0)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNotes(): Flow<VaultList> = session.unlocked.flatMapLatest { open ->
        if (!open) {
            flowOf(VaultList(emptyList(), 0))
        } else {
            dao.observeItems().map { rows ->
                val key = session.keyCopy() ?: return@map VaultList(emptyList(), 0)
                try {
                    var damaged = 0
                    val list = rows.mapNotNull { row ->
                        runCatching { decodeNote(key, row) }.getOrElse { damaged++; null }
                    }
                    VaultList(list, damaged)
                } finally {
                    key.fill(0)
                }
            }.flowOn(Dispatchers.Default)
        }
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    private fun decodeNote(key: ByteArray, row: VaultItemEntity): VaultNote {
        val (title, body) = VaultCodec.decodeNote(VaultCrypto.open(key, row.payload, VaultCodec.aadNote(row.id)))
        return VaultNote(row.id, title, body, row.createdAt, row.updatedAt)
    }

    suspend fun getNote(id: String): VaultNote? = withContext(Dispatchers.Default) {
        val row = dao.getItem(id) ?: return@withContext null
        withKey { decodeNote(it, row) }
    }

    /** Saves (creates if needed). Returns false if nothing was stored (new and empty). */
    suspend fun saveNote(id: String, title: String, body: String): Boolean = withContext(Dispatchers.Default) {
        withKey { key -> saveWithKey(key, id, title, body) }
    }

    /** Used by the editor's before-lock hook, which receives the key directly. */
    suspend fun saveWithKey(key: ByteArray, id: String, title: String, body: String): Boolean = withContext(Dispatchers.Default) {
        val payload = VaultCrypto.seal(key, VaultCodec.encodeNote(title, body), VaultCodec.aadNote(id))
        val now = clock.now()
        if (dao.updateItem(id, payload, now) == 0) {
            if (title.isBlank() && body.isBlank()) return@withContext false
            dao.upsertItem(VaultItemEntity(id, payload, now, now))
        }
        true
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        val atts = dao.getAttachments(id)
        dao.deleteItem(id) // attachments rows cascade
        atts.forEach { files.delete(it.fileName) }
        checkpoint()
    }

    // ---- attachments ------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAttachments(itemId: String): Flow<List<VaultAttachment>> = session.unlocked.flatMapLatest { open ->
        if (!open) flowOf(emptyList()) else dao.observeAttachments(itemId).map { rows ->
            val key = session.keyCopy() ?: return@map emptyList()
            try {
                rows.mapNotNull { runCatching { decodeAttachment(key, it) }.getOrNull() }
            } finally {
                key.fill(0)
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun decodeAttachment(key: ByteArray, row: VaultAttachmentEntity): VaultAttachment {
        val m = VaultCodec.decodeAttachment(VaultCrypto.open(key, row.payload, VaultCodec.aadAttachment(row.id)))
        return VaultAttachment(row.id, row.itemId, row.fileName, m.displayName, m.mimeType, m.sizeBytes, row.createdAt)
    }

    /** Encrypts a picked file into the vault. The plaintext never touches disk. */
    suspend fun addAttachment(itemId: String, uri: Uri, title: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(uri.scheme == "content") { "Only files chosen through Android's pickers can be added." }
            if (dao.countAttachments(itemId) >= VaultPolicy.MAX_ATTACHMENTS_PER_NOTE) {
                error("A vault note can hold up to ${VaultPolicy.MAX_ATTACHMENTS_PER_NOTE} files.")
            }
            var name: String? = null
            var declared: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sz = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (sz >= 0 && !c.isNull(sz)) declared = c.getLong(sz)
                }
            }
            if ((declared ?: 0L) > VaultPolicy.MAX_FILE_BYTES) throw VaultFileTooLargeException()
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val input = resolver.openInputStream(uri) ?: error("Could not open the file.")
            withKey { key ->
                // Make sure the note row exists (a new note gets saved first).
                if (dao.getItem(itemId) == null) {
                    val now = clock.now()
                    dao.upsertItem(VaultItemEntity(itemId, VaultCrypto.seal(key, VaultCodec.encodeNote(title, body), VaultCodec.aadNote(itemId)), now, now))
                }
                insertAttachment(key, itemId, input, AttachmentPolicy.safeDisplayName(name, "file"), mime)
            }
        }
    }

    private suspend fun insertAttachment(key: ByteArray, itemId: String, input: java.io.InputStream, displayName: String, mime: String) {
        val id = UUID.randomUUID().toString()
        val (fileName, size) = input.use { files.write(key, id, it, VaultCodec.aadFile(id)) }
        val meta = VaultCrypto.seal(key, VaultCodec.encodeAttachment(VaultCodec.AttachmentMeta(displayName, mime, size)), VaultCodec.aadAttachment(id))
        try {
            dao.insertAttachment(VaultAttachmentEntity(id, itemId, fileName, meta, clock.now()))
        } catch (e: Throwable) {
            files.delete(fileName)
            throw e
        }
    }

    /** Decrypted bytes, in memory only (images are shown from this). */
    suspend fun readAttachment(a: VaultAttachment): ByteArray = withContext(Dispatchers.IO) {
        withKey { files.readAll(it, a.fileName, VaultCodec.aadFile(a.id)) }
    }

    fun isFileMissing(a: VaultAttachment): Boolean = !files.file(a.fileName).isFile

    /** Writes the decrypted file to a place the user explicitly chose (SAF "Save as"). */
    suspend fun exportAttachment(a: VaultAttachment, target: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = resolver.openOutputStream(target, "w") ?: error("Could not write there.")
            out.use { stream -> withKey { files.decryptTo(it, a.fileName, VaultCodec.aadFile(a.id), stream) } }
        }
    }

    suspend fun removeAttachment(a: VaultAttachment) = withContext(Dispatchers.IO) {
        dao.deleteAttachment(a.id)
        files.delete(a.fileName)
    }

    suspend fun cleanUpFiles() = withContext(Dispatchers.IO) {
        runCatching { files.cleanUp(dao.allFileNames().toSet(), clock.now()) }
    }

    // ---- backup -----------------------------------------------------------------------

    /** Encrypted vault rows + files for a backup. The vault key goes in wrapped by [password]. */
    class BackupPart(
        val key: VaultKeyRec,
        val items: List<VaultItemRec>,
        val attachments: List<VaultAttachmentRec>,
        val files: Map<String, java.io.File>,
    )

    suspend fun backupPart(password: CharArray): BackupPart = withContext(Dispatchers.Default) {
        withKey { key ->
            val b64 = Base64.getEncoder()
            val atts = dao.allAttachments().filter { files.file(it.fileName).isFile }
            BackupPart(
                key = VaultBackupKey.wrap(key, password),
                items = dao.allItems().map { VaultItemRec(it.id, b64.encodeToString(it.payload), it.createdAt, it.updatedAt) },
                attachments = atts.map { VaultAttachmentRec(it.id, it.itemId, it.fileName, b64.encodeToString(it.payload), it.createdAt) },
                files = atts.associate { it.fileName to files.file(it.fileName) },
            )
        }
    }

    /**
     * Adds the vault notes from a backup to this phone's vault (which must exist and be unlocked).
     * Everything is re-encrypted with this vault's key. Notes already present are skipped.
     * Returns the number of notes added.
     */
    suspend fun restoreFromBackup(
        items: List<VaultItemRec>,
        attachments: List<VaultAttachmentRec>,
        keyRec: VaultKeyRec,
        password: CharArray,
        stagedFile: (String) -> java.io.File?,
    ): Result<Int> = withContext(Dispatchers.Default) {
        runCatching {
            if (_isSetUp.value != true) error("Set up the vault on this phone first, then restore again.")
            val backupKey = VaultBackupKey.unwrap(keyRec, password) ?: error("Wrong backup password.")
            try {
                val b64 = Base64.getDecoder()
                withKey { key ->
                    var added = 0
                    items.forEach { item ->
                        val plain = VaultCrypto.open(backupKey, b64.decode(item.payload), VaultCodec.aadNote(item.id))
                        val existing = dao.getItem(item.id)
                        if (existing != null && runCatching { VaultCrypto.open(key, existing.payload, VaultCodec.aadNote(item.id)) }.getOrNull()?.contentEquals(plain) == true) {
                            return@forEach // same note already here
                        }
                        val newId = if (existing == null) item.id else UUID.randomUUID().toString()
                        dao.upsertItem(VaultItemEntity(newId, VaultCrypto.seal(key, plain, VaultCodec.aadNote(newId)), item.createdAt, item.updatedAt))
                        plain.fill(0)
                        attachments.filter { it.itemId == item.id }.forEach { a ->
                            val meta = VaultCodec.decodeAttachment(VaultCrypto.open(backupKey, b64.decode(a.payload), VaultCodec.aadAttachment(a.id)))
                            val source = stagedFile(a.fileName) ?: error("A vault file is missing from the backup.")
                            val bytes = source.inputStream().buffered().use { VaultCrypto.decryptStreamToBytes(backupKey, it, VaultCodec.aadFile(a.id)) }
                            try {
                                insertAttachment(key, newId, bytes.inputStream(), meta.displayName, meta.mimeType)
                            } finally {
                                bytes.fill(0)
                            }
                        }
                        added++
                    }
                    added
                }
            } finally {
                backupKey.fill(0)
            }
        }
    }

    // ---- moving -----------------------------------------------------------------------

    /**
     * Encrypts a normal note (and its attachments) into the vault, then deletes the original
     * forever: row, search index entry and plaintext files. Folder and tags are not kept.
     */
    suspend fun moveNoteToVault(noteId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val note = notes.getNote(noteId) ?: error("Note not found.")
            val noteAttachments = attachments.listForNote(noteId)
            val itemId = UUID.randomUUID().toString()
            withKey { key ->
                try {
                    val payload = VaultCrypto.seal(key, VaultCodec.encodeNote(note.title, note.body), VaultCodec.aadNote(itemId))
                    dao.upsertItem(VaultItemEntity(itemId, payload, note.createdAt, clock.now()))
                    noteAttachments.forEach { a ->
                        val file = attachments.fileFor(a)
                        if (file.isFile) insertAttachment(key, itemId, file.inputStream(), a.displayName, a.mimeType)
                    }
                } catch (e: Throwable) {
                    deleteNote(itemId) // roll back; the original note is untouched
                    throw e
                }
            }
            // Only after everything is safely encrypted: remove the plaintext.
            noteAttachments.forEach { attachments.deleteFileNow(it) }
            notes.moveToTrash(noteId)
            notes.deletePermanently(noteId)
            checkpoint()
            runCatching { deleteDatabaseBackups() }
            itemId
        }
    }

    /** Decrypts a vault note back into a normal (searchable, unencrypted) note. */
    suspend fun moveOutOfVault(itemId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val note = getNote(itemId) ?: error("Vault note not found.")
            val atts = dao.getAttachments(itemId)
            val noteId = UUID.randomUUID().toString()
            notes.saveContent(noteId, note.title.ifBlank { "Untitled" }, note.body)
            try {
                withKey { key ->
                    atts.forEach { row ->
                        val a = decodeAttachment(key, row)
                        val bytes = files.readAll(key, a.fileName, VaultCodec.aadFile(a.id))
                        attachments.addFromStream(noteId, bytes.inputStream(), a.displayName, a.mimeType)
                        bytes.fill(0)
                    }
                }
            } catch (e: Throwable) {
                notes.moveToTrash(noteId)
                notes.deletePermanently(noteId)
                throw e
            }
            deleteNote(itemId)
            noteId
        }
    }

    /** Pushes deleted pages out of the WAL file so removed plaintext doesn't linger there. */
    private fun checkpoint() {
        runCatching { database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close() }
    }
}
