package com.nexusnotes.domain.model

/**
 * A note as the app sees it. [body] is Markdown text (format version 1).
 * Trash is a soft delete: [deletedAt] is set, the row stays until permanently deleted.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val colorIndex: Int?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: String? = null,
) {
    val isInTrash: Boolean get() = deletedAt != null
}

/** Sort options for the All Notes list. Enum names are persisted in settings — do not rename. */
enum class NoteSort(val label: String) {
    MODIFIED("Last edited"),
    CREATED("Date created"),
    TITLE("Title A–Z"),
}

/** Enum names are persisted in settings — do not rename. */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val noteSort: NoteSort = NoteSort.MODIFIED,
    val saveSearchHistory: Boolean = false,
    val searchHistory: List<String> = emptyList(),
)

/** Result of an autosave attempt. */
enum class SaveOutcome {
    /** New note row inserted. */
    CREATED,

    /** Existing note text changed and was written. */
    UPDATED,

    /** Text identical to what is stored; nothing written. */
    UNCHANGED,

    /** A brand-new note with empty title and body is never stored. */
    SKIPPED_EMPTY,

    /** Notes in Trash are read-only. */
    READ_ONLY,
}

data class Folder(
    val id: String,
    val name: String,
    val parentId: String?,
    val colorIndex: Int?,
    val createdAt: Long,
)

/** A folder placed in the tree: [depth] 0 = top level. */
data class FolderNode(val folder: Folder, val depth: Int, val noteCount: Int = 0)

data class Tag(val id: String, val name: String)

data class TagWithCount(val tag: Tag, val noteCount: Int)

/** Filters for the Search screen. A null field means "any". */
data class SearchFilter(
    val folderId: String? = null,
    val unfiledOnly: Boolean = false,
    val tagId: String? = null,
    val includeArchived: Boolean = false,
)

/** A saved prompt template. [categoryKey]: built-in PromptCategory name or "custom:<id>". */
data class Prompt(
    val id: String,
    val title: String,
    val template: String,
    val categoryKey: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PromptVersion(val id: String, val title: String, val template: String, val createdAt: Long)

/** A category as shown in the UI: built-in or user-created. */
data class PromptCategoryRef(val key: String, val name: String, val isCustom: Boolean) {
    companion object {
        const val CUSTOM_PREFIX = "custom:"
        fun customKey(id: String) = CUSTOM_PREFIX + id
    }
}

/** A file attached to a note. The bytes live in app-private storage under [fileName]. */
data class Attachment(
    val id: String,
    val noteId: String,
    val kind: com.nexusnotes.domain.attachment.AttachmentKind,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val createdAt: Long,
)
