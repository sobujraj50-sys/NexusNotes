package com.nexusnotes

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/** Long-press the app icon: Quick note, New checklist, Search. */
object AppShortcuts {
    const val ACTION_QUICK_NOTE = "com.nexusnotes.action.QUICK_NOTE"
    const val ACTION_CHECKLIST = "com.nexusnotes.action.NEW_CHECKLIST"
    const val ACTION_SEARCH = "com.nexusnotes.action.SEARCH"

    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(id: String, label: String, action: String) = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_notification))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            .build()
        runCatching {
            manager.setDynamicShortcuts(
                listOf(
                    shortcut("quick_note", "Quick note", ACTION_QUICK_NOTE),
                    shortcut("checklist", "New checklist", ACTION_CHECKLIST),
                    shortcut("search", "Search notes", ACTION_SEARCH),
                ),
            )
        }
    }
}
