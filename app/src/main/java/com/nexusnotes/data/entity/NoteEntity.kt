package com.nexusnotes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexusnotes.domain.model.Note

/**
 * Database row for a note (schema version 1).
 *
 * - [id] is a random UUID string, stable across backup/restore.
 * - [body] is Markdown; [bodyFormatVersion] lets later milestones change the format safely.
 * - Trash is a soft delete via [deletedAt]. Flags (pin, favorite, color, archive) do not
 *   change [updatedAt]; only text edits do.
 * - [folderId] (added in schema v2) points to a folder, or null for "Unfiled". It has no SQL
 *   foreign key (adding one needs a full table rebuild); the DAOs keep it consistent instead.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["deletedAt", "isArchived"]),
        Index(value = ["isPinned"]),
        Index(value = ["folderId"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(defaultValue = "1") val bodyFormatVersion: Int = BODY_FORMAT_MARKDOWN_V1,
    val colorIndex: Int?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
) {
    companion object {
        const val BODY_FORMAT_MARKDOWN_V1 = 1

        fun new(id: String, title: String, body: String, now: Long, folderId: String? = null) = NoteEntity(
            id = id,
            title = title,
            body = body,
            colorIndex = null,
            isPinned = false,
            isFavorite = false,
            isArchived = false,
            deletedAt = null,
            createdAt = now,
            updatedAt = now,
            folderId = folderId,
        )
    }
}

fun NoteEntity.toDomain() = Note(
    id = id,
    title = title,
    body = body,
    colorIndex = colorIndex,
    isPinned = isPinned,
    isFavorite = isFavorite,
    isArchived = isArchived,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    folderId = folderId,
)
