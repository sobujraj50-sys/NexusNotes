package com.nexusnotes.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePlannerTest {
    private var counter = 0
    private val planner = RestorePlanner { "new${++counter}" }

    private fun note(id: String, title: String = "T$id", updated: Long = 1, folder: String? = null) =
        NoteRec(id, title, "body $id", 1, null, false, false, false, null, 1, updated, folder)

    private val backup = BackupData(
        notes = listOf(note("a"), note("b", "Changed", 5), note("c", folder = "f9")),
        folders = listOf(FolderRec("f1", "Work", null, null, 1), FolderRec("f2", "Sub", "f1", null, 1), FolderRec("f3", "Orphan", "gone", null, 1)),
        tags = listOf(TagRec("t1", "food"), TagRec("t2", "work")),
        noteTags = listOf(NoteTagRec("a", "t1"), NoteTagRec("b", "t2"), NoteTagRec("x", "t1")),
        attachments = listOf(
            AttachmentRec("at1", "a", "IMAGE", "at1.jpg", "p.jpg", "image/jpeg", 1, "h", null, 1),
            AttachmentRec("at2", "b", "FILE", "at2.pdf", "d.pdf", "application/pdf", 1, "h", null, 1),
        ),
        prompts = listOf(PromptRec("p1", "P", "t", "writing", false, 1, 1)),
        variables = listOf(VariableRec("p1", "x", "")),
        versions = listOf(VersionRec("v1", "p1", "P", "t", 1)),
    )

    @Test fun mergeIntoEmptyAddsEverything() {
        val plan = planner.plan(backup, ExistingData(), RestoreMode.MERGE)
        assertEquals(3, plan.stats.notesAdded)
        assertEquals(listOf("a", "b", "c"), plan.insert.notes.map { it.id })
        assertEquals(2, plan.files.size)
        // Unknown folder reference dropped, orphan folder moved to top level.
        assertNull(plan.insert.notes.single { it.id == "c" }.folderId)
        assertNull(plan.insert.folders.single { it.id == "f3" }.parentId)
        assertEquals("f1", plan.insert.folders.single { it.id == "f2" }.parentId)
        // Link to a note that isn't in the backup is dropped.
        assertEquals(2, plan.insert.noteTags.size)
    }

    @Test fun mergeSkipsDuplicatesAndCopiesConflicts() {
        val existing = ExistingData(
            notes = mapOf("a" to note("a"), "b" to note("b", "Mine", 9)),
            tags = listOf(TagRec("t9", "food")),
            attachmentIds = setOf("at1"),
            attachmentFileNames = setOf("at1.jpg"),
            prompts = mapOf("p1" to PromptRec("p1", "P", "t", "writing", false, 1, 1)),
        )
        val plan = planner.plan(backup, existing, RestoreMode.MERGE)
        assertEquals(1, plan.stats.notesSkipped) // "a" identical
        assertEquals(1, plan.stats.notesCopied) // "b" differs
        assertEquals(1, plan.stats.notesAdded) // "c"
        val copy = plan.insert.notes.single { it.title == "Changed (restored)" }
        assertTrue(copy.id.startsWith("new"))
        // Nothing existing is re-inserted (no overwrite).
        assertTrue(plan.insert.notes.none { it.id == "a" || it.id == "b" })
        // Tag "food" reuses the existing tag; "work" is added.
        assertEquals(listOf("work"), plan.insert.tags.map { it.name })
        // Copy keeps its tag via the remapped id; skipped note's attachment is not restored.
        assertEquals(listOf(NoteTagRec(copy.id, "t2")), plan.insert.noteTags)
        assertEquals(1, plan.insert.attachments.size)
        assertEquals(copy.id, plan.insert.attachments.single().noteId)
        // Identical prompt skipped with its variables and versions.
        assertEquals(1, plan.stats.promptsSkipped)
        assertTrue(plan.insert.prompts.isEmpty() && plan.insert.variables.isEmpty() && plan.insert.versions.isEmpty())
    }

    @Test fun attachmentNameClashGetsNewName() {
        val existing = ExistingData(attachmentFileNames = setOf("at2.pdf"))
        val plan = planner.plan(backup, existing, RestoreMode.MERGE)
        val a = plan.insert.attachments.single { it.displayName == "d.pdf" }
        assertEquals("at2.pdf", plan.files.single { it.targetName == a.fileName }.archivePath.removePrefix("attachments/"))
        assertTrue(a.fileName != "at2.pdf")
    }

    @Test fun conflictingPromptCopiedWithChildren() {
        val existing = ExistingData(prompts = mapOf("p1" to PromptRec("p1", "Other", "x", "writing", false, 1, 7)), versionIds = setOf("v1"))
        val plan = planner.plan(backup, existing, RestoreMode.MERGE)
        val p = plan.insert.prompts.single()
        assertEquals("P (restored)", p.title)
        assertEquals(p.id, plan.insert.variables.single().promptId)
        assertEquals(p.id, plan.insert.versions.single().promptId)
        assertTrue(plan.insert.versions.single().id != "v1")
    }

    @Test fun replaceIgnoresExisting() {
        val existing = ExistingData(notes = mapOf("a" to note("a")), tags = listOf(TagRec("t9", "food")))
        val plan = planner.plan(backup, existing, RestoreMode.REPLACE)
        assertEquals(3, plan.insert.notes.size)
        assertEquals(listOf("t1", "t2"), plan.insert.tags.map { it.id })
    }

    @Test fun folderCycleBroken() {
        val b = BackupData(folders = listOf(FolderRec("x", "X", "y", null, 1), FolderRec("y", "Y", "x", null, 1)))
        val plan = planner.plan(b, ExistingData(), RestoreMode.MERGE)
        assertTrue(plan.insert.folders.all { it.parentId == null })
    }
}
