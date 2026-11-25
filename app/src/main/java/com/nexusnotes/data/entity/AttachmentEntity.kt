package com.nexusnotes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexusnotes.domain.attachment.AttachmentKind
import com.nexusnotes.domain.model.Attachment

/**
 * A note attachment (schema v4). The file itself is stored in app-private storage
 * (filesDir/attachments/[fileName]); only metadata lives in the database.
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["noteId"])],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    /** AttachmentKind name: IMAGE, AUDIO or FILE. */
    val kind: String,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** SHA-256 of the stored bytes (hex), used to verify files in backups. */
    val sha256: String,
    val durationMs: Long?,
    val createdAt: Long,
)

fun AttachmentEntity.toDomain() = Attachment(
    id = id,
    noteId = noteId,
    kind = AttachmentKind.entries.firstOrNull { it.name == kind } ?: AttachmentKind.FILE,
    fileName = fileName,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    createdAt = createdAt,
)
