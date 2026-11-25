package com.nexusnotes.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexusnotes.NexusNotesApp
import kotlinx.coroutines.launch

/** Alarm fired / Snooze / Done. Work is short; goAsync keeps the receiver alive while it runs. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = ReminderAlarms.noteIdFrom(intent) ?: return
        val container = (context.applicationContext as NexusNotesApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val reminders = container.reminderRepository
                when (intent.action) {
                    ACTION_FIRE -> reminders.onFired(noteId)?.let { title -> ReminderAlarms.show(context, noteId, title) }
                    ACTION_SNOOZE -> {
                        ReminderAlarms.dismiss(context, noteId)
                        reminders.snooze(noteId)
                    }
                    ACTION_DONE -> ReminderAlarms.dismiss(context, noteId)
                }
            } catch (_: Exception) {
                // Never crash in the background; the reminder row stays and is re-armed later.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.nexusnotes.reminder.FIRE"
        const val ACTION_SNOOZE = "com.nexusnotes.reminder.SNOOZE"
        const val ACTION_DONE = "com.nexusnotes.reminder.DONE"
    }
}

/** Re-arms alarms and the backup job after reboot, app update, clock or time-zone changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as NexusNotesApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderRepository.rearmAll()
                container.autoBackupScheduler.syncWithSettings()
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
