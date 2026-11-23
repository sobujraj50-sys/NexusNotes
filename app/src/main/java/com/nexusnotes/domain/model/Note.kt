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
