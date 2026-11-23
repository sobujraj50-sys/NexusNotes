package com.nexusnotes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.Tag

/**
 * A folder (schema v2). [parentId] = null means top level.
 * Parent links have no SQL foreign key; FolderDao moves/deletes whole subtrees in transactions
 * and the domain layer blocks cycles before any move.
 */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentId"])],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val colorIndex: Int?,
    val createdAt: Long,
)

fun FolderEntity.toDomain() = Folder(id, name, parentId, colorIndex, createdAt)

/** A tag (schema v2). Names are unique ignoring case (enforced by TagDao). */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

fun TagEntity.toDomain() = Tag(id, name)

/** Many-to-many link between notes and tags (schema v2). */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)

/**
 * Full-text index over note title and body (schema v2). External-content FTS4 table:
 * Room keeps it in sync with `notes` through triggers. Vault notes are never stored in
 * `notes`, so they can never enter this index.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
)
