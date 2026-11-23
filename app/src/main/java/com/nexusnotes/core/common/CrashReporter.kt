package com.nexusnotes.core.common

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Local-only crash capture. When the app crashes, the technical error is written to app-private
 * storage. On the next start the app shows it so the user can copy and send it for a fix.
 * Nothing is uploaded; no note content is included (only the error and stack trace).
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context, appVersion: String) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val report = buildString {
                    appendLine("Nexus Notes $appVersion")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString().take(12_000))
                }
                File(appContext.filesDir, FILE).writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
