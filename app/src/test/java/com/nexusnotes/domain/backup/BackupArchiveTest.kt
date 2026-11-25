package com.nexusnotes.domain.backup

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    private val staging: File = Files.createTempDirectory("staging").toFile()

    @After fun cleanup() { staging.deleteRecursively() }

    private val note = NoteRec("n1", "বাজার", "চাল\n\"quoted\" \\ text 😀", 1, 3, true, false, false, null, 10, 20, "f1")
    private val data = BackupData(
        notes = listOf(note, note.copy(id = "n2", title = "Trash", deletedAt = 30, folderId = null, colorIndex = null)),
        folders = listOf(FolderRec("f1", "Home", null, null, 1)),
        tags = listOf(TagRec("t1", "food")),
        noteTags = listOf(NoteTagRec("n1", "t1")),
        prompts = listOf(PromptRec("p1", "Summarise", "Summarise {{text}}", "writing", true, 1, 2)),
        categories = listOf(CategoryRec("c1", "Mine", 1)),
        variables = listOf(VariableRec("p1", "text", "")),
        versions = listOf(VersionRec("v1", "p1", "Summarise", "Summarise {{text}}", 1)),
        attachments = listOf(AttachmentRec("a1", "n1", "IMAGE", "a1.jpg", "photo.jpg", "image/jpeg", 4, "x", null, 5)),
    )
    private val photo = byteArrayOf(1, 2, 3, 4)

    private fun archive(d: BackupData = data, files: Map<String, ByteArray> = mapOf("attachments/a1.jpg" to photo)): ByteArray {
        val out = ByteArrayOutputStream()
        val w = BackupWriter(out)
        w.writeData(d)
        files.forEach { (p, b) -> w.writeFile(p, b.inputStream()) }
        w.finish("0.8.0", 6, 99, d)
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = BackupReader.read(ByteArrayInputStream(bytes), staging)

    @Test fun roundTripKeepsEverything() {
        val staged = read(archive())
        assertEquals(data, staged.data)
        assertEquals(99, staged.manifest.createdAt)
        assertEquals(2L, staged.manifest.counts["notes"])
        assertArrayEquals(photo, staged.file("attachments/a1.jpg")!!.readBytes())
        assertNull(staged.file("attachments/none.jpg"))
    }

    @Test fun emptyBackupIsValid() {
        val staged = read(archive(BackupData(), emptyMap()))
        assertTrue(staged.data.notes.isEmpty())
    }

    /** Rewrites one entry's bytes, keeping the original manifest. */
    private fun tamper(bytes: ByteArray, path: String, change: (ByteArray) -> ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zo ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zi ->
                while (true) {
                    val e = zi.nextEntry ?: break
                    val content = zi.readBytes()
                    val newContent = if (e.name == path) change(content) else content
                    if (newContent != null) {
                        zo.putNextEntry(ZipEntry(e.name))
                        zo.write(newContent)
                        zo.closeEntry()
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun assertRejected(bytes: ByteArray, containing: String) {
        val e = assertThrows(BackupInvalidException::class.java) { read(bytes) }
        assertTrue("got: ${e.message}", e.message!!.contains(containing, ignoreCase = true))
    }

    @Test fun changedAttachmentRejected() = assertRejected(tamper(archive(), "attachments/a1.jpg") { byteArrayOf(9, 9, 9, 9) }, "integrity")

    @Test fun changedDataRejected() = assertRejected(
        tamper(archive(), "data.json") { String(it).replace("বাজার", "hacked").toByteArray() },
        "integrity",
    )

    @Test fun missingFileRejected() = assertRejected(tamper(archive(), "attachments/a1.jpg") { null }, "missing")

    @Test fun missingManifestRejected() = assertRejected(tamper(archive(), "manifest.json") { null }, "manifest")

    @Test fun truncatedFileRejected() {
        val full = archive()
        assertThrows(BackupInvalidException::class.java) { read(full.copyOf(full.size / 2)) }
    }

    @Test fun notAZipRejected() = assertRejected("hello world, definitely not a zip".toByteArray(), "")

    @Test fun otherZipRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("readme.txt")); it.write(1); it.closeEntry() }
        assertRejected(out.toByteArray(), "unexpected")
    }

    @Test fun pathTraversalRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("attachments/../../shared_prefs/x.xml")); it.write(1); it.closeEntry() }
        assertRejected(out.toByteArray(), "unexpected")
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test fun newerFormatRejected() {
        val bytes = tamper(archive(), "manifest.json") { String(it).replace("\"formatVersion\":1", "\"formatVersion\":99").toByteArray() }
        assertRejected(bytes, "newer version")
    }

    @Test fun sizeLimitEnforced() {
        val e = assertThrows(BackupInvalidException::class.java) {
            BackupReader.read(ByteArrayInputStream(archive()), staging, BackupLimits(maxTotalBytes = 10))
        }
        assertTrue(e.message!!.contains("too large"))
    }

    @Test fun attachmentWithoutNoteRejected() {
        val d = data.copy(attachments = data.attachments.map { it.copy(noteId = "ghost") })
        assertRejected(archive(d), "without its note")
    }

    @Test fun unsafeFileNameInDataRejected() {
        val d = data.copy(attachments = data.attachments.map { it.copy(fileName = "../evil") })
        // The writer refuses the path itself; build the archive with a safe path but unsafe record.
        assertRejected(archive(d, mapOf("attachments/a1.jpg" to photo)), "invalid file name")
    }

    @Test fun safeNames() {
        assertTrue(BackupPaths.isSafeFileName("2f1c-aa.jpg"))
        assertTrue(!BackupPaths.isSafeFileName("../x"))
        assertTrue(!BackupPaths.isSafeFileName(".hidden"))
        assertTrue(!BackupPaths.isSafeFileName("a/b"))
        assertTrue(!BackupPaths.isAllowedEntry("data.json/x"))
    }
}
