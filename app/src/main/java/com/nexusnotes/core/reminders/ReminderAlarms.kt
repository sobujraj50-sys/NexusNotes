package com.nexusnotes.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexusnotes.MainActivity
import com.nexusnotes.R

/**
 * Reminder alarms via AlarmManager (no extra library). Exact when the user allowed "Alarms &
 * reminders"; otherwise Android may deliver a few minutes late, which the UI explains.
 */
class ReminderAlarms(context: Context) {
    private val ctx = context.applicationContext
    private val alarms = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(noteId: String, at: Long) {
        val pi = firePending(noteId)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(noteId: String) {
        alarms.cancel(firePending(noteId))
    }

    private fun firePending(noteId: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).setData(noteUri(noteId)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "reminders"
        const val EXTRA_NOTE_ID = "com.nexusnotes.extra.NOTE_ID"
        const val ACTION_OPEN_NOTE = "com.nexusnotes.action.OPEN_NOTE"

        /** Unique per note, so each note has its own alarm and notification intents. */
        fun noteUri(noteId: String): Uri = Uri.parse("nexus://note/" + Uri.encode(noteId))

        fun noteIdFrom(intent: Intent): String? = intent.data?.lastPathSegment

        fun notificationId(noteId: String): Int = noteId.hashCode()

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Note reminders you set in Nexus Notes"
                    },
                )
            }
        }

        /** Shows the reminder. Only the note title is shown, and only when the phone is unlocked. */
        fun show(context: Context, noteId: String, title: String) {
            if (!hasNotificationPermission(context)) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_NOTE).setData(noteUri(noteId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            fun action(a: String) = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ReminderReceiver::class.java).setAction(a).setData(noteUri(noteId)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Nexus Notes reminder")
                .build()
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title.ifBlank { "Untitled note" })
                .setContentText("Reminder · tap to open")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Snooze 10 min", action(ReminderReceiver.ACTION_SNOOZE))
                .addAction(0, "Done", action(ReminderReceiver.ACTION_DONE))
                .build()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                NotificationManagerCompat.from(context).notify(notificationId(noteId), n)
            }
        }

        fun dismiss(context: Context, noteId: String) {
            NotificationManagerCompat.from(context).cancel(notificationId(noteId))
        }
    }
}
