package com.nexusnotes.domain.backup

import com.nexusnotes.domain.prompt.MiniJson

/** A backup file that is damaged, from another app, or from a newer app version. Message is user-facing. */
class BackupInvalidException(message: String) : Exception(message)

// One record type per table. Plain values only, so a backup never depends on Room classes.
data class NoteRec(
    val id: String, val title: String, val body: String, val bodyFormatVersion: Int, val colorIndex: Int?,
    val isPinned: Boolean, val isFavorite: Boolean, val isArchived: Boolean, val deletedAt: Long?,
    val createdAt: Long, val updatedAt: Long, val folderId: String?,
)
data class FolderRec(val id: String, val name: String, val parentId: String?, val colorIndex: Int?, val createdAt: Long)
data class TagRec(val id: String, val name: String)
data class NoteTagRec(val noteId: String, val tagId: String)
data class PromptRec(
    val id: String, val title: String, val template: String, val categoryKey: String,
    val isFavorite: Boolean, val createdAt: Long, val updatedAt: Long,
)
data class CategoryRec(val id: String, val name: String, val createdAt: Long)
data class VariableRec(val promptId: String, val name: String, val defaultValue: String)
data class VersionRec(val id: String, val promptId: String, val title: String, val template: String, val createdAt: Long)
data class AttachmentRec(
    val id: String, val noteId: String, val kind: String, val fileName: String, val displayName: String,
    val mimeType: String, val sizeBytes: Long, val sha256: String, val durationMs: Long?, val createdAt: Long,
)
data class TemplateRec(val id: String, val name: String, val title: String, val body: String, val createdAt: Long)
data class ReminderRec(val noteId: String, val triggerAt: Long, val repeat: String, val anchorAt: Long, val createdAt: Long)
/** Vault rows stay encrypted (Base64 of the AES-GCM payload). */
data class VaultItemRec(val id: String, val payload: String, val createdAt: Long, val updatedAt: Long)
data class VaultAttachmentRec(val id: String, val itemId: String, val fileName: String, val payload: String, val createdAt: Long)
/** The vault key, encrypted with the backup password (never the device key, which can't leave the phone). */
data class VaultKeyRec(val salt: String, val iterations: Int, val wrapped: String)

data class BackupData(
    val notes: List<NoteRec> = emptyList(),
    val folders: List<FolderRec> = emptyList(),
    val tags: List<TagRec> = emptyList(),
    val noteTags: List<NoteTagRec> = emptyList(),
    val prompts: List<PromptRec> = emptyList(),
    val categories: List<CategoryRec> = emptyList(),
    val variables: List<VariableRec> = emptyList(),
    val versions: List<VersionRec> = emptyList(),
    val attachments: List<AttachmentRec> = emptyList(),
    val vaultItems: List<VaultItemRec> = emptyList(),
    val vaultAttachments: List<VaultAttachmentRec> = emptyList(),
    val vaultKey: VaultKeyRec? = null,
    val templates: List<TemplateRec> = emptyList(),
    val reminders: List<ReminderRec> = emptyList(),
) {
    val includesVault: Boolean get() = vaultKey != null

    fun counts(): Map<String, Long> = linkedMapOf(
        "notes" to notes.size.toLong(),
        "folders" to folders.size.toLong(),
        "tags" to tags.size.toLong(),
        "prompts" to prompts.size.toLong(),
        "attachments" to attachments.size.toLong(),
        "vaultNotes" to vaultItems.size.toLong(),
        "vaultFiles" to vaultAttachments.size.toLong(),
        "templates" to templates.size.toLong(),
        "reminders" to reminders.size.toLong(),
    )
}

data class FileHash(val path: String, val size: Long, val sha256: String)

data class Manifest(
    val formatVersion: Int,
    val appVersion: String,
    val dbVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Long>,
    val files: List<FileHash>,
    val includesVault: Boolean,
)

/** JSON encoding of the backup. Strict on read: every required field must be present with the right type. */
object BackupJson {
    const val FORMAT = "nexus-notes-backup"
    const val FORMAT_VERSION = 1

    fun encodeData(d: BackupData): String = MiniJson.write(
        mapOf(
            "notes" to d.notes.map {
                mapOf(
                    "id" to it.id, "title" to it.title, "body" to it.body, "bodyFormatVersion" to it.bodyFormatVersion,
                    "colorIndex" to it.colorIndex, "isPinned" to it.isPinned, "isFavorite" to it.isFavorite,
                    "isArchived" to it.isArchived, "deletedAt" to it.deletedAt, "createdAt" to it.createdAt,
                    "updatedAt" to it.updatedAt, "folderId" to it.folderId,
                )
            },
            "folders" to d.folders.map { mapOf("id" to it.id, "name" to it.name, "parentId" to it.parentId, "colorIndex" to it.colorIndex, "createdAt" to it.createdAt) },
            "tags" to d.tags.map { mapOf("id" to it.id, "name" to it.name) },
            "noteTags" to d.noteTags.map { mapOf("noteId" to it.noteId, "tagId" to it.tagId) },
            "prompts" to d.prompts.map {
                mapOf(
                    "id" to it.id, "title" to it.title, "template" to it.template, "categoryKey" to it.categoryKey,
                    "isFavorite" to it.isFavorite, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt,
                )
            },
            "categories" to d.categories.map { mapOf("id" to it.id, "name" to it.name, "createdAt" to it.createdAt) },
            "variables" to d.variables.map { mapOf("promptId" to it.promptId, "name" to it.name, "defaultValue" to it.defaultValue) },
            "versions" to d.versions.map { mapOf("id" to it.id, "promptId" to it.promptId, "title" to it.title, "template" to it.template, "createdAt" to it.createdAt) },
            "attachments" to d.attachments.map {
                mapOf(
                    "id" to it.id, "noteId" to it.noteId, "kind" to it.kind, "fileName" to it.fileName,
                    "displayName" to it.displayName, "mimeType" to it.mimeType, "sizeBytes" to it.sizeBytes,
                    "sha256" to it.sha256, "durationMs" to it.durationMs, "createdAt" to it.createdAt,
                )
            },
            "vaultItems" to d.vaultItems.map { mapOf("id" to it.id, "payload" to it.payload, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt) },
            "vaultAttachments" to d.vaultAttachments.map {
                mapOf("id" to it.id, "itemId" to it.itemId, "fileName" to it.fileName, "payload" to it.payload, "createdAt" to it.createdAt)
            },
            "templates" to d.templates.map { mapOf("id" to it.id, "name" to it.name, "title" to it.title, "body" to it.body, "createdAt" to it.createdAt) },
            "reminders" to d.reminders.map {
                mapOf("noteId" to it.noteId, "triggerAt" to it.triggerAt, "repeat" to it.repeat, "anchorAt" to it.anchorAt, "createdAt" to it.createdAt)
            },
            "vaultKey" to d.vaultKey?.let { mapOf("salt" to it.salt, "iterations" to it.iterations, "wrapped" to it.wrapped) },
        ),
    )

    fun decodeData(json: String): BackupData {
        val root = parseObject(json, "data")
        return BackupData(
            notes = root.list("notes") {
                NoteRec(
                    it.str("id"), it.str("title"), it.str("body"), it.int("bodyFormatVersion"), it.intOrNull("colorIndex"),
                    it.bool("isPinned"), it.bool("isFavorite"), it.bool("isArchived"), it.longOrNull("deletedAt"),
                    it.long("createdAt"), it.long("updatedAt"), it.strOrNull("folderId"),
                )
            },
            folders = root.list("folders") { FolderRec(it.str("id"), it.str("name"), it.strOrNull("parentId"), it.intOrNull("colorIndex"), it.long("createdAt")) },
            tags = root.list("tags") { TagRec(it.str("id"), it.str("name")) },
            noteTags = root.list("noteTags") { NoteTagRec(it.str("noteId"), it.str("tagId")) },
            prompts = root.list("prompts") {
                PromptRec(it.str("id"), it.str("title"), it.str("template"), it.str("categoryKey"), it.bool("isFavorite"), it.long("createdAt"), it.long("updatedAt"))
            },
            categories = root.list("categories") { CategoryRec(it.str("id"), it.str("name"), it.long("createdAt")) },
            variables = root.list("variables") { VariableRec(it.str("promptId"), it.str("name"), it.str("defaultValue")) },
            versions = root.list("versions") { VersionRec(it.str("id"), it.str("promptId"), it.str("title"), it.str("template"), it.long("createdAt")) },
            attachments = root.list("attachments") {
                AttachmentRec(
                    it.str("id"), it.str("noteId"), it.str("kind"), it.str("fileName"), it.str("displayName"), it.str("mimeType"),
                    it.long("sizeBytes"), it.str("sha256"), it.longOrNull("durationMs"), it.long("createdAt"),
                )
            },
            vaultItems = root.list("vaultItems") { VaultItemRec(it.str("id"), it.str("payload"), it.long("createdAt"), it.long("updatedAt")) },
            vaultAttachments = root.list("vaultAttachments") {
                VaultAttachmentRec(it.str("id"), it.str("itemId"), it.str("fileName"), it.str("payload"), it.long("createdAt"))
            },
            templates = root.list("templates") { TemplateRec(it.str("id"), it.str("name"), it.str("title"), it.str("body"), it.long("createdAt")) },
            reminders = root.list("reminders") {
                ReminderRec(it.str("noteId"), it.long("triggerAt"), it.str("repeat"), it.long("anchorAt"), it.long("createdAt"))
            },
            vaultKey = (root["vaultKey"] as? Map<*, *>)?.let {
                @Suppress("UNCHECKED_CAST")
                val m = it as Map<String, Any?>
                VaultKeyRec(m.str("salt"), m.int("iterations"), m.str("wrapped"))
            },
        )
    }

    fun encodeManifest(m: Manifest): String = MiniJson.write(
        mapOf(
            "format" to FORMAT,
            "formatVersion" to m.formatVersion,
            "appVersion" to m.appVersion,
            "dbVersion" to m.dbVersion,
            "createdAt" to m.createdAt,
            "includesVault" to m.includesVault,
            "counts" to m.counts,
            "files" to m.files.map { mapOf("path" to it.path, "size" to it.size, "sha256" to it.sha256) },
        ),
    )

    fun decodeManifest(json: String): Manifest {
        val root = parseObject(json, "manifest")
        if (root["format"] != FORMAT) throw BackupInvalidException("This file is not a Nexus Notes backup.")
        val version = root.int("formatVersion")
        if (version > FORMAT_VERSION) {
            throw BackupInvalidException("This backup was made by a newer version of Nexus Notes. Update the app first.")
        }
        if (version < 1) throw BackupInvalidException("Unknown backup version.")
        @Suppress("UNCHECKED_CAST")
        val counts = (root["counts"] as? Map<String, Any?>)?.mapValues { (it.value as? Long) ?: 0L } ?: emptyMap()
        return Manifest(
            formatVersion = version,
            appVersion = root.str("appVersion"),
            dbVersion = root.int("dbVersion"),
            createdAt = root.long("createdAt"),
            counts = counts,
            files = root.list("files") { FileHash(it.str("path"), it.long("size"), it.str("sha256")) },
            includesVault = root.bool("includesVault"),
        )
    }

    // ---- strict readers ----

    private fun parseObject(json: String, what: String): Map<String, Any?> {
        val parsed = try {
            MiniJson.parse(json)
        } catch (e: Exception) {
            throw BackupInvalidException("The backup's $what is damaged.")
        }
        @Suppress("UNCHECKED_CAST")
        return parsed as? Map<String, Any?> ?: throw BackupInvalidException("The backup's $what is damaged.")
    }

    private fun bad(key: String): Nothing = throw BackupInvalidException("The backup is damaged (field “$key”).")

    private fun Map<String, Any?>.str(k: String): String = this[k] as? String ?: bad(k)
    private fun Map<String, Any?>.strOrNull(k: String): String? = when (val v = this[k]) {
        null -> null
        is String -> v
        else -> bad(k)
    }
    private fun Map<String, Any?>.long(k: String): Long = this[k] as? Long ?: bad(k)
    private fun Map<String, Any?>.longOrNull(k: String): Long? = when (val v = this[k]) {
        null -> null
        is Long -> v
        else -> bad(k)
    }
    private fun Map<String, Any?>.int(k: String): Int = long(k).let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else bad(k) }
    private fun Map<String, Any?>.intOrNull(k: String): Int? = longOrNull(k)?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else bad(k) }
    private fun Map<String, Any?>.bool(k: String): Boolean = this[k] as? Boolean ?: bad(k)

    private fun <T> Map<String, Any?>.list(k: String, item: (Map<String, Any?>) -> T): List<T> {
        val raw = this[k] ?: return emptyList()
        val list = raw as? List<*> ?: bad(k)
        return list.map {
            @Suppress("UNCHECKED_CAST")
            item(it as? Map<String, Any?> ?: bad(k))
        }
    }
}
