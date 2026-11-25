package com.nexusnotes.domain.perf

import com.nexusnotes.domain.NoteText
import com.nexusnotes.domain.backup.BackupData
import com.nexusnotes.domain.backup.BackupJson
import com.nexusnotes.domain.backup.NoteRec
import com.nexusnotes.domain.markdown.MarkdownFormatter
import com.nexusnotes.domain.markdown.MarkdownParser
import com.nexusnotes.domain.vault.VaultCodec
import com.nexusnotes.domain.vault.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Large-note performance guards. Limits are generous (CI machines vary); they catch accidental
 * quadratic algorithms, not micro-regressions. A phone is slower than CI, so real limits are ~5–10× lower.
 */
class LargeNotePerformanceTest {
    /** ~0.6 MB note: headings, lists, tasks, bold/italic/code, Bangla text. */
    private val big: String = buildString {
        repeat(4_000) { i ->
            append("## Section $i\n")
            append("Some **bold** and *italic* text with `code` and a [link](https://example.com) — বাংলা লেখা $i.\n")
            append("- [ ] task $i\n- [x] done $i\n> quote $i\n\n")
        }
    }

    private inline fun timed(limitMs: Long, what: String, block: () -> Unit) {
        block() // warm-up (JIT)
        val start = System.nanoTime()
        block()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$what took ${ms}ms (limit ${limitMs}ms)", ms < limitMs)
    }

    @Test fun parseLargeNote() {
        assertTrue("size ${big.length}", big.length > 500_000)
        timed(3_000, "Markdown parse") { assertTrue(MarkdownParser.parse(big).size > 10_000) }
    }

    @Test fun wordCountAndPreview() {
        timed(1_000, "word count") { assertTrue(NoteText.wordCount(big) > 50_000) }
        timed(200, "preview") { NoteText.preview(big) }
    }

    @Test fun toggleTaskNearEnd() {
        val lines = big.lines()
        val last = lines.indexOfLast { it.startsWith("- [ ]") }
        timed(1_000, "toggle task") { assertTrue(MarkdownFormatter.toggleTask(big, last) != null) }
    }

    @Test fun encryptBigVaultNote() {
        val key = VaultCrypto.newKey()
        timed(2_000, "vault encrypt+decrypt") {
            val blob = VaultCrypto.seal(key, VaultCodec.encodeNote("t", big), "note:1")
            assertEquals(big, VaultCodec.decodeNote(VaultCrypto.open(key, blob, "note:1")).second)
        }
    }

    @Test fun backupJsonOfThousandsOfNotes() {
        val notes = (1..5_000).map { NoteRec("id$it", "Note $it", "Body $it ".repeat(20), 1, null, false, false, false, null, 1, 2, null) }
        timed(5_000, "backup JSON") {
            val json = BackupJson.encodeData(BackupData(notes = notes))
            assertEquals(5_000, BackupJson.decodeData(json).notes.size)
        }
    }
}
