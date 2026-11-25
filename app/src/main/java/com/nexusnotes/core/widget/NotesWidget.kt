package com.nexusnotes.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.nexusnotes.MainActivity
import com.nexusnotes.NexusNotesApp
import com.nexusnotes.R
import com.nexusnotes.core.reminders.ReminderAlarms
import com.nexusnotes.domain.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: up to 4 pinned/recent note titles and a "+" button. Titles only — never note
 * text, and never vault notes. Built with RemoteViews (no extra library).
 */
class NotesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as NexusNotesApp).container
        container.applicationScope.launch {
            try {
                container.openDatabase()
                val pinned = container.noteRepository.observePinned().first()
                val recent = container.noteRepository.observeRecent(8).first()
                NotesWidget.render(context, (pinned + recent).distinctBy { it.id }.take(4))
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

object NotesWidget {
    const val ACTION_NEW_NOTE = "com.nexusnotes.action.NEW_NOTE"
    private val rows = intArrayOf(R.id.widget_note_1, R.id.widget_note_2, R.id.widget_note_3, R.id.widget_note_4)

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))

    fun render(context: Context, notes: List<Note>) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_notes)
        rows.forEachIndexed { i, rowId ->
            val note = notes.getOrNull(i)
            if (note == null) {
                views.setViewVisibility(rowId, View.GONE)
            } else {
                views.setViewVisibility(rowId, View.VISIBLE)
                views.setTextViewText(rowId, (if (note.isPinned) "📌 " else "") + note.title.ifBlank { "Untitled" })
                views.setOnClickPendingIntent(
                    rowId,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java).setAction(ReminderAlarms.ACTION_OPEN_NOTE)
                            .setData(ReminderAlarms.noteUri(note.id))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
        views.setViewVisibility(R.id.widget_empty, if (notes.isEmpty()) View.VISIBLE else View.GONE)
        val newNote = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction(ACTION_NEW_NOTE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_new, newNote)
        val openApp = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_title, openApp)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }
}
