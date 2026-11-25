package com.nexusnotes.domain.ai

import com.nexusnotes.domain.backup.AutoBackupPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AiPromptsTest {
    @Test fun includesNoteAndTask() {
        val p = AiPrompts.build(AiAction.SUMMARIZE, "বাজার", "চাল ২ কেজি")
        assertTrue(p.startsWith("Summarize"))
        assertTrue(p.contains("Title: বাজার\n\nচাল ২ কেজি"))
    }

    @Test fun translateUsesLanguage() {
        assertTrue(AiPrompts.build(AiAction.TRANSLATE, "", "hi", "Bangla").contains("into Bangla"))
        assertTrue(AiPrompts.build(AiAction.TRANSLATE, "", "hi", " ").contains("into English"))
    }

    @Test fun longNotesAreCutAndSaySo() {
        val p = AiPrompts.build(AiAction.REWRITE, "", "x".repeat(AiPrompts.MAX_NOTE_CHARS + 50))
        assertTrue(p.contains("Only the first"))
        assertEquals(AiPrompts.MAX_NOTE_CHARS, p.count { it == 'x' })
    }

    @Test fun everyActionBuilds() {
        AiAction.entries.forEach { assertTrue(AiPrompts.build(it, "t", "b").contains("---\nTitle: t")) }
    }

    @Test fun autoBackupNamesAndRetention() {
        val zone = ZoneId.of("Asia/Dhaka")
        val name = AutoBackupPolicy.fileName(ZonedDateTime.of(2026, 11, 25, 3, 7, 0, 0, zone).toInstant().toEpochMilli(), zone)
        assertEquals("NexusNotes-auto-20261125-0307.zip", name)
        assertTrue(AutoBackupPolicy.isAutoBackup("NexusNotes-auto-20261125-0307 (1).zip"))
        assertFalse(AutoBackupPolicy.isAutoBackup("NexusNotes-backup-20261125-0307.zip"))
        val names = listOf(
            "NexusNotes-auto-20261120-0300.zip", "NexusNotes-auto-20261125-0300.zip", "my.zip",
            "NexusNotes-auto-20261122-0300.zip", "NexusNotes-backup-20260101-0000.zip", "NexusNotes-auto-20261101-0300.zip",
        )
        assertEquals(listOf("NexusNotes-auto-20261120-0300.zip", "NexusNotes-auto-20261101-0300.zip"), AutoBackupPolicy.toDelete(names, 2))
        assertTrue(AutoBackupPolicy.toDelete(names, 10).isEmpty())
    }
}
