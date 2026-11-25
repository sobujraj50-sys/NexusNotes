package com.nexusnotes.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.database.NexusDatabase
import com.nexusnotes.core.storage.AttachmentStore
import com.nexusnotes.core.storage.PdfExporter
import com.nexusnotes.domain.backup.ExportFormat
import com.nexusnotes.domain.backup.ExportNote
import com.nexusnotes.domain.backup.NoteExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Readable exports for other apps. Exports are NOT backups (they can't be restored) and never
 * include vault notes or Trash.
 */
class ExportRepository(
    private val database: NexusDatabase,
    private val attachmentStore: AttachmentStore,
    private val resolver: ContentResolver,
    private val clock: Clock,
    private val history: suspend (kind: String, fileName: String, summary: String, success: Boolean) -> Unit,
) {
    /** Exports all notes (not in Trash), or only [noteIds]. One note as MARKDOWN/TEXT → a single file. */
    suspend fun export(format: ExportFormat, target: Uri, fileName: String, noteIds: Set<String>? = null): Result<Int> =
        withContext(Dispatchers.IO) {
            val result = runCatching {
                val notes = collect(noteIds)
                val out = resolver.openOutputStream(target, "wt") ?: error("Could not write to that location.")
                out.use { stream ->
                    when (format) {
                        ExportFormat.JSON -> stream.write(NoteExport.toJson(notes.map { it.first }, clock.now()).toByteArray())
                        ExportFormat.PDF -> PdfExporter.write(notes.map { it.first }, stream)
                        ExportFormat.MARKDOWN, ExportFormat.TEXT -> {
                            val md = format == ExportFormat.MARKDOWN
                            if (noteIds != null && notes.size == 1) {
                                val n = notes.single().first
                                stream.write((if (md) NoteExport.toMarkdown(n) else NoteExport.toText(n)).toByteArray())
                            } else {
                                ZipOutputStream(stream).use { zip ->
                                    val used = mutableSetOf<String>()
                                    notes.forEach { (n, files) ->
                                        val name = NoteExport.fileName(n.title, if (md) "md" else "txt", used)
                                        zip.putNextEntry(ZipEntry(name))
                                        zip.write((if (md) NoteExport.toMarkdown(n) else NoteExport.toText(n)).toByteArray())
                                        zip.closeEntry()
                                        if (md) {
                                            val folder = "attachments/" + name.substringBeforeLast('.') + "/"
                                            val usedFiles = mutableSetOf<String>()
                                            files.forEach { (display, stored) ->
                                                val f = attachmentStore.file(stored)
                                                if (!f.isFile) return@forEach
                                                val base = display.substringBeforeLast('.')
                                                val ext = display.substringAfterLast('.', "bin")
                                                zip.putNextEntry(ZipEntry(folder + NoteExport.fileName(base, ext, usedFiles)))
                                                f.inputStream().use { it.copyTo(zip) }
                                                zip.closeEntry()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                notes.size
            }
            history("EXPORT", fileName, result.fold({ "${format.name}: $it notes" }, { it.message ?: "Failed" }), result.isSuccess)
            result
        }

    /** Notes with tags, folder path and attachments (display name → stored file name). */
    private suspend fun collect(noteIds: Set<String>?): List<Pair<ExportNote, List<Pair<String, String>>>> {
        val dao = database.backupDao()
        val folders = dao.folders().associateBy { it.id }
        fun path(id: String?): String? {
            if (id == null) return null
            val parts = mutableListOf<String>()
            var cur = folders[id]
            val seen = mutableSetOf<String>()
            while (cur != null && seen.add(cur.id)) {
                parts.add(0, cur.name)
                cur = cur.parentId?.let { folders[it] }
            }
            return parts.joinToString("/").ifEmpty { null }
        }
        val tags = dao.tags().associate { it.id to it.name }
        val tagsByNote = dao.noteTags().groupBy({ it.noteId }, { tags[it.tagId] }).mapValues { e -> e.value.filterNotNull().sorted() }
        val atts = dao.attachments().groupBy { it.noteId }
        return dao.notes()
            .filter { it.deletedAt == null && (noteIds == null || it.id in noteIds) }
            .sortedByDescending { it.updatedAt }
            .map { n ->
                val files = atts[n.id].orEmpty().map { it.displayName to it.fileName }
                ExportNote(
                    id = n.id,
                    title = n.title,
                    body = n.body,
                    createdAt = n.createdAt,
                    updatedAt = n.updatedAt,
                    tags = tagsByNote[n.id].orEmpty(),
                    folderPath = path(n.folderId),
                    isArchived = n.isArchived,
                    attachmentNames = files.map { it.first },
                ) to files
            }
    }
}
