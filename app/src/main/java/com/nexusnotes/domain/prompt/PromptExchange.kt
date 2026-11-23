package com.nexusnotes.domain.prompt

/**
 * Prompt export/import file format ("nexus-prompts", version 1). Plain JSON, readable by people
 * and other tools. Import validates everything and never trusts sizes or types from the file.
 */
object PromptExchange {
    const val FORMAT = "nexus-prompts"
    const val VERSION = 1L
    const val MAX_PROMPTS = 5_000
    const val MAX_TEMPLATE_CHARS = 100_000
    const val MAX_FILE_CHARS = 20_000_000

    data class ExportedPrompt(
        val id: String,
        val title: String,
        val template: String,
        /** Built-in category key (e.g. IMAGE_GENERATION) or the custom category's name. */
        val category: String,
        val isCustomCategory: Boolean,
        val isFavorite: Boolean,
        val createdAt: Long,
        val updatedAt: Long,
        val variableDefaults: Map<String, String>,
    )

    data class Bundle(val exportedAt: Long, val prompts: List<ExportedPrompt>, val skippedInvalid: Int)

    fun encode(prompts: List<ExportedPrompt>, exportedAt: Long): String = MiniJson.write(
        linkedMapOf(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to exportedAt,
            "prompts" to prompts.map { p ->
                linkedMapOf(
                    "id" to p.id,
                    "title" to p.title,
                    "template" to p.template,
                    "category" to p.category,
                    "customCategory" to p.isCustomCategory,
                    "favorite" to p.isFavorite,
                    "createdAt" to p.createdAt,
                    "updatedAt" to p.updatedAt,
                    "variables" to p.variableDefaults.map { (k, v) -> linkedMapOf("name" to k, "default" to v) },
                )
            },
        ),
    )

    /** Parses and validates an export file. Individual broken entries are skipped and counted. */
    fun decode(text: String): Result<Bundle> = runCatching {
        if (text.length > MAX_FILE_CHARS) throw IllegalArgumentException("The file is too large.")
        val root = MiniJson.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Not a Nexus prompts file.")
        if (root["format"] != FORMAT) throw IllegalArgumentException("Not a Nexus prompts file.")
        val version = (root["version"] as? Long) ?: throw IllegalArgumentException("Missing file version.")
        if (version > VERSION) throw IllegalArgumentException("This file was made by a newer version of the app.")
        val list = root["prompts"] as? List<*> ?: throw IllegalArgumentException("The file has no prompts list.")
        if (list.size > MAX_PROMPTS) throw IllegalArgumentException("Too many prompts in one file (max $MAX_PROMPTS).")
        var skipped = 0
        val prompts = list.mapNotNull { item ->
            parsePrompt(item).also { if (it == null) skipped++ }
        }
        Bundle((root["exportedAt"] as? Long) ?: 0L, prompts, skipped)
    }

    private fun parsePrompt(item: Any?): ExportedPrompt? {
        val m = item as? Map<*, *> ?: return null
        val template = m["template"] as? String ?: return null
        if (template.length > MAX_TEMPLATE_CHARS) return null
        val title = (m["title"] as? String).orEmpty().take(200)
        if (title.isBlank() && template.isBlank()) return null
        val variables = (m["variables"] as? List<*>).orEmpty().mapNotNull { v ->
            val vm = v as? Map<*, *> ?: return@mapNotNull null
            val name = vm["name"] as? String ?: return@mapNotNull null
            val def = (vm["default"] as? String).orEmpty().take(5_000)
            name.take(100) to def
        }.toMap()
        return ExportedPrompt(
            id = (m["id"] as? String)?.take(100).orEmpty(),
            title = title,
            template = template,
            category = (m["category"] as? String)?.take(100).orEmpty(),
            isCustomCategory = m["customCategory"] as? Boolean ?: false,
            isFavorite = m["favorite"] as? Boolean ?: false,
            createdAt = (m["createdAt"] as? Long) ?: 0L,
            updatedAt = (m["updatedAt"] as? Long) ?: 0L,
            variableDefaults = variables,
        )
    }
}
