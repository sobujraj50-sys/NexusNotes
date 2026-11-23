package com.nexusnotes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nexusnotes.domain.model.Prompt
import com.nexusnotes.domain.model.PromptVersion

/**
 * A saved prompt (schema v3). [categoryKey] is a built-in PromptCategory name
 * (e.g. IMAGE_GENERATION) or "custom:<id>" for a user category.
 */
@Entity(
    tableName = "prompts",
    indices = [Index(value = ["categoryKey"]), Index(value = ["updatedAt"])],
)
data class PromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val template: String,
    val categoryKey: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

fun PromptEntity.toDomain() = Prompt(id, title, template, categoryKey, isFavorite, createdAt, updatedAt)

/** A user-created prompt category (schema v3). */
@Entity(tableName = "prompt_categories")
data class PromptCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

/** Default value for a {{variable}} of one prompt (schema v3). */
@Entity(
    tableName = "prompt_variables",
    primaryKeys = ["promptId", "name"],
    foreignKeys = [
        ForeignKey(entity = PromptEntity::class, parentColumns = ["id"], childColumns = ["promptId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PromptVariableEntity(
    val promptId: String,
    val name: String,
    val defaultValue: String,
)

/** A snapshot of a prompt for version history (schema v3). */
@Entity(
    tableName = "prompt_versions",
    indices = [Index(value = ["promptId", "createdAt"])],
    foreignKeys = [
        ForeignKey(entity = PromptEntity::class, parentColumns = ["id"], childColumns = ["promptId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PromptVersionEntity(
    @PrimaryKey val id: String,
    val promptId: String,
    val title: String,
    val template: String,
    val createdAt: Long,
)

fun PromptVersionEntity.toDomain() = PromptVersion(id, title, template, createdAt)

/** Full-text index over prompt title and template (schema v3). */
@Fts4(contentEntity = PromptEntity::class)
@Entity(tableName = "prompts_fts")
data class PromptFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "template") val template: String,
)
