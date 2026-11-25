package com.nexusnotes.domain.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Properties

/** The device-bound key (Android Keystore) is gone or unusable; the vault cannot be opened. */
class VaultKeysLostException(cause: Throwable? = null) : Exception("The vault's device key is no longer available.", cause)

/**
 * A key that never leaves secure hardware/OS storage (Android Keystore in the app;
 * an in-memory fake in tests). Throws [VaultKeysLostException] if the key is gone.
 */
interface DeviceKeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
    fun delete()
}

/** Non-secret vault settings + the wrapped key. Stored as a small file outside the database. */
data class VaultMeta(
    val salt: ByteArray,
    val iterations: Int,
    /** deviceKey( pinKey( vaultKey ) ) */
    val wrappedKey: ByteArray,
    /** Biometric Keystore key ( vaultKey ): iv(12) + ciphertext, or null if biometrics are off. */
    val biometricKey: ByteArray? = null,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0,
    val autoLockMs: Long = VaultPolicy.DEFAULT_AUTO_LOCK_MS,
    val createdAt: Long = 0,
) {
    fun encode(): ByteArray {
        val p = Properties()
        val b64 = Base64.getEncoder()
        p["format"] = "1"
        p["salt"] = b64.encodeToString(salt)
        p["iterations"] = iterations.toString()
        p["wrappedKey"] = b64.encodeToString(wrappedKey)
        biometricKey?.let { p["biometricKey"] = b64.encodeToString(it) }
        p["failedAttempts"] = failedAttempts.toString()
        p["lockoutUntil"] = lockoutUntil.toString()
        p["autoLockMs"] = autoLockMs.toString()
        p["createdAt"] = createdAt.toString()
        return ByteArrayOutputStream().also { p.store(it, null) }.toByteArray()
    }

    companion object {
        /** Throws IllegalArgumentException if the file is damaged. */
        fun decode(bytes: ByteArray): VaultMeta {
            val p = Properties().apply { load(ByteArrayInputStream(bytes)) }
            require(p.getProperty("format") == "1") { "Unknown vault format" }
            val b64 = Base64.getDecoder()
            fun req(k: String) = requireNotNull(p.getProperty(k)) { "Missing $k" }
            return VaultMeta(
                salt = b64.decode(req("salt")),
                iterations = req("iterations").toInt(),
                wrappedKey = b64.decode(req("wrappedKey")),
                biometricKey = p.getProperty("biometricKey")?.let { b64.decode(it) },
                failedAttempts = p.getProperty("failedAttempts")?.toIntOrNull() ?: 0,
                lockoutUntil = p.getProperty("lockoutUntil")?.toLongOrNull() ?: 0,
                autoLockMs = p.getProperty("autoLockMs")?.toLongOrNull() ?: VaultPolicy.DEFAULT_AUTO_LOCK_MS,
                createdAt = p.getProperty("createdAt")?.toLongOrNull() ?: 0,
            )
        }
    }

    override fun equals(other: Any?) = other is VaultMeta && encode().contentEquals(other.encode())
    override fun hashCode() = encode().contentHashCode()
}

interface VaultMetaStore {
    fun read(): VaultMeta?
    fun write(meta: VaultMeta)
    fun delete()
}

sealed interface UnlockResult {
    /** [key] is the vault key; the caller owns it and must wipe it on lock. */
    class Success(val key: ByteArray) : UnlockResult
    data class WrongPin(val attemptsLeft: Int, val lockedUntil: Long) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object KeysLost : UnlockResult
    data object NotSetUp : UnlockResult
}

/**
 * Creates and opens the vault key. Layers:
 *   vaultKey (random 256-bit) → sealed with a PIN-derived key (PBKDF2) → sealed again with a
 *   non-exportable device key. Copying the files to another device is useless without that
 *   device key, and guessing PINs requires this device (plus the attempt limit below).
 * A wrong PIN is detected by GCM authentication failure — no PIN hash is stored anywhere.
 */
class VaultKeyManager(
    private val deviceKey: DeviceKeyWrapper,
    private val store: VaultMetaStore,
    private val now: () -> Long,
    private val iterations: Int = VaultPolicy.PBKDF2_ITERATIONS,
) {
    /** A damaged settings file still counts as "set up" (so it is never silently replaced). */
    fun isSetUp(): Boolean = runCatching { store.read() != null }.getOrDefault(true)

    /** null if not set up or unreadable. */
    fun meta(): VaultMeta? = runCatching { store.read() }.getOrNull()

    private fun readMeta(): VaultMeta? = try {
        store.read()
    } catch (e: Exception) {
        throw VaultKeysLostException(e)
    }

    /** Creates a new vault and returns its key. Fails if one exists. */
    fun create(pin: CharArray): ByteArray {
        check(!isSetUp()) { "A vault already exists." }
        VaultPolicy.pinProblem(pin)?.let { throw IllegalArgumentException(it) }
        val vaultKey = VaultCrypto.newKey()
        val salt = VaultCrypto.randomBytes(VaultPolicy.SALT_BYTES)
        store.write(
            VaultMeta(
                salt = salt,
                iterations = iterations,
                wrappedKey = wrap(vaultKey, pin, salt, iterations),
                createdAt = now(),
            ),
        )
        return vaultKey
    }

    fun unlock(pin: CharArray): UnlockResult {
        val meta = try {
            readMeta()
        } catch (e: VaultKeysLostException) {
            return UnlockResult.KeysLost
        } ?: return UnlockResult.NotSetUp
        val t = now()
        if (meta.lockoutUntil > t) return UnlockResult.LockedOut(meta.lockoutUntil)
        // Count the attempt BEFORE checking, so killing the app mid-check doesn't give free tries.
        val attempts = meta.failedAttempts + 1
        val counted = meta.copy(failedAttempts = attempts, lockoutUntil = 0)
        store.write(counted)
        val inner = try {
            deviceKey.unwrap(meta.wrappedKey)
        } catch (e: VaultKeysLostException) {
            store.write(meta) // not the user's fault
            return UnlockResult.KeysLost
        }
        val pinKey = VaultCrypto.deriveKey(pin, meta.salt, meta.iterations)
        return try {
            val vaultKey = VaultCrypto.open(pinKey, inner, AAD_PIN)
            store.write(counted.copy(failedAttempts = 0))
            UnlockResult.Success(vaultKey)
        } catch (e: VaultTamperedException) {
            val wait = VaultPolicy.lockoutMs(attempts)
            val until = if (wait > 0) t + wait else 0
            store.write(counted.copy(lockoutUntil = until))
            UnlockResult.WrongPin((VaultPolicy.FREE_ATTEMPTS - attempts).coerceAtLeast(0), until)
        } finally {
            pinKey.fill(0)
            inner.fill(0)
        }
    }

    /** Re-wraps the existing vault key under a new PIN. Old PIN must be correct. */
    fun changePin(oldPin: CharArray, newPin: CharArray): UnlockResult {
        VaultPolicy.pinProblem(newPin)?.let { throw IllegalArgumentException(it) }
        val result = unlock(oldPin)
        if (result is UnlockResult.Success) {
            val meta = meta() ?: return UnlockResult.KeysLost
            val salt = VaultCrypto.randomBytes(VaultPolicy.SALT_BYTES)
            store.write(meta.copy(salt = salt, iterations = iterations, wrappedKey = wrap(result.key, newPin, salt, iterations)))
        }
        return result
    }

    fun setBiometricKey(blob: ByteArray?) {
        val meta = meta() ?: return
        store.write(meta.copy(biometricKey = blob))
    }

    fun setAutoLock(ms: Long) {
        val meta = meta() ?: return
        store.write(meta.copy(autoLockMs = ms))
    }

    /** A successful biometric unlock also clears the wrong-PIN counter. */
    fun recordBiometricSuccess() {
        val meta = meta() ?: return
        if (meta.failedAttempts != 0 || meta.lockoutUntil != 0L) store.write(meta.copy(failedAttempts = 0, lockoutUntil = 0))
    }

    /** Destroys the vault key material. Encrypted data becomes permanently unreadable. */
    fun destroy() {
        store.delete()
        runCatching { deviceKey.delete() }
    }

    private fun wrap(vaultKey: ByteArray, pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val pinKey = VaultCrypto.deriveKey(pin, salt, iterations)
        try {
            return deviceKey.wrap(VaultCrypto.seal(pinKey, vaultKey, AAD_PIN))
        } finally {
            pinKey.fill(0)
        }
    }

    companion object {
        const val AAD_PIN = "nexus-vault-key-v1"
        const val AAD_BIOMETRIC = "nexus-vault-bio-v1"
    }
}
