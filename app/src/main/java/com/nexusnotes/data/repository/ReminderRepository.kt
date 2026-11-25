package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.core.reminders.ReminderAlarms
import com.nexusnotes.data.dao.ReminderDao
import com.nexusnotes.data.entity.ReminderEntity
import com.nexusnotes.data.entity.ReminderWithNote
import com.nexusnotes.domain.reminder.Reminder
import com.nexusnotes.domain.reminder.ReminderRepeat
import com.nexusnotes.domain.reminder.ReminderSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/** Reminders: the database row is the truth; alarms are (re)armed from it. */
class ReminderRepository(
    private val dao: ReminderDao,
    private val alarms: ReminderAlarms,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun observe(noteId: String): Flow<Reminder?> = dao.observe(noteId).map { it?.toDomain() }

    fun observeAll(): Flow<List<ReminderWithNote>> = dao.observeAllWithNotes()

    val exactAlarmsAllowed: Boolean get() = alarms.canScheduleExact()

    suspend fun set(noteId: String, at: Long, repeat: ReminderRepeat) {
        val first = ReminderSchedule.nextAfter(at, repeat, clock.now(), zone()) ?: throw IllegalArgumentException("Pick a time in the future.")
        dao.upsert(ReminderEntity(noteId, first, repeat.name, at, clock.now()))
        alarms.schedule(noteId, first)
    }

    suspend fun remove(noteId: String) {
        dao.delete(noteId)
        alarms.cancel(noteId)
    }

    /**
     * Called when an alarm fires. Returns the note title to show, or null if nothing should be shown
     * (reminder removed, note deleted or in Trash). Repeating reminders are armed for next time.
     */
    suspend fun onFired(noteId: String): String? {
        val r = dao.getWithNote(noteId) ?: return null
        if (r.deletedAt != null) {
            remove(noteId)
            return null
        }
        val repeat = ReminderRepeat.entries.firstOrNull { it.name == r.repeat } ?: ReminderRepeat.NONE
        val next = ReminderSchedule.nextAfter(r.anchorAt, repeat, clock.now(), zone())
        if (next == null) {
            dao.delete(noteId)
        } else {
            dao.upsert(ReminderEntity(noteId, next, r.repeat, r.anchorAt, clock.now()))
            alarms.schedule(noteId, next)
        }
        return r.title
    }

    suspend fun snooze(noteId: String) {
        val r = dao.get(noteId)
        val at = clock.now() + ReminderSchedule.SNOOZE_MS
        if (r == null) {
            // One-time reminder already removed when it fired: bring it back for the snooze.
            dao.upsert(ReminderEntity(noteId, at, ReminderRepeat.NONE.name, at, clock.now()))
            alarms.schedule(noteId, at)
        } else {
            // Repeating: show again soon; the regular schedule continues afterwards.
            alarms.schedule(noteId, minOf(at, r.triggerAt))
        }
    }

    /** After reboot, time-zone change, app update or restore. Missed one-time reminders show soon. */
    suspend fun rearmAll() {
        val now = clock.now()
        dao.all().forEach { r ->
            val repeat = ReminderRepeat.entries.firstOrNull { it.name == r.repeat } ?: ReminderRepeat.NONE
            val at = when {
                r.triggerAt > now -> r.triggerAt
                repeat == ReminderRepeat.NONE -> now + 5_000 // missed while the phone was off
                else -> ReminderSchedule.nextAfter(r.anchorAt, repeat, now, zone()) ?: return@forEach
            }
            if (at != r.triggerAt) dao.upsert(r.copy(triggerAt = at))
            alarms.schedule(r.noteId, at)
        }
    }

    private fun ReminderEntity.toDomain() = Reminder(
        noteId = noteId,
        triggerAt = triggerAt,
        repeat = ReminderRepeat.entries.firstOrNull { it.name == repeat } ?: ReminderRepeat.NONE,
        anchorAt = anchorAt,
    )
}
