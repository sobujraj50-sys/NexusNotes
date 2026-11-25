package com.nexusnotes.domain.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Decrypted vault note. Exists only in memory while the vault is unlocked. */
data class VaultNote(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Decrypted vault attachment metadata (name, type and size are secret too). */
data class VaultAttachment(
    val id: String,
    val itemId: String,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/**
 * Plaintext layout inside the encrypted payloads. Length-prefixed UTF-8, versioned.
 * AAD strings bind each ciphertext to its record ("note:<id>", "att:<id>", "file:<id>").
 */
object VaultCodec {
    private const val V1 = 1

    fun aadNote(id: String) = "note:$id"
    fun aadAttachment(id: String) = "att:$id"
    fun aadFile(id: String) = "file:$id"

    fun encodeNote(title: String, body: String): ByteArray = write { writeStr(title); writeStr(body) }

    fun decodeNote(bytes: ByteArray): Pair<String, String> = read(bytes) { readStr() to readStr() }

    data class AttachmentMeta(val displayName: String, val mimeType: String, val sizeBytes: Long)

    fun encodeAttachment(m: AttachmentMeta): ByteArray = write {
        writeStr(m.displayName)
        writeStr(m.mimeType)
        writeLong(m.sizeBytes)
    }

    fun decodeAttachment(bytes: ByteArray): AttachmentMeta = read(bytes) { AttachmentMeta(readStr(), readStr(), readLong()) }

    private inline fun write(block: DataOutputStream.() -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { it.writeByte(V1); it.block() }
        return bos.toByteArray()
    }

    private inline fun <T> read(bytes: ByteArray, block: DataInputStream.() -> T): T =
        DataInputStream(ByteArrayInputStream(bytes)).use {
            require(it.readUnsignedByte() == V1) { "Unknown vault record version" }
            it.block()
        }

    private fun DataOutputStream.writeStr(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeInt(b.size)
        write(b)
    }

    private fun DataInputStream.readStr(): String {
        val n = readInt()
        require(n in 0..(64 * 1024 * 1024)) { "Bad length" }
        val b = ByteArray(n)
        readFully(b)
        return String(b, Charsets.UTF_8)
    }
}
