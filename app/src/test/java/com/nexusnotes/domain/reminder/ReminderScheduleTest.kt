package com.nexusnotes.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ReminderScheduleTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private val ny = ZoneId.of("America/New_York")
    private fun t(y: Int, m: Int, d: Int, h: Int, min: Int, z: ZoneId = dhaka) = ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant().toEpochMilli()

    @Test fun futureOneTimeIsItself() {
        val at = t(2026, 11, 26, 9, 0)
        assertEquals(at, ReminderSchedule.nextAfter(at, ReminderRepeat.NONE, t(2026, 11, 25, 22, 0), dhaka))
    }

    @Test fun pastOneTimeIsNull() {
        assertNull(ReminderSchedule.nextAfter(t(2026, 11, 25, 9, 0), ReminderRepeat.NONE, t(2026, 11, 25, 22, 0), dhaka))
    }

    @Test fun dailyGoesToNextDaySameTime() {
        val next = ReminderSchedule.nextAfter(t(2026, 11, 1, 9, 0), ReminderRepeat.DAILY, t(2026, 11, 25, 22, 0), dhaka)
        assertEquals(t(2026, 11, 26, 9, 0), next)
        // Exactly at fire time → the following day.
        assertEquals(t(2026, 11, 27, 9, 0), ReminderSchedule.nextAfter(t(2026, 11, 1, 9, 0), ReminderRepeat.DAILY, t(2026, 11, 26, 9, 0), dhaka))
    }

    @Test fun weekly() {
        // Monday 2026-11-23 → next Monday 2026-11-30
        assertEquals(t(2026, 11, 30, 8, 30), ReminderSchedule.nextAfter(t(2026, 11, 23, 8, 30), ReminderRepeat.WEEKLY, t(2026, 11, 25, 0, 0), dhaka))
    }

    @Test fun monthlyKeepsEndOfMonth() {
        val anchor = t(2026, 1, 31, 10, 0)
        assertEquals(t(2026, 2, 28, 10, 0), ReminderSchedule.nextAfter(anchor, ReminderRepeat.MONTHLY, t(2026, 2, 1, 0, 0), dhaka))
        assertEquals(t(2026, 3, 31, 10, 0), ReminderSchedule.nextAfter(anchor, ReminderRepeat.MONTHLY, t(2026, 3, 1, 0, 0), dhaka))
    }

    @Test fun keepsWallClockAcrossDst() {
        // New York: DST ends 2026-11-01. A 9:00 daily reminder stays 9:00 local.
        val next = ReminderSchedule.nextAfter(t(2026, 10, 30, 9, 0, ny), ReminderRepeat.DAILY, t(2026, 11, 1, 12, 0, ny), ny)
        assertEquals(t(2026, 11, 2, 9, 0, ny), next)
    }

    @Test fun farPastDailyIsFast() {
        val next = ReminderSchedule.nextAfter(t(2000, 1, 1, 7, 0), ReminderRepeat.DAILY, t(2026, 11, 25, 22, 0), dhaka)
        assertEquals(t(2026, 11, 26, 7, 0), next)
    }

    @Test fun labels() {
        val now = t(2026, 11, 25, 22, 0)
        assertEquals("Today 11:30 PM", ReminderSchedule.label(t(2026, 11, 25, 23, 30), ReminderRepeat.NONE, now, dhaka, Locale.US))
        assertEquals("Tomorrow 9:00 AM · every day", ReminderSchedule.label(t(2026, 11, 26, 9, 0), ReminderRepeat.DAILY, now, dhaka, Locale.US))
        assertEquals("Thu 3 Dec 9:00 AM", ReminderSchedule.label(t(2026, 12, 3, 9, 0), ReminderRepeat.NONE, now, dhaka, Locale.US))
        assertEquals(t(2026, 12, 3, 9, 0), ReminderSchedule.at(LocalDate.of(2026, 12, 3), 9, 0, dhaka))
    }
}
