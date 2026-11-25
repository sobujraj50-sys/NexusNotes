package com.nexusnotes.domain.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file = ZIP:
 *   data.json              all records (vault records stay encrypted)
 *   attachments/<file>     attachment bytes, as stored
 *   vault/<file>.nxv       vault files, still encrypted
 *   manifest.json          format version + size and SHA-256 of every other entry (written last)
 */
object BackupPaths {
    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val ATTACHMENTS = "attachments/"
    const val VAULT = "vault/"

    private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")

    /** File names inside the app's folders: no paths, no "..", nothing surprising. */
    fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name) && !name.contains("..")

    fun isAllowedEntry(path: String): Boolean = when {
        path == MANIFEST || path == DATA -> true
        path.startsWith(ATTACHMENTS) -> isSafeFileName(path.removePrefix(ATTACHMENTS))
        path.startsWith(VAULT) -> isSafeFileName(path.removePrefix(VAULT))
        else -> false
    }
}

data class BackupLimits(
    val maxEntries: Int = 50_000,
    val maxDataBytes: Long = 256L * 1024 * 1024,
    val maxTotalBytes: Long = 8L * 1024 * 1024 * 1024,
)

class BackupWriter(output: OutputStream) {
    private val zip = ZipOutputStream(output)
    private val hashes = mutableListOf<FileHash>()

    fun writeData(data: BackupData) {
        val bytes = BackupJson.encodeData(data).toByteArray(Charsets.UTF_8)
        writeEntry(BackupPaths.DATA, bytes.inputStream())
    }

    /** Adds a file. [path] must be "attachments/<name>" or "vault/<name>". */
    fun writeFile(path: String, input: InputStream) {
        require(BackupPaths.isAllowedEntry(path) && path != BackupPaths.DATA && path != BackupPaths.MANIFEST) { "Bad path $path" }
        writeEntry(path, input)
    }

    private fun writeEntry(path: String, input: InputStream) {
        zip.putNextEntry(ZipEntry(path))
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
            zip.write(buf, 0, n)
            size += n
        }
        zip.closeEntry()
        hashes += FileHash(path, size, hex(digest.digest()))
    }

    /** Writes the manifest and closes the archive (and the underlying stream). */
    fun finish(appVersion: String, dbVersion: Int, createdAt: Long, data: BackupData): Manifest {
        val manifest = Manifest(
            formatVersion = BackupJson.FORMAT_VERSION,
            appVersion = appVersion,
            dbVersion = dbVersion,
            createdAt = createdAt,
            counts = data.counts(),
            files = hashes.toList(),
            includesVault = data.includesVault,
        )
        zip.putNextEntry(ZipEntry(BackupPaths.MANIFEST))
        zip.write(BackupJson.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.close()
        return manifest
    }
}

/** A fully read and verified backup. Files are in [stagingDir] (caller deletes it). */
class StagedBackup(
    val manifest: Manifest,
    val data: BackupData,
    private val stagingDir: File,
    private val stagedNames: Map<String, String>,
) {
    /** Staged copy of an archive entry, e.g. "attachments/x.jpg". */
    fun file(path: String): File? = stagedNames[path]?.let { File(stagingDir, it) }
}

object BackupReader {

    /**
     * Reads and verifies the whole archive. Nothing is restored here. Throws
     * [BackupInvalidException] with a user-facing reason for any problem.
     */
    fun read(input: InputStream, stagingDir: File, limits: BackupLimits = BackupLimits()): StagedBackup {
        stagingDir.mkdirs()
        var manifestJson: String? = null
        var dataBytes: ByteArray? = null
        val actual = mutableMapOf<String, FileHash>()
        val staged = mutableMapOf<String, String>()
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > limits.maxEntries) throw BackupInvalidException("The backup has too many files.")
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (!BackupPaths.isAllowedEntry(name)) throw BackupInvalidException("The backup contains an unexpected file (“${name.take(60)}”).")
                    if (name in actual || (name == BackupPaths.MANIFEST && manifestJson != null)) {
                        throw BackupInvalidException("The backup contains a duplicate file.")
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    var size = 0L
                    val sink: OutputStream = when (name) {
                        BackupPaths.MANIFEST, BackupPaths.DATA -> ByteArrayOutputStream()
                        else -> {
                            val stagedName = "e" + staged.size
                            staged[name] = stagedName
                            File(stagingDir, stagedName).outputStream().buffered()
                        }
                    }
                    sink.use { out ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            total += n
                            if (total > limits.maxTotalBytes) throw BackupInvalidException("The backup is too large.")
                            if ((name == BackupPaths.DATA || name == BackupPaths.MANIFEST) && size > limits.maxDataBytes) {
                                throw BackupInvalidException("The backup's data is too large.")
                            }
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                    when (name) {
                        BackupPaths.MANIFEST -> manifestJson = (sink as ByteArrayOutputStream).toString("UTF-8")
                        else -> {
                            if (name == BackupPaths.DATA) dataBytes = (sink as ByteArrayOutputStream).toByteArray()
                            actual[name] = FileHash(name, size, hex(digest.digest()))
                        }
                    }
                }
            }
        } catch (e: BackupInvalidException) {
            throw e
        } catch (e: ZipException) {
            throw BackupInvalidException("The file is not a valid backup or is damaged (${e.message ?: "ZIP error"}).")
        } catch (e: java.io.EOFException) {
            throw BackupInvalidException("The backup file is incomplete (cut off).")
        }

        val manifest = BackupJson.decodeManifest(
            manifestJson ?: throw BackupInvalidException(
                if (entries == 0) "The file is empty or not a backup." else "The backup is incomplete (no manifest).",
            ),
        )
        // Integrity: exactly the listed files, with the same size and hash.
        val expected = manifest.files.associateBy { it.path }
        if (expected.size != manifest.files.size) throw BackupInvalidException("The backup manifest is damaged.")
        val missing = expected.keys - actual.keys
        val extra = actual.keys - expected.keys
        if (missing.isNotEmpty()) throw BackupInvalidException("The backup is incomplete (${missing.size} file(s) missing).")
        if (extra.isNotEmpty()) throw BackupInvalidException("The backup contains files not listed in its manifest.")
        val changed = expected.values.filter { it != actual[it.path] }
        if (changed.isNotEmpty()) throw BackupInvalidException("The backup is damaged: ${changed.size} file(s) failed the integrity check.")

        val data = BackupJson.decodeData(String(dataBytes ?: throw BackupInvalidException("The backup has no data."), Charsets.UTF_8))
        validate(data, manifest, actual.keys)
        return StagedBackup(manifest, data, stagingDir, staged)
    }

    /** Cross-checks records against each other and against the files present. */
    private fun validate(data: BackupData, manifest: Manifest, paths: Set<String>) {
        if (manifest.includesVault != data.includesVault) throw BackupInvalidException("The backup manifest doesn't match its data.")
        val counts = data.counts()
        manifest.counts.forEach { (k, v) ->
            if (counts[k] != null && counts[k] != v) throw BackupInvalidException("The backup manifest doesn't match its data.")
        }
        fun ids(list: List<String>, what: String) {
            if (list.size != list.toSet().size) throw BackupInvalidException("The backup has duplicate $what.")
        }
        ids(data.notes.map { it.id }, "notes")
        ids(data.folders.map { it.id }, "folders")
        ids(data.tags.map { it.id }, "tags")
        ids(data.prompts.map { it.id }, "prompts")
        ids(data.attachments.map { it.id }, "attachments")
        ids(data.vaultItems.map { it.id }, "vault notes")
        val noteIds = data.notes.map { it.id }.toSet()
        data.attachments.forEach {
            if (!BackupPaths.isSafeFileName(it.fileName)) throw BackupInvalidException("The backup has an invalid file name.")
            if (it.noteId !in noteIds) throw BackupInvalidException("The backup has an attachment without its note.")
            if (BackupPaths.ATTACHMENTS + it.fileName !in paths) throw BackupInvalidException("The backup is missing an attachment file.")
        }
        val vaultIds = data.vaultItems.map { it.id }.toSet()
        data.vaultAttachments.forEach {
            if (!BackupPaths.isSafeFileName(it.fileName)) throw BackupInvalidException("The backup has an invalid file name.")
            if (it.itemId !in vaultIds) throw BackupInvalidException("The backup has a vault file without its note.")
            if (BackupPaths.VAULT + it.fileName !in paths) throw BackupInvalidException("The backup is missing a vault file.")
        }
        if (data.vaultItems.isNotEmpty() && data.vaultKey == null) throw BackupInvalidException("The backup's vault key is missing.")
    }
}

internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
