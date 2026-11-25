package com.nexusnotes.domain.backup

import com.nexusnotes.domain.prompt.MiniJson
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    MARKDOWN("Markdown (.md files in a ZIP)", "zip", "application/zip"),
    TEXT("Plain text (.txt files in a ZIP)", "zip", "application/zip"),
    JSON("JSON (one file)", "json", "application/json"),
    PDF("PDF (one document)", "pdf", "application/pdf"),
}

/** A note plus its organisation, ready to export. Vault notes are never exported. */
data class ExportNote(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String>,
    val folderPath: String?,
    val isArchived: Boolean,
    val attachmentNames: List<String>,
)

object NoteExport {
    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun isoTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = iso.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun yamlString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    fun toMarkdown(n: ExportNote, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("---\n")
        append("title: ").append(yamlString(n.title)).append('\n')
        append("created: ").append(isoTime(n.createdAt, zone)).append('\n')
        append("updated: ").append(isoTime(n.updatedAt, zone)).append('\n')
        if (n.tags.isNotEmpty()) append("tags: [").append(n.tags.joinToString(", ") { yamlString(it) }).append("]\n")
        n.folderPath?.let { append("folder: ").append(yamlString(it)).append('\n') }
        if (n.isArchived) append("archived: true\n")
        append("---\n\n")
        if (n.title.isNotBlank()) append("# ").append(n.title).append("\n\n")
        append(n.body)
        if (!n.body.endsWith("\n")) append('\n')
        if (n.attachmentNames.isNotEmpty()) {
            append("\n---\nAttachments (in the attachments folder):\n")
            n.attachmentNames.forEach { append("- ").append(it).append('\n') }
        }
    }

    fun toText(n: ExportNote, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val title = n.title.ifBlank { "Untitled" }
        append(title).append('\n').append("=".repeat(title.length.coerceIn(3, 60))).append('\n')
        append("Updated: ").append(isoTime(n.updatedAt, zone)).append('\n')
        if (n.tags.isNotEmpty()) append("Tags: ").append(n.tags.joinToString(", ")).append('\n')
        n.folderPath?.let { append("Folder: ").append(it).append('\n') }
        append('\n').append(n.body)
        if (!n.body.endsWith("\n")) append('\n')
    }

    fun toJson(notes: List<ExportNote>, exportedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String = MiniJson.write(
        mapOf(
            "app" to "Nexus Notes",
            "exportedAt" to isoTime(exportedAt, zone),
            "notes" to notes.map {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "body" to it.body,
                    "created" to isoTime(it.createdAt, zone),
                    "updated" to isoTime(it.updatedAt, zone),
                    "tags" to it.tags,
                    "folder" to it.folderPath,
                    "archived" to it.isArchived,
                    "attachments" to it.attachmentNames,
                )
            },
        ),
    )

    /** Safe, unique file name for a note ("Shopping list.md", "Shopping list (2).md"). */
    fun fileName(title: String, extension: String, used: MutableSet<String>): String {
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(80).ifBlank { "Untitled" }
        var candidate = "$clean.$extension"
        var i = 2
        while (!used.add(candidate.lowercase())) {
            candidate = "$clean ($i).$extension"
            i++
        }
        return candidate
    }
}
