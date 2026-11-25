package com.nexusnotes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One reminder per note; removed automatically when the note is deleted forever. */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("triggerAt")],
)
data class ReminderEntity(
    @PrimaryKey val noteId: String,
    val triggerAt: Long,
    /** ReminderRepeat name */
    val repeat: String,
    val anchorAt: Long,
    val createdAt: Long,
)

/** Reminder plus what the list/notification needs from its note. */
data class ReminderWithNote(
    val noteId: String,
    val triggerAt: Long,
    val repeat: String,
    val anchorAt: Long,
    val title: String,
    val deletedAt: Long?,
)

/** A user-made note template. Built-in templates live in code. */
@Entity(tableName = "note_templates")
data class NoteTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val body: String,
    val createdAt: Long,
)
