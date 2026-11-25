package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexusnotes.data.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    abstract fun observeForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    abstract suspend fun getForNote(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    abstract suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE noteId = :noteId")
    abstract suspend fun countForNote(noteId: String): Int

    @Query("SELECT fileName FROM attachments")
    abstract suspend fun allFileNames(): List<String>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM attachments")
    abstract fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    abstract suspend fun delete(id: String): Int
}
