package com.nexusnotes.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.security.BiometricKeyInvalidatedException
import com.nexusnotes.data.repository.VaultRepository
import com.nexusnotes.domain.vault.UnlockResult
import com.nexusnotes.domain.vault.VaultNote
import com.nexusnotes.domain.vault.VaultPolicy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher

enum class VaultPhase { LOADING, SETUP, LOCKED, UNLOCKED, KEYS_LOST }

data class VaultUiState(
    val phase: VaultPhase = VaultPhase.LOADING,
    val busy: Boolean = false,
    val pinError: String? = null,
    val lockedUntil: Long = 0,
    val biometricEnabled: Boolean = false,
    val autoLockMs: Long = VaultPolicy.DEFAULT_AUTO_LOCK_MS,
    val notes: List<VaultNote> = emptyList(),
    val damaged: Int = 0,
    val query: String = "",
)

private data class Local(
    val busy: Boolean = false,
    val pinError: String? = null,
    val lockedUntil: Long = 0,
    val keysLost: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMs: Long = VaultPolicy.DEFAULT_AUTO_LOCK_MS,
    val query: String = "",
)

class VaultViewModel(private val vault: VaultRepository, private val clock: Clock) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<VaultUiState> = combine(vault.isSetUp, vault.unlocked, vault.observeNotes(), local) { setUp, open, list, l ->
        val phase = when {
            setUp == null -> VaultPhase.LOADING
            !setUp -> VaultPhase.SETUP
            l.keysLost -> VaultPhase.KEYS_LOST
            open -> VaultPhase.UNLOCKED
            else -> VaultPhase.LOCKED
        }
        val q = l.query.trim()
        val notes = if (q.isEmpty()) list.notes else list.notes.filter {
            it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
        }
        VaultUiState(phase, l.busy, l.pinError, l.lockedUntil, l.biometricEnabled, l.autoLockMs, notes, list.damaged, l.query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    init {
        viewModelScope.launch {
            vault.refresh()
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val until = vault.lockoutUntil()
        local.update {
            it.copy(
                biometricEnabled = vault.biometricEnabled(),
                autoLockMs = vault.autoLockMs(),
                lockedUntil = if (until > clock.now()) until else 0,
            )
        }
    }

    fun setQuery(q: String) = local.update { it.copy(query = q) }

    fun clearPinError() = local.update { it.copy(pinError = null) }

    fun create(pin: String, confirm: String) {
        if (local.value.busy) return
        val chars = pin.toCharArray()
        VaultPolicy.pinProblem(chars)?.let { msg -> local.update { it.copy(pinError = msg) }; return }
        if (pin != confirm) { local.update { it.copy(pinError = "The two entries don't match.") }; return }
        local.update { it.copy(busy = true, pinError = null) }
        viewModelScope.launch {
            vault.create(chars).onFailure { e -> _messages.send(e.message ?: "Could not create the vault.") }
            chars.fill(' ')
            local.update { it.copy(busy = false) }
            reloadSettings()
        }
    }

    fun unlock(pin: String) {
        if (local.value.busy || pin.isEmpty()) return
        val chars = pin.toCharArray()
        local.update { it.copy(busy = true, pinError = null) }
        viewModelScope.launch {
            val r = vault.unlock(chars)
            chars.fill(' ')
            local.update { it.copy(busy = false) }
            handle(r)
        }
    }

    private suspend fun handle(r: UnlockResult) {
        when (r) {
            is UnlockResult.Success -> local.update { it.copy(pinError = null, lockedUntil = 0) }
            is UnlockResult.WrongPin -> local.update {
                it.copy(
                    pinError = when {
                        r.lockedUntil > 0 -> "Wrong PIN. Too many attempts — wait before trying again."
                        r.attemptsLeft <= 2 -> "Wrong PIN. ${r.attemptsLeft} tries left before a wait."
                        else -> "Wrong PIN."
                    },
                    lockedUntil = r.lockedUntil,
                )
            }
            is UnlockResult.LockedOut -> local.update { it.copy(lockedUntil = r.until, pinError = "Too many wrong attempts.") }
            UnlockResult.KeysLost -> local.update { it.copy(keysLost = true) }
            UnlockResult.NotSetUp -> vault.refresh()
        }
        reloadSettings()
    }

    /** For the biometric prompt. Null (with a message) if it can't be used. */
    suspend fun biometricUnlockCipher(): Cipher? = runCatching { vault.biometricUnlockCipher() }.getOrElse { e ->
        local.update { it.copy(biometricEnabled = false) }
        _messages.send(
            if (e is BiometricKeyInvalidatedException) {
                "Fingerprint unlock was turned off because your fingerprints/face changed. Unlock with your PIN and turn it on again."
            } else {
                "Fingerprint unlock is not available. Use your PIN."
            },
        )
        null
    }

    fun completeBiometricUnlock(cipher: Cipher) {
        viewModelScope.launch {
            if (!vault.completeBiometricUnlock(cipher)) _messages.send("Fingerprint unlock failed. Use your PIN.")
            reloadSettings()
        }
    }

    fun biometricMessage(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    fun lock() = vault.lock()

    fun enableBiometricCipher(): Cipher? = runCatching { vault.biometricEnableCipher() }.getOrElse {
        viewModelScope.launch { _messages.send("Fingerprint unlock needs a screen lock and an enrolled fingerprint or face.") }
        null
    }

    fun completeBiometricEnable(cipher: Cipher) {
        viewModelScope.launch {
            runCatching { vault.completeBiometricEnable(cipher) }
                .onSuccess { _messages.send("Fingerprint unlock is on.") }
                .onFailure { _messages.send("Could not turn on fingerprint unlock.") }
            reloadSettings()
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            vault.disableBiometric()
            reloadSettings()
            _messages.send("Fingerprint unlock is off.")
        }
    }

    fun setAutoLock(ms: Long) {
        viewModelScope.launch {
            vault.setAutoLock(ms)
            reloadSettings()
        }
    }

    /** Returns an error message or null on success. */
    suspend fun changePin(old: String, new: String, confirm: String): String? {
        val n = new.toCharArray()
        VaultPolicy.pinProblem(n)?.let { return it }
        if (new != confirm) return "The new entries don't match."
        val r = vault.changePin(old.toCharArray(), n)
        n.fill(' ')
        return when (r) {
            is UnlockResult.Success -> { _messages.send("PIN changed."); null }
            is UnlockResult.WrongPin -> "Current PIN is wrong."
            is UnlockResult.LockedOut -> "Too many wrong attempts. Try later."
            else -> "Could not change the PIN."
        }.also { reloadSettings() }
    }

    fun reset() {
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            runCatching { vault.reset() }
            local.value = Local()
            reloadSettings()
            _messages.send("The vault was deleted.")
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                VaultViewModel(c.vaultRepository, c.clock)
            }
        }
    }
}
