package com.nexusnotes.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Safety net for schema upgrades: before Room opens (and migrates) the database for the first
 * time with a newer schema, the current files are copied to `nexus-backup-v<old>.db`.
 * If a migration ever failed, the user's notes would still exist in that copy.
 */
object DatabaseSafety {

    fun backupBeforeUpgrade(context: Context, targetVersion: Int) {
        val dbFile = context.getDatabasePath(NexusDatabase.NAME)
        if (!dbFile.exists()) return // fresh install: nothing to protect
        val currentVersion = readVersion(dbFile) ?: return
        if (currentVersion >= targetVersion) return

        val backup = File(dbFile.parentFile, "nexus-backup-v$currentVersion.db")
        if (backup.exists()) return // keep the first, oldest copy
        val temp = File(dbFile.parentFile, backup.name + ".tmp")
        dbFile.copyTo(temp, overwrite = true)
        // Include write-ahead-log files so recently committed notes are not lost.
        listOf("-wal", "-shm").forEach { suffix ->
            val part = File(dbFile.path + suffix)
            if (part.exists()) part.copyTo(File(temp.path + suffix), overwrite = true)
        }
        // Rename last so a half-written copy is never mistaken for a good one.
        listOf("-wal", "-shm").forEach { suffix ->
            val part = File(temp.path + suffix)
            if (part.exists()) part.renameTo(File(backup.path + suffix))
        }
        temp.renameTo(backup)
    }

    private fun readVersion(dbFile: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }.getOrNull()
}
