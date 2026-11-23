package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.dao.NoteDao
import com.nexusnotes.data.entity.NoteEntity
import com.nexusnotes.data.entity.toDomain
import com.nexusnotes.domain.model.Note
import com.nexusnotes.domain.model.NoteSort
import com.nexusnotes.domain.model.SaveOutcome
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

    suspend fun getNote(id: String): Note?
    suspend fun saveContent(id: String, title: String, body: String): SaveOutcome
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
) : NoteRepository {

    private fun Flow<List<NoteEntity>>.toNotes(): Flow<List<Note>> = map { list -> list.map { it.toDomain() } }

    override fun observeActive(sort: NoteSort) = dao.observeActive(sort.name).toNotes()
    override fun observePinned() = dao.observePinned().toNotes()
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).toNotes()
    override fun observeFavorites() = dao.observeFavorites().toNotes()
    override fun observeArchived() = dao.observeArchived().toNotes()
    override fun observeTrash() = dao.observeTrash().toNotes()
    override fun observeNote(id: String): Flow<Note?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getNote(id: String): Note? = dao.getById(id)?.toDomain()

    override suspend fun saveContent(id: String, title: String, body: String): SaveOutcome =
        dao.saveContent(id, title, body, clock.now())

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
    }

    override suspend fun emptyTrash() {
        dao.emptyTrash()
    }

    override suspend fun duplicate(id: String): String? {
        val newId = UUID.randomUUID().toString()
        return if (dao.duplicate(id, newId, clock.now())) newId else null
    }
}
