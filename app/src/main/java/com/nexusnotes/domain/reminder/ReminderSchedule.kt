package com.nexusnotes.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ReminderRepeat(val label: String) {
    NONE("Once"),
    DAILY("Every day"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
}

/** One reminder per note. */
data class Reminder(
    val noteId: String,
    val triggerAt: Long,
    val repeat: ReminderRepeat,
    /** The originally chosen time, so monthly reminders on the 31st stay on the last day. */
    val anchorAt: Long,
)

object ReminderSchedule {
    const val SNOOZE_MS = 10 * 60_000L

    /**
     * The first occurrence strictly after [now] for a reminder anchored at [anchor].
     * Uses wall-clock time in [zone], so 9:00 stays 9:00 across daylight-saving changes.
     * Returns null for a one-time reminder that is in the past.
     */
    fun nextAfter(anchor: Long, repeat: ReminderRepeat, now: Long, zone: ZoneId): Long? {
        if (anchor > now) return anchor
        if (repeat == ReminderRepeat.NONE) return null
        val start = Instant.ofEpochMilli(anchor).atZone(zone)
        var n = estimateSteps(start, repeat, now, zone)
        while (true) {
            val candidate = step(start, repeat, n).toInstant().toEpochMilli()
            if (candidate > now) return candidate
            n++
        }
    }

    private fun estimateSteps(start: ZonedDateTime, repeat: ReminderRepeat, now: Long, zone: ZoneId): Long {
        val end = Instant.ofEpochMilli(now).atZone(zone)
        val n = when (repeat) {
            ReminderRepeat.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(start, end)
            ReminderRepeat.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(start, end)
            ReminderRepeat.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(start, end)
            ReminderRepeat.NONE -> 0
        }
        return (n - 1).coerceAtLeast(1)
    }

    /** Always counted from the anchor (not from the previous step) so Jan 31 → Feb 28 → Mar 31. */
    private fun step(start: ZonedDateTime, repeat: ReminderRepeat, n: Long): ZonedDateTime = when (repeat) {
        ReminderRepeat.DAILY -> start.plusDays(n)
        ReminderRepeat.WEEKLY -> start.plusWeeks(n)
        ReminderRepeat.MONTHLY -> start.plusMonths(n)
        ReminderRepeat.NONE -> start
    }

    /** "Today 9:00 AM", "Tomorrow 7:30 PM", "Mon 3 Mar, 9:00 AM", with the repeat appended. */
    fun label(at: Long, repeat: ReminderRepeat, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = DateTimeFormatter.ofPattern("h:mm a", locale).format(t)
        val day = when (t.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> DateTimeFormatter.ofPattern(if (t.year == today.year) "EEE d MMM" else "d MMM yyyy", locale).format(t)
        }
        val base = "$day $time"
        return if (repeat == ReminderRepeat.NONE) base else "$base · ${repeat.label.lowercase(locale)}"
    }

    /** Combines a picked date and time into epoch millis. */
    fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}
