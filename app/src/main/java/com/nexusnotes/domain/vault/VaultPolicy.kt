package com.nexusnotes.domain.vault

/** Vault rules, in one place so UI, logic and tests agree. */
object VaultPolicy {
    const val MIN_PIN = 6
    const val MAX_PIN = 64

    /** PBKDF2 iterations for new vaults (stored per vault, so it can be raised later). */
    const val PBKDF2_ITERATIONS = 210_000
    const val SALT_BYTES = 16

    /** Wrong attempts allowed before waiting is required. */
    const val FREE_ATTEMPTS = 5
    private const val FIRST_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 60L * 60 * 1000

    const val MAX_FILE_BYTES = 25L * 1024 * 1024
    const val MAX_ATTACHMENTS_PER_NOTE = 30

    /** Auto-lock choices after leaving the app (ms). 0 = as soon as you leave. */
    val AUTO_LOCK_OPTIONS = listOf(0L, 60_000L, 5 * 60_000L, 15 * 60_000L)
    const val DEFAULT_AUTO_LOCK_MS = 60_000L

    /** When a file picker is open the app is "in the background"; give it this long. */
    const val PICKER_GRACE_MS = 2 * 60_000L

    fun autoLockLabel(ms: Long): String = when (ms) {
        0L -> "Immediately"
        60_000L -> "After 1 minute"
        else -> "After ${ms / 60_000} minutes"
    }

    /** null = OK, otherwise the reason the PIN/passphrase is not accepted. */
    fun pinProblem(pin: CharArray): String? = when {
        pin.size < MIN_PIN -> "Use at least $MIN_PIN characters."
        pin.size > MAX_PIN -> "Use at most $MAX_PIN characters."
        pin.any { it.isWhitespace() && it != ' ' } -> "Tabs and line breaks are not allowed."
        pin.all { it == pin[0] } -> "Don't use the same character repeated."
        isSequence(pin) -> "Don't use a simple sequence like 123456."
        else -> null
    }

    private fun isSequence(pin: CharArray): Boolean {
        if (!pin.all { it.isDigit() }) return false
        val up = (1 until pin.size).all { pin[it] - pin[it - 1] == 1 }
        val down = (1 until pin.size).all { pin[it - 1] - pin[it] == 1 }
        return up || down
    }

    /** Wait time after [failedAttempts] consecutive wrong entries. */
    fun lockoutMs(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0
        val steps = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(10)
        return (FIRST_LOCKOUT_MS shl steps).coerceAtMost(MAX_LOCKOUT_MS)
    }

    fun formatWait(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
    }
}
