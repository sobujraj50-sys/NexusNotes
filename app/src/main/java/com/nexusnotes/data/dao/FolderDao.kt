package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexusnotes.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

data class FolderNoteCount(val folderId: String, val count: Int)

@Dao
abstract class FolderDao {

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    abstract suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    abstract fun observeById(id: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE id = :id")
    abstract suspend fun getById(id: String): FolderEntity?

    /** Note counts per folder (active notes only: not archived, not in Trash). */
    @Query(
        "SELECT folderId AS folderId, COUNT(*) AS count FROM notes " +
            "WHERE folderId IS NOT NULL AND deletedAt IS NULL AND isArchived = 0 GROUP BY folderId",
    )
    abstract fun observeNoteCounts(): Flow<List<FolderNoteCount>>

    @Query(
        "SELECT COUNT(*) FROM folders WHERE name = :name COLLATE NOCASE AND id != :excludeId AND " +
            "((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)",
    )
    abstract suspend fun countSiblingsNamed(name: String, parentId: String?, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: String, name: String): Int

    @Query("UPDATE folders SET colorIndex = :colorIndex WHERE id = :id")
    abstract suspend fun setColor(id: String, colorIndex: Int?): Int

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    abstract suspend fun setParent(id: String, parentId: String?): Int

    @Query("UPDATE notes SET folderId = NULL WHERE folderId IN (:folderIds)")
    abstract suspend fun unfileNotesIn(folderIds: List<String>): Int

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    abstract suspend fun deleteAll(folderIds: List<String>): Int

    /**
     * Deletes [folderIds] (a folder and all its sub-folders, computed by the caller).
     * Notes inside are kept and become Unfiled. Atomic.
     */
    @Transaction
    open suspend fun deleteFoldersKeepNotes(folderIds: List<String>) {
        if (folderIds.isEmpty()) return
        unfileNotesIn(folderIds)
        deleteAll(folderIds)
    }
}
