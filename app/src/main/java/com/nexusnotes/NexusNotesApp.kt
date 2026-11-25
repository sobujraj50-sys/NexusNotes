package com.nexusnotes

import android.app.Application
import com.nexusnotes.core.common.CrashReporter
import com.nexusnotes.core.security.VaultAutoLock

class NexusNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, BuildConfig.VERSION_NAME)
        container = AppContainer(this)
        AppShortcuts.publish(this)
        registerActivityLifecycleCallbacks(
            VaultAutoLock(
                session = container.vaultSession,
                // Only consulted while the vault is unlocked, i.e. after the repository exists.
                autoLockMs = { container.vaultRepository.autoLockMsBlocking() },
                now = container.clock::now,
            ),
        )
    }
}
