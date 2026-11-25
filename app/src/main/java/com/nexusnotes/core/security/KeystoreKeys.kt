package com.nexusnotes.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.nexusnotes.domain.vault.DeviceKeyWrapper
import com.nexusnotes.domain.vault.VaultKeysLostException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val IV_BYTES = 12

private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

/**
 * Non-exportable AES-256-GCM key in Android Keystore (hardware-backed where the phone has it).
 * It never leaves the Keystore, so the vault files are useless on another device.
 * No user authentication is attached: the PIN layer inside provides that.
 */
class KeystoreDeviceKey(private val alias: String = "nexus_vault_device_v1") : DeviceKeyWrapper {

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    private fun getOrCreate(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreate())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val key = try {
            existingKey()
        } catch (e: GeneralSecurityException) {
            throw VaultKeysLostException(e)
        } ?: throw VaultKeysLostException()
        if (blob.size <= IV_BYTES) throw VaultKeysLostException()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV_BYTES))
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw VaultKeysLostException(e)
        }
    }

    override fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }
}

/** The biometric key was invalidated (e.g. a new fingerprint was added) and must be set up again. */
class BiometricKeyInvalidatedException : Exception("Biometric unlock was reset because your fingerprints or face data changed.")

/**
 * Keystore key that can only be used right after a strong biometric check (per use).
 * It wraps the vault key so a fingerprint can open the vault without typing the PIN.
 * Android deletes/invalidates it when biometrics change, which is the safe behaviour.
 */
class BiometricKey(private val alias: String = "nexus_vault_bio_v1") {

    private fun create(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        init(builder.build())
        generateKey()
    }

    /** Cipher for enabling biometrics. Pass it to the biometric prompt. */
    fun encryptCipher(): Cipher {
        delete() // always start from a fresh key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, create())
        return cipher
    }

    /** Cipher for unlocking; [blob] is iv + ciphertext from [seal]. */
    fun decryptCipher(blob: ByteArray): Cipher {
        val key = runCatching { keyStore().getKey(alias, null) as SecretKey? }.getOrNull()
            ?: throw BiometricKeyInvalidatedException()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV_BYTES))
            return cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            throw BiometricKeyInvalidatedException()
        }
    }

    /** After a successful prompt with [encryptCipher]. */
    fun seal(authenticated: Cipher, vaultKey: ByteArray): ByteArray = authenticated.iv + authenticated.doFinal(vaultKey)

    /** After a successful prompt with [decryptCipher]. */
    fun open(authenticated: Cipher, blob: ByteArray): ByteArray = authenticated.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)

    fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }
}
