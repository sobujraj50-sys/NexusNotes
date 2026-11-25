package com.nexusnotes.domain.backup

import com.nexusnotes.domain.prompt.MiniJson
import com.nexusnotes.domain.vault.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ExportAndKeyTest {
    private val n = ExportNote("1", "Trip \"Cox's\" Bazar", "- [ ] tickets\n- hotel", 0, 86_400_000, listOf("travel", "family"), "Plans/2026", false, listOf("ticket.pdf"))

    @Test fun markdownHasFrontMatterAndBody() {
        val md = NoteExport.toMarkdown(n, ZoneOffset.UTC)
        assertTrue(md.startsWith("---\ntitle: \"Trip \\\"Cox's\\\" Bazar\"\n"))
        assertTrue(md.contains("updated: 1970-01-02T00:00:00Z"))
        assertTrue(md.contains("tags: [\"travel\", \"family\"]"))
        assertTrue(md.contains("folder: \"Plans/2026\""))
        assertTrue(md.contains("# Trip \"Cox's\" Bazar\n\n- [ ] tickets\n- hotel\n"))
        assertTrue(md.contains("- ticket.pdf"))
    }

    @Test fun textExport() {
        val t = NoteExport.toText(n.copy(title = ""), ZoneOffset.UTC)
        assertTrue(t.startsWith("Untitled\n========\n"))
        assertTrue(t.endsWith("- hotel\n"))
    }

    @Test fun jsonExportParses() {
        @Suppress("UNCHECKED_CAST")
        val root = MiniJson.parse(NoteExport.toJson(listOf(n), 0, ZoneOffset.UTC)) as Map<String, Any?>
        val notes = root["notes"] as List<*>
        assertEquals("Trip \"Cox's\" Bazar", (notes[0] as Map<*, *>)["title"])
    }

    @Test fun fileNamesAreSafeAndUnique() {
        val used = mutableSetOf<String>()
        assertEquals("a_b_c.md", NoteExport.fileName("a/b:c", "md", used))
        assertEquals("a_b_c (2).md", NoteExport.fileName("a/b:c", "md", used))
        assertEquals("A_B_C (3).md", NoteExport.fileName("A/B:C", "md", used))
        assertEquals("Untitled.md", NoteExport.fileName("  ", "md", used))
        assertEquals("বাজার.txt", NoteExport.fileName("বাজার", "txt", used))
    }

    @Test fun vaultKeyWrapRoundTripAndWrongPassword() {
        val key = VaultCrypto.newKey()
        val rec = VaultBackupKey.wrap(key, "correct horse 9".toCharArray(), iterations = 20_000)
        assertArrayEquals(key, VaultBackupKey.unwrap(rec, "correct horse 9".toCharArray()))
        assertNull(VaultBackupKey.unwrap(rec, "wrong horse 99".toCharArray()))
        assertNull(VaultBackupKey.unwrap(rec.copy(iterations = 1), "correct horse 9".toCharArray()))
        assertNull(VaultBackupKey.unwrap(rec.copy(wrapped = "!!"), "correct horse 9".toCharArray()))
    }

    @Test fun backupPasswordRules() {
        assertNotNull(VaultBackupKey.passwordProblem("short".toCharArray()))
        assertNotNull(VaultBackupKey.passwordProblem("1234567890".toCharArray()))
        assertNull(VaultBackupKey.passwordProblem("my vault 2026".toCharArray()))
    }
}
