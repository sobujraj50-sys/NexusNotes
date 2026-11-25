package com.nexusnotes.core.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import javax.crypto.Cipher

/**
 * Android's own biometric prompt (no extra library). Offered on Android 11+ with a
 * "strong" (Class 3) fingerprint/face sensor only, because only those can unlock a Keystore key.
 */
object BiometricUnlock {

    enum class Availability { AVAILABLE, NOT_ENROLLED, UNSUPPORTED }

    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Availability.UNSUPPORTED
        val manager = context.getSystemService(BiometricManager::class.java) ?: return Availability.UNSUPPORTED
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
            else -> Availability.UNSUPPORTED
        }
    }

    /**
     * Shows the prompt bound to [cipher]. [onSuccess] gets the now-usable cipher.
     * [onError] gets a message, or null when the user simply cancelled.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun authenticate(
        activity: Activity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Use PIN", executor) { _, _ -> onError(null) }
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val c = result.cryptoObject?.cipher
                    if (c != null) onSuccess(c) else onError("Biometric check did not unlock the key.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The "Use PIN" button is reported through its own listener above.
                    val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    onError(if (cancelled) null else errString.toString())
                }
                // onAuthenticationFailed (a non-matching finger) — the system prompt shows it and lets the user retry.
            },
        )
    }
}
