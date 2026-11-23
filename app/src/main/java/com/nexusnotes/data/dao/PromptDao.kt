package com.nexusnotes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexusnotes.data.entity.PromptCategoryEntity
import com.nexusnotes.data.entity.PromptEntity
import com.nexusnotes.data.entity.PromptVariableEntity
import com.nexusnotes.data.entity.PromptVersionEntity
import com.nexusnotes.domain.model.SaveOutcome
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PromptDao {

    // ---------------- Prompts ----------------

    @Query("SELECT * FROM prompts ORDER BY isFavorite DESC, updatedAt DESC")
    abstract fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts")
    abstract suspend fun getAll(): List<PromptEntity>

    @Query("SELECT * FROM prompts WHERE id = :id")
    abstract fun observeById(id: String): Flow<PromptEntity?>

    @Query("SELECT * FROM prompts WHERE id = :id")
    abstract suspend fun getById(id: String): PromptEntity?

    /** Full-text search in title and template. [match] comes from SearchQuery.toFtsMatch. */
    @Query(
        """
        SELECT prompts.* FROM prompts
        JOIN prompts_fts ON prompts.rowid = prompts_fts.rowid
        WHERE prompts_fts MATCH :match
        ORDER BY prompts.isFavorite DESC, prompts.updatedAt DESC
        LIMIT :limit
        """,
    )
    abstract fun search(match: String, limit: Int): Flow<List<PromptEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(prompt: PromptEntity)

    @Query("UPDATE prompts SET title = :title, template = :template, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateContent(id: String, title: String, template: String, updatedAt: Long): Int

    @Query("UPDATE prompts SET categoryKey = :categoryKey WHERE id = :id")
    abstract suspend fun setCategory(id: String, categoryKey: String): Int

    @Query("UPDATE prompts SET isFavorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Query("DELETE FROM prompts WHERE id = :id")
    abstract suspend fun deletePromptRow(id: String): Int

    @Query("DELETE FROM prompt_variables WHERE promptId = :id")
    abstract suspend fun deleteVariablesOf(id: String): Int

    @Query("DELETE FROM prompt_versions WHERE promptId = :id")
    abstract suspend fun deleteVersionsOf(id: String): Int

    /** Deletes a prompt with its variables and history. */
    @Transaction
    open suspend fun deletePrompt(id: String) {
        deleteVariablesOf(id)
        deleteVersionsOf(id)
        deletePromptRow(id)
    }

    // ---------------- Variables ----------------

    @Query("SELECT * FROM prompt_variables WHERE promptId = :promptId")
    abstract fun observeVariables(promptId: String): Flow<List<PromptVariableEntity>>

    @Query("SELECT * FROM prompt_variables WHERE promptId = :promptId")
    abstract suspend fun getVariables(promptId: String): List<PromptVariableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertVariable(variable: PromptVariableEntity)

    @Query("DELETE FROM prompt_variables WHERE promptId = :promptId AND name = :name")
    abstract suspend fun deleteVariable(promptId: String, name: String): Int

    // ---------------- Versions ----------------

    @Query("SELECT * FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC")
    abstract fun observeVersions(promptId: String): Flow<List<PromptVersionEntity>>

    @Query("SELECT * FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC LIMIT 1")
    abstract suspend fun latestVersion(promptId: String): PromptVersionEntity?

    @Query("SELECT * FROM prompt_versions WHERE id = :id")
    abstract suspend fun getVersion(id: String): PromptVersionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertVersion(version: PromptVersionEntity)

    @Query("UPDATE prompt_versions SET title = :title, template = :template WHERE id = :id")
    abstract suspend fun updateVersion(id: String, title: String, template: String): Int

    @Query(
        "DELETE FROM prompt_versions WHERE promptId = :promptId AND id NOT IN " +
            "(SELECT id FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC LIMIT :keep)",
    )
    abstract suspend fun trimVersions(promptId: String, keep: Int): Int

    /**
     * Version history: edits within [versionWindowMs] of the latest version update that version;
     * later edits start a new one. At most [maxVersions] are kept per prompt.
     */
    private suspend fun recordVersion(
        promptId: String,
        title: String,
        template: String,
        now: Long,
        newVersionId: String,
        forceNew: Boolean,
    ) {
        val latest = latestVersion(promptId)
        if (!forceNew && latest != null && now - latest.createdAt in 0 until VERSION_WINDOW_MS) {
            updateVersion(latest.id, title, template)
        } else {
            insertVersion(PromptVersionEntity(newVersionId, promptId, title, template, now))
            trimVersions(promptId, MAX_VERSIONS)
        }
    }

    /** Autosave for prompts: creates on first content, then updates; records history. Atomic. */
    @Transaction
    open suspend fun saveContent(
        id: String,
        title: String,
        template: String,
        categoryKeyForNew: String,
        now: Long,
        newVersionId: String,
        forceNewVersion: Boolean = false,
    ): SaveOutcome {
        val existing = getById(id)
        return when {
            existing == null && title.isBlank() && template.isBlank() -> SaveOutcome.SKIPPED_EMPTY
            existing == null -> {
                insert(PromptEntity(id, title, template, categoryKeyForNew, false, now, now))
                recordVersion(id, title, template, now, newVersionId, forceNew = true)
                SaveOutcome.CREATED
            }
            existing.title == title && existing.template == template -> SaveOutcome.UNCHANGED
            else -> {
                updateContent(id, title, template, now)
                recordVersion(id, title, template, now, newVersionId, forceNewVersion)
                SaveOutcome.UPDATED
            }
        }
    }

    /** Inserts a complete prompt (duplicate / import / example) with its variable defaults. */
    @Transaction
    open suspend fun insertWithVariables(prompt: PromptEntity, defaults: Map<String, String>, versionId: String) {
        insert(prompt)
        defaults.forEach { (name, value) -> upsertVariable(PromptVariableEntity(prompt.id, name, value)) }
        insertVersion(PromptVersionEntity(versionId, prompt.id, prompt.title, prompt.template, prompt.updatedAt))
    }

    // ---------------- Categories ----------------

    @Query("SELECT * FROM prompt_categories ORDER BY name COLLATE NOCASE ASC")
    abstract fun observeCategories(): Flow<List<PromptCategoryEntity>>

    @Query("SELECT * FROM prompt_categories")
    abstract suspend fun getCategories(): List<PromptCategoryEntity>

    @Query("SELECT * FROM prompt_categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findCategoryByName(name: String): PromptCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCategory(category: PromptCategoryEntity)

    @Query("UPDATE prompt_categories SET name = :name WHERE id = :id")
    abstract suspend fun renameCategory(id: String, name: String): Int

    @Query("DELETE FROM prompt_categories WHERE id = :id")
    abstract suspend fun deleteCategoryRow(id: String): Int

    @Query("UPDATE prompts SET categoryKey = :toKey WHERE categoryKey = :fromKey")
    abstract suspend fun moveCategory(fromKey: String, toKey: String): Int

    /** Deletes a custom category; its prompts move to [fallbackKey] (they are never deleted). */
    @Transaction
    open suspend fun deleteCategory(id: String, key: String, fallbackKey: String) {
        moveCategory(key, fallbackKey)
        deleteCategoryRow(id)
    }

    companion object {
        const val VERSION_WINDOW_MS = 5 * 60 * 1000L
        const val MAX_VERSIONS = 30
    }
}
