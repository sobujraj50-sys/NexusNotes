package com.nexusnotes.data.repository

import com.nexusnotes.core.common.Clock
import com.nexusnotes.data.dao.PromptDao
import com.nexusnotes.data.entity.PromptCategoryEntity
import com.nexusnotes.data.entity.PromptEntity
import com.nexusnotes.data.entity.PromptVariableEntity
import com.nexusnotes.data.entity.toDomain
import com.nexusnotes.domain.FolderTree
import com.nexusnotes.domain.model.Prompt
import com.nexusnotes.domain.model.PromptCategory
import com.nexusnotes.domain.model.PromptCategoryRef
import com.nexusnotes.domain.model.PromptVersion
import com.nexusnotes.domain.model.SaveOutcome
import com.nexusnotes.domain.prompt.PromptExamples
import com.nexusnotes.domain.prompt.PromptExchange
import com.nexusnotes.domain.search.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

data class ImportReport(val added: Int, val skippedSame: Int, val addedAsCopy: Int, val invalid: Int)

/** Prompt library storage. Everything stays on the device. */
class PromptRepository(
    private val dao: PromptDao,
    private val clock: Clock,
) {
    private fun newId() = UUID.randomUUID().toString()

    fun observePrompts(): Flow<List<Prompt>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observePrompt(id: String): Flow<Prompt?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun getPrompt(id: String): Prompt? = dao.getById(id)?.toDomain()

    fun search(query: String, limit: Int = 100): Flow<List<Prompt>> {
        val match = SearchQuery.toFtsMatch(query) ?: return flowOf(emptyList())
        return dao.search(match, limit).map { list -> list.map { it.toDomain() } }
    }

    /** Built-in categories followed by the user's own. */
    fun observeCategories(): Flow<List<PromptCategoryRef>> = dao.observeCategories().map { custom ->
        PromptCategory.entries.map { PromptCategoryRef(it.name, it.displayName, isCustom = false) } +
            custom.map { PromptCategoryRef(PromptCategoryRef.customKey(it.id), it.name, isCustom = true) }
    }

    suspend fun saveContent(id: String, title: String, template: String, categoryKeyForNew: String): SaveOutcome =
        dao.saveContent(id, title, template, categoryKeyForNew, clock.now(), newId())

    suspend fun setCategory(id: String, categoryKey: String) {
        dao.setCategory(id, categoryKey)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    suspend fun delete(id: String) {
        dao.deletePrompt(id)
    }

    /** Copy of a prompt (with its variable defaults). Returns the new id. */
    suspend fun duplicate(id: String): String? {
        val source = dao.getById(id) ?: return null
        val defaults = dao.getVariables(id).associate { it.name to it.defaultValue }
        val now = clock.now()
        val copy = source.copy(
            id = newId(),
            title = if (source.title.isBlank()) "" else "${source.title} (copy)",
            isFavorite = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertWithVariables(copy, defaults, newId())
        return copy.id
    }

    // ---------------- Variables ----------------

    fun observeVariableDefaults(promptId: String): Flow<Map<String, String>> =
        dao.observeVariables(promptId).map { list -> list.associate { it.name to it.defaultValue } }

    suspend fun getVariableDefaults(promptId: String): Map<String, String> =
        dao.getVariables(promptId).associate { it.name to it.defaultValue }

    suspend fun setVariableDefault(promptId: String, name: String, value: String) {
        if (value.isEmpty()) {
            dao.deleteVariable(promptId, name)
        } else {
            dao.upsertVariable(PromptVariableEntity(promptId, name, value.take(5_000)))
        }
    }

    // ---------------- Versions ----------------

    fun observeVersions(promptId: String): Flow<List<PromptVersion>> =
        dao.observeVersions(promptId).map { list -> list.map { it.toDomain() } }

    /** Makes an old version the current content (recorded as a new version; nothing is lost). */
    suspend fun restoreVersion(promptId: String, versionId: String): Boolean {
        val version = dao.getVersion(versionId) ?: return false
        val prompt = dao.getById(promptId) ?: return false
        dao.saveContent(promptId, version.title, version.template, prompt.categoryKey, clock.now(), newId(), forceNewVersion = true)
        return true
    }

    // ---------------- Categories ----------------

    suspend fun createCategory(rawName: String): Result<String> = runCatching {
        val name = FolderTree.cleanName(rawName, maxLength = 40) ?: throw OrganizeException("Enter a category name.")
        if (PromptCategory.entries.any { it.displayName.equals(name, ignoreCase = true) } || dao.findCategoryByName(name) != null) {
            throw OrganizeException("A category named “$name” already exists.")
        }
        val id = newId()
        dao.insertCategory(PromptCategoryEntity(id, name, clock.now()))
        PromptCategoryRef.customKey(id)
    }

    suspend fun renameCategory(key: String, rawName: String): Result<Unit> = runCatching {
        val id = key.removePrefix(PromptCategoryRef.CUSTOM_PREFIX)
        val name = FolderTree.cleanName(rawName, maxLength = 40) ?: throw OrganizeException("Enter a category name.")
        val existing = dao.findCategoryByName(name)
        if ((existing != null && existing.id != id) || PromptCategory.entries.any { it.displayName.equals(name, ignoreCase = true) }) {
            throw OrganizeException("A category named “$name” already exists.")
        }
        dao.renameCategory(id, name)
    }

    /** Deletes a user category; its prompts move to General Prompts. */
    suspend fun deleteCategory(key: String) {
        dao.deleteCategory(key.removePrefix(PromptCategoryRef.CUSTOM_PREFIX), key, PromptCategory.GENERAL.name)
    }

    // ---------------- Examples, export, import ----------------

    suspend fun addExamples(): Int {
        val now = clock.now()
        PromptExamples.all.forEachIndexed { index, ex ->
            dao.insertWithVariables(
                PromptEntity(newId(), ex.title, ex.template, ex.category.name, false, now + index, now + index),
                ex.defaults,
                newId(),
            )
        }
        return PromptExamples.all.size
    }

    /** JSON text of the given prompts (all prompts when [ids] is null). */
    suspend fun exportJson(ids: Set<String>? = null): String {
        val categories = dao.getCategories().associateBy { PromptCategoryRef.customKey(it.id) }
        val prompts = dao.getAll().filter { ids == null || it.id in ids }.sortedBy { it.createdAt }
        val exported = prompts.map { p ->
            val custom = categories[p.categoryKey]
            PromptExchange.ExportedPrompt(
                id = p.id,
                title = p.title,
                template = p.template,
                category = custom?.name ?: p.categoryKey,
                isCustomCategory = custom != null,
                isFavorite = p.isFavorite,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                variableDefaults = dao.getVariables(p.id).associate { it.name to it.defaultValue },
            )
        }
        return PromptExchange.encode(exported, clock.now())
    }

    /**
     * Imports a prompts file. Nothing existing is ever overwritten:
     * same id + same content → skipped; same id + different content → added as a copy.
     */
    suspend fun importJson(text: String): Result<ImportReport> = runCatching {
        val bundle = PromptExchange.decode(text).getOrThrow()
        var added = 0
        var same = 0
        var copies = 0
        val now = clock.now()
        bundle.prompts.forEach { p ->
            val existing = if (p.id.isNotBlank()) dao.getById(p.id) else null
            if (existing != null && existing.title == p.title && existing.template == p.template) {
                same++
                return@forEach
            }
            val categoryKey = resolveCategory(p)
            val id = if (existing == null && p.id.isNotBlank()) p.id else newId()
            if (existing != null) copies++ else added++
            dao.insertWithVariables(
                PromptEntity(
                    id = id,
                    title = if (existing != null) "${p.title} (imported)" else p.title,
                    template = p.template,
                    categoryKey = categoryKey,
                    isFavorite = p.isFavorite,
                    createdAt = p.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = p.updatedAt.takeIf { it > 0 } ?: now,
                ),
                p.variableDefaults,
                newId(),
            )
        }
        ImportReport(added, same, copies, bundle.skippedInvalid)
    }

    private suspend fun resolveCategory(p: PromptExchange.ExportedPrompt): String {
        if (!p.isCustomCategory) {
            return PromptCategory.entries.firstOrNull { it.name == p.category }?.name ?: PromptCategory.GENERAL.name
        }
        val name = FolderTree.cleanName(p.category, maxLength = 40) ?: return PromptCategory.GENERAL.name
        dao.findCategoryByName(name)?.let { return PromptCategoryRef.customKey(it.id) }
        val id = newId()
        dao.insertCategory(PromptCategoryEntity(id, name, clock.now()))
        return PromptCategoryRef.customKey(id)
    }
}
