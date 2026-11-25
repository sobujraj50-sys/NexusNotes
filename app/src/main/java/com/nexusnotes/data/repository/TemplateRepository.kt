package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.dao.TemplateDao
import com.nexusnotes.data.entity.NoteTemplateEntity
import com.nexusnotes.domain.template.NoteTemplate
import com.nexusnotes.domain.template.NoteTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

class TemplateRepository(
    private val dao: TemplateDao,
    private val notes: NoteRepository,
    private val clock: Clock,
) {
    /** Built-in templates first, then the user's own. */
    fun observeAll(): Flow<List<NoteTemplate>> = dao.observeAll().map { mine ->
        NoteTemplates.builtIns + mine.map { NoteTemplate(it.id, it.name, it.title, it.body, builtIn = false) }
    }

    suspend fun saveFromNote(name: String, title: String, body: String) {
        dao.upsert(NoteTemplateEntity(UUID.randomUUID().toString(), name.trim().take(80).ifBlank { "My template" }, title, body, clock.now()))
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Creates a note from [template] (placeholders filled) and returns its id. */
    suspend fun createNote(template: NoteTemplate, folderId: String? = null): String {
        val id = UUID.randomUUID().toString()
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val title = NoteTemplates.fill(template.title, now, zone).ifBlank { template.name }
        notes.saveContent(id, title, NoteTemplates.fill(template.body, now, zone), folderId)
        return id
    }
}
