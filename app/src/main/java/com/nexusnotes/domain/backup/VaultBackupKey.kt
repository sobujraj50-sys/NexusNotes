package com.nexusnotes.domain.backup

import com.nexusnotes.domain.vault.VaultCrypto
import com.nexusnotes.domain.vault.VaultTamperedException
import java.util.Base64

/**
 * The vault key inside a backup is protected by a separate backup password (PBKDF2-HMAC-SHA256,
 * more rounds than the PIN because a backup file can be attacked offline on fast hardware).
 * The device Keystore key is never involved, so the backup can be restored on a new phone.
 */
object VaultBackupKey {
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD = 10
    private const val AAD = "nexus-vault-backup-v1"

    fun passwordProblem(password: CharArray): String? = when {
        password.size < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters for the backup password."
        password.all { it.isDigit() } -> "Use letters too, not only digits — backup files can be attacked offline."
        else -> null
    }

    fun wrap(vaultKey: ByteArray, password: CharArray, iterations: Int = ITERATIONS): VaultKeyRec {
        val salt = VaultCrypto.randomBytes(16)
        val k = VaultCrypto.deriveKey(password, salt, iterations)
        try {
            val b64 = Base64.getEncoder()
            return VaultKeyRec(b64.encodeToString(salt), iterations, b64.encodeToString(VaultCrypto.seal(k, vaultKey, AAD)))
        } finally {
            k.fill(0)
        }
    }

    /** The vault key, or null if the password is wrong (or the record was altered). */
    fun unwrap(rec: VaultKeyRec, password: CharArray): ByteArray? {
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(rec.salt) }.getOrNull() ?: return null
        val blob = runCatching { b64.decode(rec.wrapped) }.getOrNull() ?: return null
        if (rec.iterations !in 10_000..10_000_000) return null
        val k = VaultCrypto.deriveKey(password, salt, rec.iterations)
        return try {
            VaultCrypto.open(k, blob, AAD)
        } catch (e: VaultTamperedException) {
            null
        } finally {
            k.fill(0)
        }
    }
}
