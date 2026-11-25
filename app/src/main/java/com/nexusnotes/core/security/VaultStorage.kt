package com.nexusnotes.core.security

import android.content.Context
import com.nexusnotes.domain.vault.VaultCrypto
import com.nexusnotes.domain.vault.VaultMeta
import com.nexusnotes.domain.vault.VaultMetaStore
import com.nexusnotes.domain.vault.VaultPolicy
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Vault files live in filesDir/vault/: never in the shared attachments folder. */
fun vaultDirectory(context: Context): File = File(context.applicationContext.filesDir, "vault").apply { mkdirs() }

/** vault/vault.meta, written atomically (temp file + fsync + rename). */
class FileVaultMetaStore(context: Context) : VaultMetaStore {
    private val file = File(vaultDirectory(context), "vault.meta")

    @Synchronized
    override fun read(): VaultMeta? = if (file.isFile) VaultMeta.decode(file.readBytes()) else null

    @Synchronized
    override fun write(meta: VaultMeta) {
        val temp = File(file.parentFile, "vault.meta.tmp")
        temp.outputStream().use {
            it.write(meta.encode())
            it.fd.sync()
        }
        if (!temp.renameTo(file)) error("Could not save vault settings.")
    }

    @Synchronized
    override fun delete() {
        file.delete()
    }
}

/**
 * Encrypted attachment files: vault/files/<id>.nxv. Only ciphertext is ever written to disk
 * (also the temporary file used while importing).
 */
class VaultFileStore(context: Context) {
    private val root = vaultDirectory(context)
    private val dir: File get() = File(root, "files").apply { mkdirs() }

    fun file(fileName: String): File = File(dir, fileName)

    /** Encrypts [input] into a new file; returns its name and plaintext size. */
    fun write(key: ByteArray, id: String, input: InputStream, aad: String): Pair<String, Long> {
        val name = "$id.nxv"
        val temp = File(dir, "pending-$name")
        try {
            val size = temp.outputStream().buffered().use { out ->
                val n = VaultCrypto.encryptStream(key, input, out, aad, VaultPolicy.MAX_FILE_BYTES)
                out.flush()
                n
            }
            if (!temp.renameTo(file(name))) error("Could not save the file.")
            return name to size
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    fun readAll(key: ByteArray, fileName: String, aad: String): ByteArray =
        file(fileName).inputStream().buffered().use { VaultCrypto.decryptStreamToBytes(key, it, aad) }

    fun decryptTo(key: ByteArray, fileName: String, aad: String, output: OutputStream) {
        file(fileName).inputStream().buffered().use { VaultCrypto.decryptStream(key, it, output, aad) }
    }

    fun delete(fileName: String) {
        if (fileName.isNotBlank() && !fileName.contains('/')) file(fileName).delete()
    }

    /** Removes files no row references (older than 10 min), and pending files older than a day. */
    fun cleanUp(referenced: Set<String>, now: Long) {
        dir.listFiles().orEmpty().forEach { f ->
            val age = now - f.lastModified()
            val pending = f.name.startsWith("pending-")
            if ((pending && age > 86_400_000L) || (!pending && f.name !in referenced && age > 600_000L)) f.delete()
        }
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }
}
