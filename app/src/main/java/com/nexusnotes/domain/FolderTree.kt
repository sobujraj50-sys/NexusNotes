package com.nexusnotes.domain

import com.nexusnotes.domain.model.Folder
import com.nexusnotes.domain.model.FolderNode

/** Pure folder-tree rules. Guarantees the folder structure can never form a loop. */
object FolderTree {

    const val MAX_DEPTH = 8
    const val MAX_NAME_LENGTH = 60

    /** Folders in display order: each parent followed by its children (A–Z), with depth. */
    fun flatten(folders: List<Folder>, counts: Map<String, Int> = emptyMap()): List<FolderNode> {
        val byParent = folders.groupBy { it.parentId }
        val result = ArrayList<FolderNode>(folders.size)
        val visited = HashSet<String>()

        fun visit(parentId: String?, depth: Int) {
            byParent[parentId].orEmpty()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .forEach { folder ->
                    if (visited.add(folder.id)) {
                        result += FolderNode(folder, depth, counts[folder.id] ?: 0)
                        visit(folder.id, depth + 1)
                    }
                }
        }
        visit(null, 0)
        // Folders whose parent no longer exists (or bad data) are shown at top level, never hidden.
        folders.filter { it.id !in visited }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .forEach { orphan ->
                if (visited.add(orphan.id)) {
                    result += FolderNode(orphan, 0, counts[orphan.id] ?: 0)
                    visit(orphan.id, 1)
                }
            }
        return result
    }

    /** [folderId] and every folder below it. */
    fun subtreeIds(folders: List<Folder>, folderId: String): List<String> {
        val byParent = folders.groupBy { it.parentId }
        val out = ArrayList<String>()
        val stack = ArrayDeque(listOf(folderId))
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!seen.add(id)) continue
            out += id
            byParent[id].orEmpty().forEach { stack.addLast(it.id) }
        }
        return out
    }

    /** Depth of [folderId] (0 = top level), following parents safely. */
    fun depthOf(folders: List<Folder>, folderId: String?): Int {
        val byId = folders.associateBy { it.id }
        var depth = -1
        var current = folderId
        val seen = HashSet<String>()
        while (current != null && seen.add(current)) {
            depth++
            current = byId[current]?.parentId
        }
        return depth
    }

    /** Height of the subtree under [folderId] (a folder with no children has height 0). */
    private fun subtreeHeight(folders: List<Folder>, folderId: String): Int {
        val byParent = folders.groupBy { it.parentId }
        fun height(id: String, seen: Set<String>): Int {
            val children = byParent[id].orEmpty().filter { it.id !in seen }
            return if (children.isEmpty()) 0 else 1 + children.maxOf { height(it.id, seen + it.id) }
        }
        return height(folderId, setOf(folderId))
    }

    sealed interface MoveCheck {
        data object Ok : MoveCheck
        data object IntoItself : MoveCheck
        data object IntoOwnSubfolder : MoveCheck
        data object TooDeep : MoveCheck
    }

    /** Whether [folderId] may be moved under [newParentId] (null = top level). */
    fun checkMove(folders: List<Folder>, folderId: String, newParentId: String?): MoveCheck {
        if (newParentId == null) return MoveCheck.Ok
        if (newParentId == folderId) return MoveCheck.IntoItself
        if (newParentId in subtreeIds(folders, folderId)) return MoveCheck.IntoOwnSubfolder
        val newDepth = depthOf(folders, newParentId) + 1
        if (newDepth + subtreeHeight(folders, folderId) >= MAX_DEPTH) return MoveCheck.TooDeep
        return MoveCheck.Ok
    }

    /** Whether a new folder can be created under [parentId]. */
    fun canCreateUnder(folders: List<Folder>, parentId: String?): Boolean =
        parentId == null || depthOf(folders, parentId) + 1 < MAX_DEPTH

    /** Cleans a user-entered folder or tag name; returns null if nothing usable is left. */
    fun cleanName(raw: String, maxLength: Int = MAX_NAME_LENGTH): String? {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim().take(maxLength)
        return cleaned.ifEmpty { null }
    }
}
