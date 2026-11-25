package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexusnotes.data.entity.NoteTemplateEntity
import com.nexusnotes.data.entity.ReminderEntity
import com.nexusnotes.data.entity.ReminderWithNote
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun delete(noteId: String)

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    suspend fun get(noteId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    fun observe(noteId: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query(
        "SELECT r.noteId, r.triggerAt, r.repeat, r.anchorAt, n.title, n.deletedAt FROM reminders r " +
            "JOIN notes n ON n.id = r.noteId ORDER BY r.triggerAt",
    )
    fun observeAllWithNotes(): Flow<List<ReminderWithNote>>

    @Query(
        "SELECT r.noteId, r.triggerAt, r.repeat, r.anchorAt, n.title, n.deletedAt FROM reminders r " +
            "JOIN notes n ON n.id = r.noteId WHERE r.noteId = :noteId",
    )
    suspend fun getWithNote(noteId: String): ReminderWithNote?
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM note_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<NoteTemplateEntity>>

    @Query("SELECT * FROM note_templates")
    suspend fun all(): List<NoteTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: NoteTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<NoteTemplateEntity>)

    @Query("DELETE FROM note_templates WHERE id = :id")
    suspend fun delete(id: String)
}
