package com.nexusnotes.data.repository

import android.net.Uri
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.storage.AttachmentStore
import com.nexusnotes.data.dao.AttachmentDao
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.entity.AttachmentEntity
import com.nexusnotes.data.entity.toDomain
import com.nexusnotes.domain.attachment.AttachmentKind
import com.nexusnotes.domain.attachment.AttachmentPolicy
import com.nexusnotes.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ShareResult(val noteId: String, val attached: Int, val failed: Int)

/** Attachments: database rows + private files, always kept consistent. */
class AttachmentRepository(
    private val dao: AttachmentDao,
    private val noteDao: NoteDao,
    private val store: AttachmentStore,
    private val clock: Clock,
) {
    fun observeForNote(noteId: String): Flow<List<Attachment>> =
        dao.observeForNote(noteId).map { list -> list.map { it.toDomain() } }

    fun observeTotalBytes(): Flow<Long> = dao.observeTotalBytes()

    fun fileFor(attachment: Attachment): File = store.file(attachment.fileName)

    suspend fun missingIds(attachments: List<Attachment>): Set<String> = withContext(Dispatchers.IO) {
        attachments.filterNot { store.exists(it.fileName) }.map { it.id }.toSet()
    }

    /** Attaches a picked or shared item. Creates the note row first if the note is still new. */
    suspend fun addFromUri(noteId: String, uri: Uri, folderIdForNew: String? = null): Result<Attachment> = runCatching {
        if (dao.countForNote(noteId) >= AttachmentPolicy.MAX_PER_NOTE) {
            throw IllegalStateException("A note can hold up to ${AttachmentPolicy.MAX_PER_NOTE} attachments.")
        }
        val id = UUID.randomUUID().toString()
        val stored = store.importFrom(uri, id)
        insertOrDeleteFile(noteId, id, stored, AttachmentPolicy.kindFor(stored.mimeType), null, folderIdForNew)
    }

    fun newRecording(): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        return id to store.newRecordingFile(id)
    }

    suspend fun addRecording(noteId: String, id: String, pending: File, durationMs: Long, folderIdForNew: String?): Result<Attachment> =
        runCatching {
            val stored = store.finishRecording(pending, id)
            insertOrDeleteFile(noteId, id, stored, AttachmentKind.AUDIO, durationMs, folderIdForNew)
        }

    private suspend fun insertOrDeleteFile(
        noteId: String,
        id: String,
        stored: AttachmentStore.Stored,
        kind: AttachmentKind,
        durationMs: Long?,
        folderIdForNew: String?,
    ): Attachment {
        val entity = AttachmentEntity(
            id = id,
            noteId = noteId,
            kind = kind.name,
            fileName = stored.fileName,
            displayName = stored.displayName,
            mimeType = stored.mimeType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            durationMs = durationMs,
            createdAt = clock.now(),
        )
        try {
            noteDao.ensureExists(noteId, clock.now(), folderIdForNew)
            dao.insert(entity)
        } catch (e: Throwable) {
            store.delete(stored.fileName)
            throw e
        }
        return entity.toDomain()
    }

    /** Removes the attachment row, then its file. */
    suspend fun remove(id: String) {
        val entity = dao.getById(id) ?: return
        dao.delete(id)
        store.delete(entity.fileName)
    }

    /** Copies all attachments (rows and files) of one note to another. */
    suspend fun duplicateAll(fromNoteId: String, toNoteId: String) {
        dao.getForNote(fromNoteId).forEach { a ->
            val newId = UUID.randomUUID().toString()
            val copy = store.copy(a.fileName, newId) ?: return@forEach
            dao.insert(a.copy(id = newId, noteId = toNoteId, fileName = copy.fileName, createdAt = clock.now()))
        }
    }

    /** Deletes files that no attachment refers to any more. */
    suspend fun cleanUpFiles(): Int = store.cleanUp(dao.allFileNames().toSet(), clock.now())

    /**
     * Creates a note from content shared by another app (text and/or files).
     * Items that fail (too large, unreadable) are skipped and counted.
     */
    suspend fun importShare(
        notes: NoteRepository,
        subject: String?,
        text: String?,
        uris: List<Uri>,
    ): ShareResult {
        val noteId = UUID.randomUUID().toString()
        val title = AttachmentPolicy.sharedNoteTitle(subject, text, null)
        val body = AttachmentPolicy.sharedNoteBody(text)
        notes.saveContent(noteId, title, body)
        var ok = 0
        var failed = 0
        uris.take(AttachmentPolicy.MAX_PER_NOTE).forEach { uri ->
            if (addFromUri(noteId, uri).isSuccess) ok++ else failed++
        }
        failed += (uris.size - AttachmentPolicy.MAX_PER_NOTE).coerceAtLeast(0)
        return ShareResult(noteId, ok, failed)
    }
}
