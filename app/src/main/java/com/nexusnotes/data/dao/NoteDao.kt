package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.domain.model.SaveOutcome
import kotlinx.coroutines.flow.Flow

/**
 * All note queries. "Active" means not in Trash and not archived.
 * Pinned notes always come first in active lists.
 */
@Dao
abstract class NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL AND isArchived = 0
        ORDER BY isPinned DESC,
            CASE WHEN :sort = 'MODIFIED' THEN updatedAt END DESC,
            CASE WHEN :sort = 'CREATED' THEN createdAt END DESC,
            CASE WHEN :sort = 'TITLE' THEN title END COLLATE NOCASE ASC,
            updatedAt DESC
        """,
    )
    abstract fun observeActive(sort: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 0 AND isPinned = 1 ORDER BY updatedAt DESC")
    abstract fun observePinned(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY updatedAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 0 AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    abstract fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isArchived = 1 ORDER BY updatedAt DESC")
    abstract fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    abstract fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateContent(id: String, title: String, body: String, updatedAt: Long): Int

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun setPinned(id: String, pinned: Boolean): Int

    @Query("UPDATE notes SET isFavorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE notes SET colorIndex = :colorIndex WHERE id = :id")
    abstract suspend fun setColor(id: String, colorIndex: Int?): Int

    @Query("UPDATE notes SET isArchived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: String, archived: Boolean): Int

    @Query("UPDATE notes SET deletedAt = :deletedAt, isPinned = 0 WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun moveToTrash(id: String, deletedAt: Long): Int

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    abstract suspend fun restore(id: String): Int

    /** Only notes already in Trash can be deleted permanently. */
    @Query("DELETE FROM notes WHERE id = :id AND deletedAt IS NOT NULL")
    abstract suspend fun deletePermanently(id: String): Int

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    abstract suspend fun emptyTrash(): Int

    /** Autosave: create the note on first real content, update it afterwards. Atomic. */
    @Transaction
    open suspend fun saveContent(id: String, title: String, body: String, now: Long): SaveOutcome {
        val existing = getById(id)
        return when {
            existing == null && title.isBlank() && body.isBlank() -> SaveOutcome.SKIPPED_EMPTY
            existing == null -> {
                insert(NoteEntity.new(id, title, body, now))
                SaveOutcome.CREATED
            }
            existing.deletedAt != null -> SaveOutcome.READ_ONLY
            existing.title == title && existing.body == body -> SaveOutcome.UNCHANGED
            else -> {
                updateContent(id, title, body, now)
                SaveOutcome.UPDATED
            }
        }
    }

    /** Copies a note under [newId]. Returns false if the source no longer exists. */
    @Transaction
    open suspend fun duplicate(id: String, newId: String, now: Long): Boolean {
        val source = getById(id) ?: return false
        insert(
            source.copy(
                id = newId,
                title = if (source.title.isBlank()) "" else "${source.title} (copy)",
                isPinned = false,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }
}
