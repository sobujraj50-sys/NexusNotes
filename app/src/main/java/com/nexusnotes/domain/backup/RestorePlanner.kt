package com.nexusnotes.domain.backup

enum class RestoreMode {
    /** Keep everything on the phone; add what's new; never overwrite. */
    MERGE,

    /** Delete current notes, folders, tags, prompts and attachments, then load the backup. Vault untouched. */
    REPLACE,
}

/** What is already on the phone (only what the planner needs). */
data class ExistingData(
    val notes: Map<String, NoteRec> = emptyMap(),
    val folderIds: Set<String> = emptySet(),
    val tags: List<TagRec> = emptyList(),
    val prompts: Map<String, PromptRec> = emptyMap(),
    val categoryIds: Set<String> = emptySet(),
    val attachmentIds: Set<String> = emptySet(),
    val attachmentFileNames: Set<String> = emptySet(),
    val versionIds: Set<String> = emptySet(),
)

/** A file to copy from the archive into app storage under [targetName]. */
data class FileCopy(val archivePath: String, val targetName: String)

data class RestoreStats(
    val notesAdded: Int = 0,
    val notesCopied: Int = 0,
    val notesSkipped: Int = 0,
    val promptsAdded: Int = 0,
    val promptsCopied: Int = 0,
    val promptsSkipped: Int = 0,
    val foldersAdded: Int = 0,
    val tagsAdded: Int = 0,
    val attachmentsAdded: Int = 0,
)

data class RestorePlan(
    val mode: RestoreMode,
    val insert: BackupData,
    val files: List<FileCopy>,
    val stats: RestoreStats,
)

/**
 * Decides exactly what a restore writes. Pure (no database), so every rule is unit-tested.
 *
 * MERGE rules:
 *  - Same id and same content (title/body/time) → duplicate, skipped.
 *  - Same id but different content → added as a separate copy with a new id, title + " (restored)".
 *    Nothing on the phone is ever overwritten.
 *  - Tags are matched by name; folders and categories by id.
 *  - Links to folders/tags that don't exist anywhere are dropped (note goes to Unfiled).
 * REPLACE: the backup is loaded as-is (the caller deletes current data inside the same transaction).
 */
class RestorePlanner(private val newId: () -> String) {

    fun plan(backup: BackupData, existing: ExistingData, mode: RestoreMode): RestorePlan {
        val ex = if (mode == RestoreMode.REPLACE) ExistingData() else existing
        var stats = RestoreStats()

        // Folders
        val backupFolderIds = backup.folders.map { it.id }.toSet()
        val allFolders = ex.folderIds + backupFolderIds
        val folders = backup.folders.filter { it.id !in ex.folderIds }.map { f ->
            if (f.parentId != null && f.parentId !in allFolders) f.copy(parentId = null) else f
        }.let { list -> breakCycles(list, ex.folderIds) }
        stats = stats.copy(foldersAdded = folders.size)

        // Tags: by name
        val existingByName = ex.tags.associateBy { it.name }
        val existingTagIds = ex.tags.map { it.id }.toSet()
        val tagIdMap = mutableMapOf<String, String>()
        val tags = mutableListOf<TagRec>()
        backup.tags.forEach { t ->
            val same = existingByName[t.name]
            when {
                same != null -> tagIdMap[t.id] = same.id
                t.id in existingTagIds -> newId().let { id -> tagIdMap[t.id] = id; tags += t.copy(id = id) }
                else -> { tagIdMap[t.id] = t.id; tags += t }
            }
        }
        stats = stats.copy(tagsAdded = tags.size)

        // Notes
        val noteIdMap = mutableMapOf<String, String>()
        val notes = mutableListOf<NoteRec>()
        backup.notes.forEach { n ->
            val folderOk = n.folderId == null || n.folderId in allFolders
            val fixed = if (folderOk) n else n.copy(folderId = null)
            val current = ex.notes[n.id]
            when {
                current == null -> { noteIdMap[n.id] = n.id; notes += fixed; stats = stats.copy(notesAdded = stats.notesAdded + 1) }
                current.title == n.title && current.body == n.body && current.updatedAt == n.updatedAt ->
                    stats = stats.copy(notesSkipped = stats.notesSkipped + 1)
                else -> {
                    val id = newId()
                    noteIdMap[n.id] = id
                    notes += fixed.copy(id = id, title = restoredTitle(n.title))
                    stats = stats.copy(notesCopied = stats.notesCopied + 1)
                }
            }
        }
        val validTagIds = existingTagIds + tags.map { it.id }
        val noteTags = backup.noteTags.mapNotNull { link ->
            val note = noteIdMap[link.noteId] ?: return@mapNotNull null
            val tag = tagIdMap[link.tagId]?.takeIf { it in validTagIds } ?: return@mapNotNull null
            NoteTagRec(note, tag)
        }.distinct()

        // Attachments follow their note
        val usedNames = ex.attachmentFileNames.toMutableSet()
        val usedIds = ex.attachmentIds.toMutableSet()
        val attachments = mutableListOf<AttachmentRec>()
        val files = mutableListOf<FileCopy>()
        backup.attachments.forEach { a ->
            val note = noteIdMap[a.noteId] ?: return@forEach
            // A clash on either the id or the stored file name gets a fresh id and file name.
            val clash = a.id in usedIds || a.fileName in usedNames
            val id = if (clash) newId() else a.id
            val ext = a.fileName.substringAfterLast('.', "")
            val name = if (clash) (if (ext.isEmpty()) id else "$id.$ext") else a.fileName
            usedIds += id
            usedNames += name
            attachments += a.copy(id = id, noteId = note, fileName = name)
            files += FileCopy(BackupPaths.ATTACHMENTS + a.fileName, name)
        }
        stats = stats.copy(attachmentsAdded = attachments.size)

        // Prompts
        val categories = backup.categories.filter { it.id !in ex.categoryIds }
        val promptIdMap = mutableMapOf<String, String>()
        val prompts = mutableListOf<PromptRec>()
        backup.prompts.forEach { p ->
            val current = ex.prompts[p.id]
            when {
                current == null -> { promptIdMap[p.id] = p.id; prompts += p; stats = stats.copy(promptsAdded = stats.promptsAdded + 1) }
                current.title == p.title && current.template == p.template && current.updatedAt == p.updatedAt ->
                    stats = stats.copy(promptsSkipped = stats.promptsSkipped + 1)
                else -> {
                    val id = newId()
                    promptIdMap[p.id] = id
                    prompts += p.copy(id = id, title = restoredTitle(p.title))
                    stats = stats.copy(promptsCopied = stats.promptsCopied + 1)
                }
            }
        }
        val variables = backup.variables.mapNotNull { v -> promptIdMap[v.promptId]?.let { v.copy(promptId = it) } }
            .distinctBy { it.promptId to it.name }
        val usedVersionIds = ex.versionIds.toMutableSet()
        val versions = backup.versions.mapNotNull { v ->
            val prompt = promptIdMap[v.promptId] ?: return@mapNotNull null
            val id = if (v.id in usedVersionIds || prompt != v.promptId) newId() else v.id
            usedVersionIds += id
            v.copy(id = id, promptId = prompt)
        }

        return RestorePlan(
            mode = mode,
            insert = BackupData(
                notes = notes,
                folders = folders,
                tags = tags,
                noteTags = noteTags,
                prompts = prompts,
                categories = categories,
                variables = variables,
                versions = versions,
                attachments = attachments,
            ),
            files = files,
            stats = stats,
        )
    }

    /** A damaged backup could contain a parent loop; such folders are moved to the top level. */
    private fun breakCycles(folders: List<FolderRec>, existing: Set<String>): List<FolderRec> {
        val byId = folders.associateBy { it.id }
        return folders.map { f ->
            val seen = mutableSetOf(f.id)
            var p = f.parentId
            var loop = false
            while (p != null && p !in existing) {
                if (!seen.add(p)) { loop = true; break }
                p = byId[p]?.parentId
            }
            if (loop) f.copy(parentId = null) else f
        }
    }

    companion object {
        fun restoredTitle(title: String): String = if (title.isBlank()) "(restored)" else "$title (restored)"
    }
}
