package com.nexusnotes.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.nexusnotes.domain.attachment.AttachmentPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class AttachmentTooLargeException : Exception(
    "This file is larger than ${AttachmentPolicy.formatSize(AttachmentPolicy.MAX_FILE_BYTES)}.",
)

/**
 * Attachment bytes in app-private storage: filesDir/attachments/<id>.<ext>.
 * Other apps cannot read this folder. Imports are written to a "pending-" file first and only
 * renamed into place once complete, so a half-copied file is never treated as an attachment.
 */
class AttachmentStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    val directory: File
        get() = File(appContext.filesDir, DIR).apply { mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    fun exists(fileName: String): Boolean = file(fileName).isFile

    data class Stored(
        val fileName: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    /** Copies a content:// item into private storage. Only content URIs are accepted. */
    suspend fun importFrom(uri: Uri, id: String): Stored = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Only files chosen through Android's pickers can be attached." }
        var name: String? = null
        var declaredSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) declaredSize = c.getLong(sizeIdx)
            }
        }
        if ((declaredSize ?: 0L) > AttachmentPolicy.MAX_FILE_BYTES) throw AttachmentTooLargeException()
        val mime = resolver.getType(uri) ?: guessMime(name) ?: "application/octet-stream"
        val displayName = AttachmentPolicy.safeDisplayName(name, fallback = "attachment" + extensionFor(mime, null).let { if (it.isEmpty()) "" else ".$it" })
        val input = resolver.openInputStream(uri) ?: error("Could not open the file.")
        val fileName = id + extensionFor(mime, displayName).let { if (it.isEmpty()) "" else ".$it" }
        val (size, sha) = input.use { writeVerified(it, fileName) }
        Stored(fileName, displayName, mime, size, sha)
    }

    /** A fresh pending file for an audio recording. */
    fun newRecordingFile(id: String): File = File(directory, "$PENDING$id.m4a")

    /** Moves a finished recording into place and returns its stored details. */
    suspend fun finishRecording(pending: File, id: String): Stored = withContext(Dispatchers.IO) {
        val fileName = "$id.m4a"
        val (size, sha) = pending.inputStream().use { writeVerified(it, fileName) }
        pending.delete()
        Stored(fileName, "Recording.m4a", "audio/mp4", size, sha)
    }

    /** Copies an existing attachment file under a new id (for duplicated notes). */
    suspend fun copy(fileName: String, newId: String): Stored? = withContext(Dispatchers.IO) {
        val source = file(fileName)
        if (!source.isFile) return@withContext null
        val ext = fileName.substringAfter('.', "")
        val newName = if (ext.isEmpty()) newId else "$newId.$ext"
        val (size, sha) = source.inputStream().use { writeVerified(it, newName) }
        Stored(newName, "", "", size, sha)
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        if (fileName.isNotBlank() && !fileName.contains('/')) file(fileName).delete()
    }

    /**
     * Deletes files no attachment row refers to (e.g. after notes were deleted forever).
     * Files younger than 10 minutes and unfinished "pending-" files younger than a day are kept.
     */
    suspend fun cleanUp(referenced: Set<String>, now: Long): Int = withContext(Dispatchers.IO) {
        var removed = 0
        directory.listFiles().orEmpty().forEach { f ->
            val isPending = f.name.startsWith(PENDING)
            // Young files are skipped: an import may be between "file saved" and "row inserted".
            val orphan = !isPending && f.name !in referenced && now - f.lastModified() > ORPHAN_GRACE_MS
            val stalePending = isPending && now - f.lastModified() > DAY_MS
            if ((orphan || stalePending) && f.delete()) removed++
        }
        removed
    }

    /** Streams [input] to [fileName] via a pending file, enforcing the size limit; returns size and SHA-256. */
    private fun writeVerified(input: InputStream, fileName: String): Pair<Long, String> {
        val target = file(fileName)
        val temp = File(directory, PENDING + fileName)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > AttachmentPolicy.MAX_FILE_BYTES) throw AttachmentTooLargeException()
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
            if (!temp.renameTo(target)) error("Could not save the file.")
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun guessMime(name: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (ext.isEmpty()) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /** A short, safe file extension from the MIME type or name ("" if unknown). */
    private fun extensionFor(mime: String, name: String?): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        val fromName = name?.substringAfterLast('.', "")
        return (fromMime ?: fromName).orEmpty().lowercase().filter { it.isLetterOrDigit() }.take(8)
    }

    companion object {
        const val DIR = "attachments"
        private const val PENDING = "pending-"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val ORPHAN_GRACE_MS = 10L * 60 * 1000
    }
}
