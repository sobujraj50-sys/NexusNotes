package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.toDomain
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.NoteSort
import com.nexusnotes.domain.model.SaveOutcome
import com.nexusnotes.domain.model.SearchFilter
import com.nexusnotes.domain.search.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface NoteRepository {
    fun observeActive(sort: NoteSort): Flow<List<Note>>
    fun observePinned(): Flow<List<Note>>
    fun observeRecent(limit: Int): Flow<List<Note>>
    fun observeFavorites(): Flow<List<Note>>
    fun observeArchived(): Flow<List<Note>>
    fun observeTrash(): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>

    fun observeInFolder(folderId: String): Flow<List<Note>>
    fun observeUnfiled(): Flow<List<Note>>

    /** Full-text search with filters. A blank query lists notes matching the filters only. */
    fun search(query: String, filter: SearchFilter, limit: Int = 200): Flow<List<Note>>

    suspend fun getNote(id: String): Note?
    suspend fun saveContent(id: String, title: String, body: String, folderIdForNew: String? = null): SaveOutcome
    suspend fun setFolder(id: String, folderId: String?)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setColor(id: String, colorIndex: Int?)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun moveToTrash(id: String)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun emptyTrash()

    /** Returns the new note's id, or null if the source note no longer exists. */
    suspend fun duplicate(id: String): String?
}

/** Room-backed implementation. All data stays on the device. */
class OfflineNoteRepository(
    private val dao: NoteDao,
    private val clock: Clock,
    /** Lets note deletion remove attachment files and duplication copy them. */
    private val attachments: () -> AttachmentRepository,
) : NoteRepository {

    private fun Flow<List<NoteEntity>>.toNotes(): Flow<List<Note>> = map { list -> list.map { it.toDomain() } }

    override fun observeActive(sort: NoteSort) = dao.observeActive(sort.name).toNotes()
    override fun observePinned() = dao.observePinned().toNotes()
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).toNotes()
    override fun observeFavorites() = dao.observeFavorites().toNotes()
    override fun observeArchived() = dao.observeArchived().toNotes()
    override fun observeTrash() = dao.observeTrash().toNotes()
    override fun observeNote(id: String): Flow<Note?> = dao.observeById(id).map { it?.toDomain() }

    override fun observeInFolder(folderId: String) = dao.observeInFolder(folderId).toNotes()
    override fun observeUnfiled() = dao.observeUnfiled().toNotes()

    override fun search(query: String, filter: SearchFilter, limit: Int): Flow<List<Note>> {
        val match = SearchQuery.toFtsMatch(query)
        return if (match == null) {
            dao.filter(filter.folderId, filter.unfiledOnly, filter.tagId, filter.includeArchived, limit).toNotes()
        } else {
            dao.search(match, filter.folderId, filter.unfiledOnly, filter.tagId, filter.includeArchived, limit).toNotes()
        }
    }

    override suspend fun getNote(id: String): Note? = dao.getById(id)?.toDomain()

    override suspend fun saveContent(id: String, title: String, body: String, folderIdForNew: String?): SaveOutcome =
        dao.saveContent(id, title, body, clock.now(), folderIdForNew)

    override suspend fun setFolder(id: String, folderId: String?) {
        dao.setFolder(id, folderId)
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    override suspend fun setColor(id: String, colorIndex: Int?) {
        dao.setColor(id, colorIndex)
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        dao.setArchived(id, archived)
    }

    override suspend fun moveToTrash(id: String) {
        dao.moveToTrash(id, clock.now())
    }

    override suspend fun restore(id: String) {
        dao.restore(id)
    }

    override suspend fun deletePermanently(id: String) {
        dao.deletePermanently(id)
        attachments().cleanUpFiles()
    }

    override suspend fun emptyTrash() {
        dao.emptyTrash()
        attachments().cleanUpFiles()
    }

    override suspend fun duplicate(id: String): String? {
        val newId = UUID.randomUUID().toString()
        if (!dao.duplicate(id, newId, clock.now())) return null
        attachments().duplicateAll(id, newId)
        return newId
    }
}
