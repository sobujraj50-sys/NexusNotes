package com.nexusnotes.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.entity.NoteEntity

/**
 * The app database. Schema JSON files are exported to app/schemas/.
 *
 * Rule: no destructive migration fallback. Every version bump must ship an explicit
 * Migration plus a migration test, so user notes are never wiped by an update.
 */
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "nexus.db"

        fun build(context: Context): NexusDatabase =
            Room.databaseBuilder(context.applicationContext, NexusDatabase::class.java, NAME)
                .build()
    }
}
