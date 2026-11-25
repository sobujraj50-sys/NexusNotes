package com.nexusnotes.domain.template

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NoteTemplate(
    val id: String,
    val name: String,
    val title: String,
    val body: String,
    val builtIn: Boolean,
)

/**
 * Note templates. Placeholders filled when a note is created:
 * {{date}} 2026-11-25 · {{time}} 21:40 · {{weekday}} Wednesday · {{datetime}} both.
 */
object NoteTemplates {
    val builtIns: List<NoteTemplate> = listOf(
        NoteTemplate("builtin:checklist", "Checklist", "Checklist", "- [ ] \n- [ ] \n- [ ] \n", true),
        NoteTemplate(
            "builtin:daily", "Daily journal", "{{weekday}}, {{date}}",
            "## Today I'm grateful for\n- \n\n## Top 3 tasks\n- [ ] \n- [ ] \n- [ ] \n\n## Notes\n", true,
        ),
        NoteTemplate(
            "builtin:meeting", "Meeting notes", "Meeting — {{date}}",
            "**When:** {{datetime}}\n**Who:** \n\n## Agenda\n1. \n\n## Notes\n\n## Decisions\n- \n\n## Action items\n- [ ] \n", true,
        ),
        NoteTemplate("builtin:shopping", "Shopping list", "Shopping {{date}}", "- [ ] \n- [ ] \n- [ ] \n", true),
        NoteTemplate(
            "builtin:project", "Project plan", "Project: ",
            "## Goal\n\n## Milestones\n- [ ] \n- [ ] \n\n## Risks\n- \n\n## Links\n- \n", true,
        ),
        NoteTemplate(
            "builtin:idea", "Idea", "Idea: ",
            "**Problem:** \n\n**Idea:** \n\n**Why now:** \n\n**Next step:** \n", true,
        ),
        NoteTemplate(
            "builtin:prompt", "AI prompt draft", "Prompt — ",
            "**Goal:** \n\n**Context:** \n\n**Output format:** \n\n**Constraints:** \n- \n", true,
        ),
    )

    private val PLACEHOLDER = Regex("""\{\{\s*(date|time|weekday|datetime)\s*\}\}""")

    fun fill(text: String, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        return PLACEHOLDER.replace(text) { m ->
            when (m.groupValues[1]) {
                "date" -> DateTimeFormatter.ISO_LOCAL_DATE.format(t)
                "time" -> DateTimeFormatter.ofPattern("HH:mm", locale).format(t)
                "weekday" -> DateTimeFormatter.ofPattern("EEEE", locale).format(t)
                else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale).format(t)
            }
        }
    }
}
