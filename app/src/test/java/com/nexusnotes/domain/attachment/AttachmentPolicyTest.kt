package com.nexusnotes.domain.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPolicyTest {

    @Test
    fun kindFromMime() {
        assertEquals(AttachmentKind.IMAGE, AttachmentPolicy.kindFor("image/jpeg"))
        assertEquals(AttachmentKind.AUDIO, AttachmentPolicy.kindFor("audio/mp4"))
        assertEquals(AttachmentKind.FILE, AttachmentPolicy.kindFor("application/pdf"))
        assertEquals(AttachmentKind.FILE, AttachmentPolicy.kindFor(null))
    }

    @Test
    fun displayNameStripsPathsAndControlChars() {
        assertEquals("report.pdf", AttachmentPolicy.safeDisplayName("../../etc/report.pdf"))
        assertEquals("a_b.txt", AttachmentPolicy.safeDisplayName("a:b.txt"))
        assertEquals("evil.txt", AttachmentPolicy.safeDisplayName("ev\u0000il.txt"))
        assertEquals("hidden", AttachmentPolicy.safeDisplayName(".hidden"))
        assertEquals("attachment", AttachmentPolicy.safeDisplayName("   "))
        assertEquals("attachment", AttachmentPolicy.safeDisplayName(null))
        assertEquals("ছবি.jpg", AttachmentPolicy.safeDisplayName("ছবি.jpg"))
    }

    @Test
    fun longNamesKeepExtension() {
        val name = AttachmentPolicy.safeDisplayName("x".repeat(300) + ".pdf")
        assertTrue(name.length <= 120)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun sampleSize() {
        assertEquals(1, AttachmentPolicy.inSampleSize(300, 200, 360))
        assertEquals(8, AttachmentPolicy.inSampleSize(4000, 3000, 360))
        assertEquals(1, AttachmentPolicy.inSampleSize(4000, 3000, 4000))
        assertEquals(2, AttachmentPolicy.inSampleSize(4096, 100, 2048))
        assertEquals(1, AttachmentPolicy.inSampleSize(0, 0, 360))
    }

    @Test
    fun formatting() {
        assertEquals("512 B", AttachmentPolicy.formatSize(512))
        assertEquals("1.5 KB", AttachmentPolicy.formatSize(1536))
        assertEquals("50.0 MB", AttachmentPolicy.formatSize(AttachmentPolicy.MAX_FILE_BYTES))
        assertEquals("0:07", AttachmentPolicy.formatDuration(7_400))
        assertEquals("12:34", AttachmentPolicy.formatDuration((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", AttachmentPolicy.formatDuration((3600 + 123) * 1000L))
    }

    @Test
    fun sharedNoteTitle() {
        assertEquals("Subject", AttachmentPolicy.sharedNoteTitle("Subject", "text", null))
        assertEquals("first line", AttachmentPolicy.sharedNoteTitle(" ", "\n first line \nsecond", null))
        assertEquals("photo.jpg", AttachmentPolicy.sharedNoteTitle(null, null, "photo.jpg"))
        assertEquals("Shared note", AttachmentPolicy.sharedNoteTitle(null, "", null))
    }
}
