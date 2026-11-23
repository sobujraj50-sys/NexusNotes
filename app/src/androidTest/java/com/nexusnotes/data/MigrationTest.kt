package com.nexusnotes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexusnotes.core.database.Migrations
import com.nexusnotes.core.database.NexusDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a real schema-v1 database (exactly as Milestone 2/3 created it) to the current
 * version through Room. Room validates the whole migrated schema when it opens; any mismatch
 * fails this test.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createV1() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`bodyFormatVersion` INTEGER NOT NULL DEFAULT 1, `colorIndex` INTEGER, `isPinned` INTEGER NOT NULL, " +
                    "`isFavorite` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_deletedAt_isArchived` ON `notes` (`deletedAt`, `isArchived`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isPinned` ON `notes` (`isPinned`)")
            db.execSQL(
                "INSERT INTO notes VALUES ('a', 'Shopping list', '- [ ] milk\n- [x] bread', 1, 2, 1, 0, 0, NULL, 10, 20)",
            )
            db.execSQL("INSERT INTO notes VALUES ('b', 'বাংলা নোট', 'আজকের কাজের তালিকা', 1, NULL, 0, 1, 0, NULL, 30, 40)")
            db.execSQL("INSERT INTO notes VALUES ('c', 'Old', 'in trash', 1, NULL, 0, 0, 0, 99, 1, 2)")
            db.version = 1
        }
    }

    @Test
    fun upgradeKeepsEveryNoteAndEnablesSearch() = runBlocking {
        createV1()
        val db = Room.databaseBuilder(context, NexusDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val dao = db.noteDao()
            val a = dao.getById("a")!!
            assertEquals("Shopping list", a.title)
            assertEquals("- [ ] milk\n- [x] bread", a.body)
            assertEquals(2, a.colorIndex)
            assertEquals(true, a.isPinned)
            assertNull(a.folderId)
            assertEquals("বাংলা নোট", dao.getById("b")!!.title)
            assertEquals(99L, dao.getById("c")!!.deletedAt)

            // Existing notes were indexed during the upgrade.
            assertEquals(listOf("a"), dao.search("\"milk*\"", null, false, null, false, 50).first().map { it.id })
            assertEquals(listOf("b"), dao.search("\"আজকের*\"", null, false, null, false, 50).first().map { it.id })
            // Trash is never searched.
            assertEquals(0, dao.search("\"trash*\"", null, false, null, false, 50).first().size)

            // Prompt tables exist after the full v1 -> v3 chain and are searchable.
            val prompts = db.promptDao()
            prompts.saveContent("p1", "Cinematic", "A {{subject}} at golden hour", "IMAGE_GENERATION", now = 1, newVersionId = "v1")
            assertEquals(listOf("p1"), prompts.search("\"golden*\"", 10).first().map { it.id })
            assertEquals(1, prompts.observeVersions("p1").first().size)

            // The index follows later edits (sync triggers exist).
            dao.updateContent("a", "Shopping list", "eggs", 50)
            assertEquals(0, dao.search("\"milk*\"", null, false, null, false, 50).first().size)
            assertEquals(listOf("a"), dao.search("\"eggs*\"", null, false, null, false, 50).first().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun freshInstallSearchWorks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        try {
            val dao = db.noteDao()
            dao.saveContent("x", "Meeting", "Discuss budget", now = 1)
            assertEquals(listOf("x"), dao.search("\"budg*\"", null, false, null, false, 50).first().map { it.id })
        } finally {
            db.close()
        }
    }
}
