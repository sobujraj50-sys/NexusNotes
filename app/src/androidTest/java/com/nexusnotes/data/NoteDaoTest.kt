package com.nexusnotes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusnotes.core.database.NexusDatabase
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.domain.model.SaveOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against a real in-memory SQLite database on the device. */
@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var db: NexusDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        dao = db.noteDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun emptyNewNoteIsNotStored() = runBlocking {
        assertEquals(SaveOutcome.SKIPPED_EMPTY, dao.saveContent("n1", " ", "", now = 1))
        assertNull(dao.getById("n1"))
    }

    @Test
    fun firstSaveCreatesThenUpdates() = runBlocking {
        assertEquals(SaveOutcome.CREATED, dao.saveContent("n1", "T", "B", now = 10))
        assertEquals(SaveOutcome.UNCHANGED, dao.saveContent("n1", "T", "B", now = 20))
        assertEquals(SaveOutcome.UPDATED, dao.saveContent("n1", "T2", "B", now = 30))
        val note = dao.getById("n1")!!
        assertEquals("T2", note.title)
        assertEquals(10, note.createdAt)
        assertEquals(30, note.updatedAt)
    }

    @Test
    fun trashIsSoftDeleteAndReadOnly() = runBlocking {
        dao.saveContent("n1", "T", "B", now = 1)
        dao.setPinned("n1", true)
        dao.moveToTrash("n1", deletedAt = 5)
        val trashed = dao.getById("n1")!!
        assertEquals(5L, trashed.deletedAt)
        assertEquals(false, trashed.isPinned)
        assertTrue(dao.observeActive("MODIFIED").first().isEmpty())
        assertEquals(1, dao.observeTrash().first().size)
        assertEquals(SaveOutcome.READ_ONLY, dao.saveContent("n1", "changed", "B", now = 6))
        dao.restore("n1")
        assertNull(dao.getById("n1")!!.deletedAt)
    }

    @Test
    fun permanentDeleteOnlyWorksFromTrash() = runBlocking {
        dao.saveContent("n1", "T", "B", now = 1)
        assertEquals(0, dao.deletePermanently("n1"))
        assertNotNull(dao.getById("n1"))
        dao.moveToTrash("n1", deletedAt = 2)
        assertEquals(1, dao.deletePermanently("n1"))
        assertNull(dao.getById("n1"))
    }

    @Test
    fun pinnedFirstThenSort() = runBlocking {
        dao.saveContent("a", "Banana", "", now = 1)
        dao.saveContent("b", "apple", "", now = 2)
        dao.saveContent("c", "Cherry", "", now = 3)
        dao.setPinned("a", true)
        assertEquals(listOf("a", "c", "b"), dao.observeActive("MODIFIED").first().map { it.id })
        assertEquals(listOf("a", "b", "c"), dao.observeActive("TITLE").first().map { it.id })
        assertEquals(listOf("a", "c", "b"), dao.observeActive("CREATED").first().map { it.id })
    }

    @Test
    fun archiveHidesFromActive() = runBlocking {
        dao.saveContent("a", "A", "", now = 1)
        dao.setArchived("a", true)
        assertTrue(dao.observeActive("MODIFIED").first().isEmpty())
        assertEquals(1, dao.observeArchived().first().size)
    }

    @Test
    fun duplicateCopiesContentNotPin() = runBlocking {
        dao.saveContent("a", "Plan", "Body", now = 1)
        dao.setPinned("a", true)
        dao.setColor("a", 2)
        assertTrue(dao.duplicate("a", "b", now = 9))
        val copy = dao.getById("b")!!
        assertEquals("Plan (copy)", copy.title)
        assertEquals("Body", copy.body)
        assertEquals(2, copy.colorIndex)
        assertEquals(false, copy.isPinned)
        assertEquals(9, copy.createdAt)
    }

    @Test
    fun flagsDoNotChangeModifiedTime() = runBlocking {
        dao.saveContent("a", "A", "", now = 1)
        dao.setFavorite("a", true)
        dao.setColor("a", 1)
        assertEquals(1, dao.getById("a")!!.updatedAt)
    }

    @Test
    fun deletingFolderKeepsNotes() = runBlocking {
        val folders = db.folderDao()
        folders.insert(com.nexusnotes.data.entity.FolderEntity("f1", "Work", null, null, 1))
        folders.insert(com.nexusnotes.data.entity.FolderEntity("f2", "Sub", "f1", null, 1))
        dao.saveContent("n1", "A", "", now = 1, folderIdForNew = "f2")
        folders.deleteFoldersKeepNotes(listOf("f1", "f2"))
        assertNull(dao.getById("n1")!!.folderId)
        assertTrue(folders.getAll().isEmpty())
    }

    @Test
    fun tagsAreCaseInsensitiveAndRemovedWithNote() = runBlocking {
        val tags = db.tagDao()
        dao.saveContent("n1", "A", "", now = 1)
        tags.addTagToNote("n1", "Work", "t1")
        tags.addTagToNote("n1", "work", "t2")
        assertEquals(1, tags.observeForNote("n1").first().size)
        dao.moveToTrash("n1", 2)
        dao.deletePermanently("n1")
        assertEquals(0, tags.tagIdsForNote("n1").size)
    }
}
