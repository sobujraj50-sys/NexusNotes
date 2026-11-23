package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexusnotes.data.entity.NoteTagCrossRef
import com.nexusnotes.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagCount(val id: String, val name: String, val count: Int)

@Dao
abstract class TagDao {

    /** All tags with the number of active notes using each. */
    @Query(
        "SELECT t.id AS id, t.name AS name, " +
            "(SELECT COUNT(*) FROM note_tags nt JOIN notes n ON n.id = nt.noteId " +
            "WHERE nt.tagId = t.id AND n.deletedAt IS NULL) AS count " +
            "FROM tags t ORDER BY t.name COLLATE NOCASE ASC",
    )
    abstract fun observeAllWithCounts(): Flow<List<TagCount>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    abstract fun observeById(id: String): Flow<TagEntity?>

    @Query(
        "SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id " +
            "WHERE nt.noteId = :noteId ORDER BY t.name COLLATE NOCASE ASC",
    )
    abstract fun observeForNote(noteId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun link(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    abstract suspend fun unlink(noteId: String, tagId: String): Int

    @Query("SELECT tagId FROM note_tags WHERE noteId = :noteId")
    abstract suspend fun tagIdsForNote(noteId: String): List<String>

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: String, name: String): Int

    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    abstract suspend fun unlinkAllFromTag(tagId: String): Int

    @Query("DELETE FROM tags WHERE id = :tagId")
    abstract suspend fun delete(tagId: String): Int

    /** Adds a tag to a note, creating the tag if no tag with that name (any case) exists. */
    @Transaction
    open suspend fun addTagToNote(noteId: String, name: String, newTagId: String) {
        val existing = findByName(name)
        val tagId = existing?.id ?: newTagId.also { insert(TagEntity(it, name)) }
        link(NoteTagCrossRef(noteId, tagId))
    }

    /** Deleting a tag removes it from notes; the notes themselves are untouched. */
    @Transaction
    open suspend fun deleteTag(tagId: String) {
        unlinkAllFromTag(tagId)
        delete(tagId)
    }
}
