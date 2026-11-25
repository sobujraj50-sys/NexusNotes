package com.nexusnotes.domain.vault

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Data failed authentication: wrong key, or the bytes were changed/corrupted. */
class VaultTamperedException(message: String = "Vault data could not be verified.") : GeneralSecurityException(message)

/** A file is larger than the vault allows. */
class VaultFileTooLargeException : Exception(
    "Vault files can be at most ${VaultPolicy.MAX_FILE_BYTES / (1024 * 1024)} MB.",
)

/**
 * Vault cryptography. Only standard JCA primitives (available on Android API 26+):
 * - AES-256-GCM (authenticated encryption) with a random 96-bit nonce per message,
 * - PBKDF2-HMAC-SHA256 for PIN/passphrase key derivation,
 * - a chunked GCM stream format for files, so large files are never held in memory
 *   just to encrypt them, and truncated/reordered/swapped chunks are detected.
 *
 * Every ciphertext is bound to its purpose and record id with associated data (AAD),
 * so an encrypted note cannot be swapped onto another record without detection.
 */
object VaultCrypto {
    const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val FORMAT_V1: Byte = 1

    /** File format: magic + version + 7-byte random nonce prefix, then chunks. */
    private val FILE_MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'V'.code.toByte(), FORMAT_V1)
    private const val PREFIX_BYTES = 7
    const val CHUNK_BYTES = 64 * 1024

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun newKey(): ByteArray = randomBytes(KEY_BYTES)

    /** PBKDF2-HMAC-SHA256 → 256-bit key. The [secret] array is not modified. */
    fun deriveKey(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations >= 10_000) { "Too few iterations" }
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts a whole message: [1][nonce 12][ciphertext + 16-byte tag]. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: String): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_V1) + nonce + ct
    }

    /** Decrypts [seal] output. Throws [VaultTamperedException] on any mismatch. */
    fun open(key: ByteArray, blob: ByteArray, aad: String): ByteArray {
        if (blob.size < 1 + NONCE_BYTES + TAG_BYTES || blob[0] != FORMAT_V1) throw VaultTamperedException()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob, 1, NONCE_BYTES))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            cipher.doFinal(blob, 1 + NONCE_BYTES, blob.size - 1 - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw VaultTamperedException()
        }
    }

    /**
     * Encrypts [input] to [output] in chunks of [CHUNK_BYTES]. Each chunk has its own nonce:
     * random prefix (7) + chunk counter (4) + last-chunk flag (1). The flag makes truncation
     * detectable; the counter makes reordering detectable. Returns the plaintext size.
     */
    fun encryptStream(key: ByteArray, input: InputStream, output: OutputStream, aad: String, maxBytes: Long): Long {
        val prefix = randomBytes(PREFIX_BYTES)
        output.write(FILE_MAGIC)
        output.write(prefix)
        val aadBytes = aad.toByteArray(Charsets.UTF_8)
        val keySpec = SecretKeySpec(key, "AES")
        var current = readChunk(input, CHUNK_BYTES)
        var index = 0
        var total = 0L
        while (true) {
            val next = if (current.size < CHUNK_BYTES) ByteArray(0) else readChunk(input, CHUNK_BYTES)
            val isLast = next.isEmpty()
            total += current.size
            if (total > maxBytes) throw VaultFileTooLargeException()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, chunkNonce(prefix, index, isLast)))
            cipher.updateAAD(aadBytes)
            output.write(cipher.doFinal(current))
            if (isLast) break
            current = next
            index++
        }
        return total
    }

    /** Decrypts [encryptStream] output into [output]. Throws [VaultTamperedException] on any problem. */
    fun decryptStream(key: ByteArray, input: InputStream, output: OutputStream, aad: String) {
        val header = readChunk(input, FILE_MAGIC.size + PREFIX_BYTES)
        if (header.size != FILE_MAGIC.size + PREFIX_BYTES || !header.copyOfRange(0, FILE_MAGIC.size).contentEquals(FILE_MAGIC)) {
            throw VaultTamperedException()
        }
        val prefix = header.copyOfRange(FILE_MAGIC.size, header.size)
        val aadBytes = aad.toByteArray(Charsets.UTF_8)
        val keySpec = SecretKeySpec(key, "AES")
        val sealedChunk = CHUNK_BYTES + TAG_BYTES
        var current = readChunk(input, sealedChunk)
        var index = 0
        while (true) {
            if (current.size < TAG_BYTES) throw VaultTamperedException()
            val next = if (current.size < sealedChunk) ByteArray(0) else readChunk(input, sealedChunk)
            val isLast = next.isEmpty()
            val plain = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, chunkNonce(prefix, index, isLast)))
                cipher.updateAAD(aadBytes)
                cipher.doFinal(current)
            } catch (e: GeneralSecurityException) {
                throw VaultTamperedException()
            }
            output.write(plain)
            if (isLast) break
            current = next
            index++
        }
    }

    fun decryptStreamToBytes(key: ByteArray, input: InputStream, aad: String): ByteArray =
        ByteArrayOutputStream().also { decryptStream(key, input, it, aad) }.toByteArray()

    private fun chunkNonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray {
        val n = ByteArray(NONCE_BYTES)
        prefix.copyInto(n, 0)
        n[7] = (index ushr 24).toByte()
        n[8] = (index ushr 16).toByte()
        n[9] = (index ushr 8).toByte()
        n[10] = index.toByte()
        n[11] = if (last) 1 else 0
        return n
    }

    /** Reads up to [size] bytes (fewer only at end of stream). */
    private fun readChunk(input: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(buf, off, size - off)
            if (n < 0) break
            off += n
        }
        return if (off == size) buf else buf.copyOf(off)
    }
}
