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
        if (BuildConfig.DEBUG) {
            // Development aid: report disk/network work on the main thread and leaked resources in Logcat.
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
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
