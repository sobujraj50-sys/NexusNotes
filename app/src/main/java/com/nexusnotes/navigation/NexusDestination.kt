package com.nexusnotes.navigation

const val ARG_NOTE_ID = "noteId"
const val ARG_FOLDER_ID = "folderId"
const val ARG_PROMPT_ID = "promptId"
const val ARG_TAG_ID = "tagId"

/** Virtual folder id for notes that have no folder. Never stored as a real folder row. */
const val UNFILED_FOLDER_ID = "unfiled"

/**
 * Every destination in the app. Routes are plain strings so no serialization plugin
 * is needed. IDs passed in routes are UUID strings (URL-safe), see docs/ARCHITECTURE.md.
 *
 * Lists of destinations live in [NexusDestinations], NOT in a companion object here:
 * referencing subclass objects from a sealed parent's companion can observe them as
 * null during class initialization.
 */
sealed class NexusDestination(val route: String, val title: String) {
    data object Home : NexusDestination("home", "Nexus Notes")
    data object AllNotes : NexusDestination("notes", "All Notes")

    data object NoteEditor : NexusDestination("editor?$ARG_NOTE_ID={$ARG_NOTE_ID}&$ARG_FOLDER_ID={$ARG_FOLDER_ID}", "Note") {
        /** [folderId] puts a new note into that folder. Ignored for existing notes. */
        fun createRoute(noteId: String? = null, folderId: String? = null): String =
            withQuery("editor", ARG_NOTE_ID to noteId, ARG_FOLDER_ID to folderId)
    }

    data object Folders : NexusDestination("folders", "Folders")

    data object FolderDetail : NexusDestination("folders/{$ARG_FOLDER_ID}", "Folder") {
        fun createRoute(folderId: String): String = "folders/$folderId"
    }

    data object Tags : NexusDestination("tags", "Tags")
    data object PromptLibrary : NexusDestination("prompts", "Prompt Library")

    data object PromptEditor : NexusDestination("prompts/editor?$ARG_PROMPT_ID={$ARG_PROMPT_ID}", "Prompt") {
        fun createRoute(promptId: String? = null): String =
            if (promptId == null) "prompts/editor" else "prompts/editor?$ARG_PROMPT_ID=$promptId"
    }

    data object PromptRunner : NexusDestination("prompts/run?$ARG_PROMPT_ID={$ARG_PROMPT_ID}", "Template Runner") {
        fun createRoute(promptId: String? = null): String =
            if (promptId == null) "prompts/run" else "prompts/run?$ARG_PROMPT_ID=$promptId"
    }

    data object Search : NexusDestination("search?$ARG_TAG_ID={$ARG_TAG_ID}&$ARG_FOLDER_ID={$ARG_FOLDER_ID}", "Search") {
        /** Opens Search pre-filtered by a tag and/or folder (use UNFILED_FOLDER_ID for Unfiled). */
        fun createRoute(tagId: String? = null, folderId: String? = null): String =
            withQuery("search", ARG_TAG_ID to tagId, ARG_FOLDER_ID to folderId)
    }
    data object Favorites : NexusDestination("favorites", "Favorites")
    data object Archive : NexusDestination("archive", "Archive")
    data object Trash : NexusDestination("trash", "Trash")
    data object Vault : NexusDestination("vault", "Private Vault")
    data object Backup : NexusDestination("backup", "Backup & Restore")
    data object Settings : NexusDestination("settings", "Settings")
    data object About : NexusDestination("about", "About")
}

/** Builds "base?a=1&b=2", skipping null values. Values are UUIDs or fixed ids (URL-safe). */
internal fun withQuery(base: String, vararg params: Pair<String, String?>): String {
    val query = params.filter { it.second != null }.joinToString("&") { "${it.first}=${it.second}" }
    return if (query.isEmpty()) base else "$base?$query"
}

/** Bottom-navigation destinations, in display order. */
enum class TopLevelDestination(val destination: NexusDestination, val label: String) {
    HOME(NexusDestination.Home, "Home"),
    FOLDERS(NexusDestination.Folders, "Folders"),
    PROMPTS(NexusDestination.PromptLibrary, "Prompts"),
    SETTINGS(NexusDestination.Settings, "Settings"),
}

object NexusDestinations {
    val all: List<NexusDestination> by lazy {
        listOf(
            NexusDestination.Home,
            NexusDestination.AllNotes,
            NexusDestination.NoteEditor,
            NexusDestination.Folders,
            NexusDestination.FolderDetail,
            NexusDestination.Tags,
            NexusDestination.PromptLibrary,
            NexusDestination.PromptEditor,
            NexusDestination.PromptRunner,
            NexusDestination.Search,
            NexusDestination.Favorites,
            NexusDestination.Archive,
            NexusDestination.Trash,
            NexusDestination.Vault,
            NexusDestination.Backup,
            NexusDestination.Settings,
            NexusDestination.About,
        )
    }

    val topLevelRoutes: Set<String> by lazy {
        TopLevelDestination.entries.map { it.destination.route }.toSet()
    }
}
