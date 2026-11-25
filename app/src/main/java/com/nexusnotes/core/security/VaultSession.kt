package com.nexusnotes.core.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the vault key in memory while unlocked, and nowhere else.
 * Locking runs "before lock" hooks (editors save their last edit), then wipes the key bytes.
 * The key is gone after process death too, so a killed app always comes back locked.
 *
 * Limitation (documented): the JVM may keep transient copies inside crypto classes; we wipe
 * every copy we own, which is the best an app can do.
 */
class VaultSession(private val scope: CoroutineScope) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var key: ByteArray? = null
    private val hooks = CopyOnWriteArrayList<suspend (ByteArray) -> Unit>()
    private var autoLockJob: Job? = null

    /** Set before launching a picker so leaving for it does not lock instantly. */
    @Volatile var pickerOpenUntil: Long = 0

    fun open(newKey: ByteArray) {
        key?.fill(0)
        key = newKey
        _unlocked.value = true
    }

    /** A private copy of the key for one operation; wipe it after use. Null when locked. */
    fun keyCopy(): ByteArray? = key?.copyOf()

    fun addBeforeLockHook(hook: suspend (ByteArray) -> Unit) = hooks.add(hook)
    fun removeBeforeLockHook(hook: suspend (ByteArray) -> Unit) = hooks.remove(hook)

    suspend fun lockNow() {
        autoLockJob?.cancel()
        val k = keyCopy()
        if (k != null) {
            hooks.forEach { runCatching { it(k) } }
            k.fill(0)
        }
        key?.fill(0)
        key = null
        _unlocked.value = false
    }

    fun lock() {
        scope.launch { lockNow() }
    }

    fun scheduleLock(delayMs: Long) {
        autoLockJob?.cancel()
        if (key == null) return
        autoLockJob = scope.launch {
            delay(delayMs)
            lockNow()
        }
    }

    fun cancelScheduledLock() {
        autoLockJob?.cancel()
        autoLockJob = null
    }
}
