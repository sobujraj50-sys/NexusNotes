package com.nexusnotes.domain.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class NoteTemplatesTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = ZonedDateTime.of(2026, 11, 25, 21, 40, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun fillsPlaceholders() {
        assertEquals(
            "Wednesday 2026-11-25 21:40 / 2026-11-25 21:40",
            NoteTemplates.fill("{{weekday}} {{date}} {{time}} / {{ datetime }}", now, zone, Locale.US),
        )
    }

    @Test fun leavesOtherBracesAlone() {
        assertEquals("{{name}} and {x}", NoteTemplates.fill("{{name}} and {x}", now, zone, Locale.US))
    }

    @Test fun builtInsAreValid() {
        assertTrue(NoteTemplates.builtIns.size >= 5)
        assertEquals(NoteTemplates.builtIns.size, NoteTemplates.builtIns.map { it.id }.toSet().size)
        assertTrue(NoteTemplates.builtIns.single { it.name == "Checklist" }.body.startsWith("- [ ] "))
    }
}
