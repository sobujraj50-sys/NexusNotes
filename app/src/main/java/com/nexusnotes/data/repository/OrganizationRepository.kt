package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.dao.FolderDao
import com.nexusnotes.data.dao.TagDao
import com.nexusnotes.data.entity.FolderEntity
import com.nexusnotes.data.entity.toDomain
import com.nexusnotes.domain.FolderTree
import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.FolderNode
import com.nexusnotes.domain.model.Tag
import com.nexusnotes.domain.model.TagWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/** A user-facing reason an organize action was refused. */
class OrganizeException(message: String) : Exception(message)

/** Folders and tags. All rules (unique names, no folder loops, depth limit) are enforced here. */
class OrganizationRepository(
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val clock: Clock,
) {
    // ---------------- Folders ----------------

    fun observeFolderTree(): Flow<List<FolderNode>> =
        combine(folderDao.observeAll(), folderDao.observeNoteCounts()) { folders, counts ->
            FolderTree.flatten(folders.map { it.toDomain() }, counts.associate { it.folderId to it.count })
        }

    fun observeFolder(id: String): Flow<Folder?> = folderDao.observeById(id).map { it?.toDomain() }

    suspend fun createFolder(rawName: String, parentId: String?, colorIndex: Int? = null): Result<String> = runCatching {
        val name = FolderTree.cleanName(rawName) ?: throw OrganizeException("Enter a folder name.")
        val all = folderDao.getAll().map { it.toDomain() }
        if (parentId != null && all.none { it.id == parentId }) throw OrganizeException("That folder no longer exists.")
        if (!FolderTree.canCreateUnder(all, parentId)) throw OrganizeException("Folders can be nested up to ${FolderTree.MAX_DEPTH} levels.")
        if (folderDao.countSiblingsNamed(name, parentId, excludeId = "") > 0) throw OrganizeException("A folder named “$name” already exists here.")
        val id = UUID.randomUUID().toString()
        folderDao.insert(FolderEntity(id, name, parentId, colorIndex, clock.now()))
        id
    }

    suspend fun renameFolder(id: String, rawName: String): Result<Unit> = runCatching {
        val name = FolderTree.cleanName(rawName) ?: throw OrganizeException("Enter a folder name.")
        val folder = folderDao.getById(id) ?: throw OrganizeException("That folder no longer exists.")
        if (folderDao.countSiblingsNamed(name, folder.parentId, excludeId = id) > 0) {
            throw OrganizeException("A folder named “$name” already exists here.")
        }
        folderDao.rename(id, name)
    }

    suspend fun setFolderColor(id: String, colorIndex: Int?) {
        folderDao.setColor(id, colorIndex)
    }

    suspend fun moveFolder(id: String, newParentId: String?): Result<Unit> = runCatching {
        val all = folderDao.getAll().map { it.toDomain() }
        val folder = all.firstOrNull { it.id == id } ?: throw OrganizeException("That folder no longer exists.")
        when (FolderTree.checkMove(all, id, newParentId)) {
            FolderTree.MoveCheck.IntoItself -> throw OrganizeException("A folder can't be moved into itself.")
            FolderTree.MoveCheck.IntoOwnSubfolder -> throw OrganizeException("A folder can't be moved into its own sub-folder.")
            FolderTree.MoveCheck.TooDeep -> throw OrganizeException("Folders can be nested up to ${FolderTree.MAX_DEPTH} levels.")
            FolderTree.MoveCheck.Ok -> Unit
        }
        if (folderDao.countSiblingsNamed(folder.name, newParentId, excludeId = id) > 0) {
            throw OrganizeException("The destination already has a folder named “${folder.name}”.")
        }
        folderDao.setParent(id, newParentId)
    }

    /** Number of sub-folders that would be deleted together with [id]. */
    suspend fun subfolderCount(id: String): Int =
        FolderTree.subtreeIds(folderDao.getAll().map { it.toDomain() }, id).size - 1

    /** Deletes the folder and its sub-folders. Notes inside are kept and become Unfiled. */
    suspend fun deleteFolder(id: String) {
        val ids = FolderTree.subtreeIds(folderDao.getAll().map { it.toDomain() }, id)
        folderDao.deleteFoldersKeepNotes(ids)
    }

    // ---------------- Tags ----------------

    fun observeTagsWithCounts(): Flow<List<TagWithCount>> =
        tagDao.observeAllWithCounts().map { list -> list.map { TagWithCount(Tag(it.id, it.name), it.count) } }

    fun observeTags(): Flow<List<Tag>> = tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTag(id: String): Flow<Tag?> = tagDao.observeById(id).map { it?.toDomain() }

    fun observeTagsForNote(noteId: String): Flow<List<Tag>> =
        tagDao.observeForNote(noteId).map { list -> list.map { it.toDomain() } }

    suspend fun addTagToNote(noteId: String, rawName: String): Result<Unit> = runCatching {
        val name = cleanTagName(rawName) ?: throw OrganizeException("Enter a tag name.")
        tagDao.addTagToNote(noteId, name, UUID.randomUUID().toString())
    }

    suspend fun removeTagFromNote(noteId: String, tagId: String) {
        tagDao.unlink(noteId, tagId)
    }

    suspend fun renameTag(id: String, rawName: String): Result<Unit> = runCatching {
        val name = cleanTagName(rawName) ?: throw OrganizeException("Enter a tag name.")
        val existing = tagDao.findByName(name)
        if (existing != null && existing.id != id) throw OrganizeException("A tag named “${existing.name}” already exists.")
        tagDao.rename(id, name)
    }

    /** Removes the tag from all notes and deletes it. Notes are not changed otherwise. */
    suspend fun deleteTag(id: String) {
        tagDao.deleteTag(id)
    }

    private fun cleanTagName(raw: String): String? =
        FolderTree.cleanName(raw.trim().removePrefix("#"), maxLength = 40)
}
