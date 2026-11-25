package com.nexusnotes.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One line per backup / restore / export, shown on the Backup screen. No note content. */
@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    /** BACKUP, RESTORE, EXPORT, VERIFY */
    val kind: String,
    val fileName: String,
    val sizeBytes: Long,
    val summary: String,
    val success: Boolean,
)
