package com.nexusnotes.domain.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class VaultCryptoTest {
    private val key = VaultCrypto.newKey()

    @Test fun sealOpenRoundTrip() {
        val msg = "গোপন নোট — secret".toByteArray()
        val blob = VaultCrypto.seal(key, msg, "note:1")
        assertArrayEquals(msg, VaultCrypto.open(key, blob, "note:1"))
    }

    @Test fun ciphertextDoesNotContainPlaintext() {
        val blob = VaultCrypto.seal(key, "my bank password".toByteArray(), "note:1")
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("password"))
    }

    @Test fun sameMessageEncryptsDifferently() {
        val m = "x".toByteArray()
        assertNotEquals(VaultCrypto.seal(key, m, "a").toList(), VaultCrypto.seal(key, m, "a").toList())
    }

    @Test fun wrongKeyRejected() {
        val blob = VaultCrypto.seal(key, "x".toByteArray(), "a")
        assertThrows(VaultTamperedException::class.java) { VaultCrypto.open(VaultCrypto.newKey(), blob, "a") }
    }

    @Test fun swappedRecordRejected() {
        val blob = VaultCrypto.seal(key, "x".toByteArray(), "note:1")
        assertThrows(VaultTamperedException::class.java) { VaultCrypto.open(key, blob, "note:2") }
    }

    @Test fun flippedBitRejected() {
        val blob = VaultCrypto.seal(key, "hello".toByteArray(), "a")
        for (i in blob.indices) {
            val bad = blob.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(VaultTamperedException::class.java) { VaultCrypto.open(key, bad, "a") }
        }
    }

    @Test fun truncatedBlobRejected() {
        assertThrows(VaultTamperedException::class.java) { VaultCrypto.open(key, ByteArray(5), "a") }
    }

    @Test fun pbkdf2IsDeterministicAndSaltDependent() {
        val salt = ByteArray(16) { it.toByte() }
        val a = VaultCrypto.deriveKey("123987".toCharArray(), salt, 10_000)
        val b = VaultCrypto.deriveKey("123987".toCharArray(), salt, 10_000)
        val c = VaultCrypto.deriveKey("123987".toCharArray(), ByteArray(16), 10_000)
        assertArrayEquals(a, b)
        assertEquals(32, a.size)
        assertNotEquals(a.toList(), c.toList())
    }

    private fun encrypt(data: ByteArray, aad: String = "file:1"): ByteArray {
        val out = ByteArrayOutputStream()
        val n = VaultCrypto.encryptStream(key, ByteArrayInputStream(data), out, aad, Long.MAX_VALUE)
        assertEquals(data.size.toLong(), n)
        return out.toByteArray()
    }

    private fun decrypt(blob: ByteArray, aad: String = "file:1") =
        VaultCrypto.decryptStreamToBytes(key, ByteArrayInputStream(blob), aad)

    @Test fun streamRoundTripAtChunkBoundaries() {
        val c = VaultCrypto.CHUNK_BYTES
        for (size in listOf(0, 1, c - 1, c, c + 1, 2 * c, 3 * c + 17)) {
            val data = VaultCrypto.randomBytes(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test fun streamTruncationDetected() {
        val c = VaultCrypto.CHUNK_BYTES
        val blob = encrypt(VaultCrypto.randomBytes(3 * c))
        // Cut exactly at a chunk boundary: the remaining last chunk lacks the "last" flag.
        val header = 11
        val cut = blob.copyOf(header + 2 * (c + 16))
        assertThrows(VaultTamperedException::class.java) { decrypt(cut) }
        assertThrows(VaultTamperedException::class.java) { decrypt(blob.copyOf(blob.size - 1)) }
    }

    @Test fun streamReorderDetected() {
        val c = VaultCrypto.CHUNK_BYTES
        val blob = encrypt(VaultCrypto.randomBytes(2 * c + 10))
        val h = 11
        val s = c + 16
        val swapped = blob.copyOfRange(0, h) + blob.copyOfRange(h + s, h + 2 * s) + blob.copyOfRange(h, h + s) + blob.copyOfRange(h + 2 * s, blob.size)
        assertThrows(VaultTamperedException::class.java) { decrypt(swapped) }
    }

    @Test fun streamWrongAadOrGarbageRejected() {
        val blob = encrypt("abc".toByteArray())
        assertThrows(VaultTamperedException::class.java) { decrypt(blob, "file:2") }
        assertThrows(VaultTamperedException::class.java) { decrypt("not a vault file".toByteArray()) }
    }

    @Test fun streamSizeLimitEnforced() {
        assertThrows(VaultFileTooLargeException::class.java) {
            VaultCrypto.encryptStream(key, ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), "a", 99)
        }
    }

    @Test fun codecRoundTrip() {
        val (t, b) = VaultCodec.decodeNote(VaultCodec.encodeNote("শিরোনাম", "body\nline 2 😀"))
        assertEquals("শিরোনাম", t)
        assertEquals("body\nline 2 😀", b)
        val m = VaultCodec.AttachmentMeta("photo.jpg", "image/jpeg", 1234)
        assertEquals(m, VaultCodec.decodeAttachment(VaultCodec.encodeAttachment(m)))
    }
}
