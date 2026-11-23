package com.nexusnotes

import android.app.Application
import com.nexusnotes.core.common.CrashReporter

class NexusNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, BuildConfig.VERSION_NAME)
        container = AppContainer(this)
    }
}
