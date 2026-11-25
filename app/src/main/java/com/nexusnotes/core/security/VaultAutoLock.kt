package com.nexusnotes.core.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nexusnotes.domain.vault.VaultPolicy

/**
 * Locks the vault after the app has been in the background for the chosen time.
 * Rotation does not count as leaving. A file picker opened from the vault gets a grace period.
 */
class VaultAutoLock(
    private val session: VaultSession,
    private val autoLockMs: () -> Long,
    private val now: () -> Long,
) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
        session.cancelScheduledLock()
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (started > 0 || activity.isChangingConfigurations || !session.unlocked.value) return
        val pickerGrace = if (session.pickerOpenUntil > now()) VaultPolicy.PICKER_GRACE_MS else 0L
        session.scheduleLock(maxOf(autoLockMs(), pickerGrace))
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
