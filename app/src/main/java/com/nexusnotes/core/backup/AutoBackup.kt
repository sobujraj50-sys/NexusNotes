package com.nexusnotes.core.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.nexusnotes.NexusNotesApp
import com.nexusnotes.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Periodic automatic backups with Android's JobScheduler (no extra library). */
class AutoBackupScheduler(context: Context, private val settings: SettingsRepository) {
    private val ctx = context.applicationContext
    private val jobs = ctx.getSystemService(JobScheduler::class.java)

    suspend fun syncWithSettings() {
        val s = settings.settings.first()
        if (s.autoBackupEnabled && s.autoBackupFolder != null) schedule(s.autoBackupIntervalDays) else jobs.cancel(JOB_ID)
    }

    private fun schedule(days: Int) {
        val period = days.coerceIn(1, 30) * 24L * 60 * 60 * 1000
        val existing = jobs.getPendingJob(JOB_ID)
        if (existing != null && existing.intervalMillis == period) return
        jobs.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, AutoBackupJobService::class.java))
                .setPeriodic(period)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setPersisted(true)
                .build(),
        )
    }

    companion object {
        const val JOB_ID = 4201
    }
}

class AutoBackupJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as NexusNotesApp).container
        work = container.applicationScope.launch {
            var retry = false
            try {
                val s = container.settingsRepository.settings.first()
                val folder = s.autoBackupFolder
                if (s.autoBackupEnabled && folder != null) {
                    container.openDatabase()
                    retry = container.backupRepository.createAutoBackup(android.net.Uri.parse(folder), s.autoBackupKeep).isFailure
                }
            } catch (_: Exception) {
                retry = true
            } finally {
                jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true // try again later
    }
}
