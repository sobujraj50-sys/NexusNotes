package com.nexusnotes.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexusnotes.data.dao.AttachmentDao
import com.nexusnotes.data.dao.FolderDao
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.dao.PromptDao
import com.nexusnotes.data.dao.TagDao
import com.nexusnotes.data.dao.VaultDao
import com.nexusnotes.data.entity.AttachmentEntity
import com.nexusnotes.data.entity.FolderEntity
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.NoteFtsEntity
import com.nexusnotes.data.entity.NoteTagCrossRef
import com.nexusnotes.data.entity.PromptCategoryEntity
import com.nexusnotes.data.entity.PromptEntity
import com.nexusnotes.data.entity.PromptFtsEntity
import com.nexusnotes.data.entity.PromptVariableEntity
import com.nexusnotes.data.entity.PromptVersionEntity
import com.nexusnotes.data.entity.TagEntity
import com.nexusnotes.data.entity.VaultAttachmentEntity
import com.nexusnotes.data.entity.VaultItemEntity

/** Deleted rows are overwritten with zeros (e.g. a note moved into the vault leaves no plaintext in free pages). */
private object SecureDeleteCallback : RoomDatabase.Callback() {
    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("PRAGMA secure_delete = ON").close()
    }
}

/**
 * The app database. Schema JSON files are exported to app/schemas/.
 *
 * Rule: no destructive migration fallback. Every version bump must ship an explicit
 * Migration plus a migration test, so user notes are never wiped by an update.
 */
@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
        PromptEntity::class,
        PromptCategoryEntity::class,
        PromptVariableEntity::class,
        PromptVersionEntity::class,
        PromptFtsEntity::class,
        AttachmentEntity::class,
        VaultItemEntity::class,
        VaultAttachmentEntity::class,
    ],
    version = 5, // keep equal to VERSION below
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun promptDao(): PromptDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun vaultDao(): VaultDao

    companion object {
        const val NAME = "nexus.db"
        const val VERSION = 5

        fun build(context: Context): NexusDatabase {
            DatabaseSafety.backupBeforeUpgrade(context.applicationContext, VERSION)
            return Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(SecureDeleteCallback)
                .build()
        }
    }
}
