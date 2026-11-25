package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexusnotes.data.entity.AttachmentEntity
import com.nexusnotes.data.entity.BackupHistoryEntity
import com.nexusnotes.data.entity.FolderEntity
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.NoteTagCrossRef
import com.nexusnotes.data.entity.PromptCategoryEntity
import com.nexusnotes.data.entity.PromptEntity
import com.nexusnotes.data.entity.PromptVariableEntity
import com.nexusnotes.data.entity.PromptVersionEntity
import com.nexusnotes.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** Whole-table reads and writes for backup/restore. Restores run in one transaction: all or nothing. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM notes") abstract suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM folders") abstract suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM tags") abstract suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM note_tags") abstract suspend fun noteTags(): List<NoteTagCrossRef>
    @Query("SELECT * FROM prompts") abstract suspend fun prompts(): List<PromptEntity>
    @Query("SELECT * FROM prompt_categories") abstract suspend fun categories(): List<PromptCategoryEntity>
    @Query("SELECT * FROM prompt_variables") abstract suspend fun variables(): List<PromptVariableEntity>
    @Query("SELECT * FROM prompt_versions") abstract suspend fun versions(): List<PromptVersionEntity>
    @Query("SELECT * FROM attachments") abstract suspend fun attachments(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertNotes(items: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertFolders(items: List<FolderEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertTags(items: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertNoteTags(items: List<NoteTagCrossRef>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertPrompts(items: List<PromptEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertCategories(items: List<PromptCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertVariables(items: List<PromptVariableEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertVersions(items: List<PromptVersionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insertAttachments(items: List<AttachmentEntity>)

    @Query("DELETE FROM note_tags") abstract suspend fun clearNoteTags()
    @Query("DELETE FROM attachments") abstract suspend fun clearAttachments()
    @Query("DELETE FROM notes") abstract suspend fun clearNotes()
    @Query("DELETE FROM folders") abstract suspend fun clearFolders()
    @Query("DELETE FROM tags") abstract suspend fun clearTags()
    @Query("DELETE FROM prompt_variables") abstract suspend fun clearVariables()
    @Query("DELETE FROM prompt_versions") abstract suspend fun clearVersions()
    @Query("DELETE FROM prompts") abstract suspend fun clearPrompts()
    @Query("DELETE FROM prompt_categories") abstract suspend fun clearCategories()

    /**
     * Writes a restore. With [replace], current notes/folders/tags/prompts/attachments are deleted
     * first, inside the same transaction, so a failure leaves the old data exactly as it was.
     */
    @Transaction
    open suspend fun applyRestore(
        replace: Boolean,
        notes: List<NoteEntity>,
        folders: List<FolderEntity>,
        tags: List<TagEntity>,
        noteTags: List<NoteTagCrossRef>,
        categories: List<PromptCategoryEntity>,
        prompts: List<PromptEntity>,
        variables: List<PromptVariableEntity>,
        versions: List<PromptVersionEntity>,
        attachments: List<AttachmentEntity>,
    ) {
        if (replace) {
            clearNoteTags()
            clearAttachments()
            clearNotes()
            clearFolders()
            clearTags()
            clearVariables()
            clearVersions()
            clearPrompts()
            clearCategories()
        }
        insertFolders(folders)
        insertNotes(notes)
        insertTags(tags)
        insertNoteTags(noteTags)
        insertCategories(categories)
        insertPrompts(prompts)
        insertVariables(variables)
        insertVersions(versions)
        insertAttachments(attachments)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertHistory(entry: BackupHistoryEntity)

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC LIMIT 30")
    abstract fun observeHistory(): Flow<List<BackupHistoryEntity>>

    @Query("DELETE FROM backup_history WHERE id NOT IN (SELECT id FROM backup_history ORDER BY createdAt DESC LIMIT 100)")
    abstract suspend fun trimHistory()
}
