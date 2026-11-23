package com.nexusnotes.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexusnotes.data.dao.FolderDao
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.dao.TagDao
import com.nexusnotes.data.entity.FolderEntity
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.NoteFtsEntity
import com.nexusnotes.data.entity.NoteTagCrossRef
import com.nexusnotes.data.entity.TagEntity

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
    ],
    version = 2, // keep equal to VERSION below
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "nexus.db"
        const val VERSION = 2

        fun build(context: Context): NexusDatabase {
            DatabaseSafety.backupBeforeUpgrade(context.applicationContext, VERSION)
            return Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}
