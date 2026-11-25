package com.nexusnotes.domain.ai

/**
 * Optional AI tools — architecture without a network connection.
 *
 * Nexus Notes has no INTERNET permission and embeds no API keys. An AI action builds a clear,
 * ready-to-use prompt from the note and hands it to an AI app the user chooses (share sheet) or to
 * the clipboard. Nothing leaves the phone unless the user sends it, and the user sees the exact
 * text first. Off by default (Settings › AI tools).
 *
 * A future direct provider (user's own key, explicit consent per request) would implement
 * [AiProvider]; none is shipped, and the UI does not pretend otherwise.
 */
enum class AiAction(val label: String, val needsLanguage: Boolean = false) {
    SUMMARIZE("Summarize"),
    REWRITE("Rewrite more clearly"),
    GRAMMAR("Fix grammar and spelling"),
    TRANSLATE("Translate", needsLanguage = true),
    TITLE("Suggest titles"),
    ORGANIZE("Organize into sections"),
    ENHANCE_PROMPT("Improve as an AI prompt"),
}

interface AiProvider {
    val name: String
    suspend fun run(prompt: String): Result<String>
}

object AiPrompts {
    /** Notes longer than this are cut (and the prompt says so), to fit chat apps' limits. */
    const val MAX_NOTE_CHARS = 12_000

    fun build(action: AiAction, title: String, body: String, language: String? = null): String {
        val note = buildString {
            if (title.isNotBlank()) append("Title: ").append(title.trim()).append("\n\n")
            append(body.trim())
        }
        val cut = note.length > MAX_NOTE_CHARS
        val text = if (cut) note.take(MAX_NOTE_CHARS) else note
        val task = when (action) {
            AiAction.SUMMARIZE -> "Summarize the note below in 3–5 short bullet points. Keep names, numbers and dates exact."
            AiAction.REWRITE -> "Rewrite the note below so it is clearer and easier to read. Keep the meaning, facts and language the same. Return only the rewritten note."
            AiAction.GRAMMAR -> "Correct grammar, spelling and punctuation in the note below. Do not change the meaning or style. Keep Markdown formatting. Return only the corrected note."
            AiAction.TRANSLATE -> "Translate the note below into ${language?.trim().takeUnless { it.isNullOrEmpty() } ?: "English"}. Keep Markdown formatting, names and numbers. Return only the translation."
            AiAction.TITLE -> "Suggest 5 short, specific titles for the note below. Return them as a numbered list."
            AiAction.ORGANIZE -> "Reorganize the note below into clear Markdown sections with headings and bullet points. Do not drop any information. Return only the organized note."
            AiAction.ENHANCE_PROMPT -> "The text below is a prompt for an AI model. Improve it: make the goal, context, constraints and output format explicit, and keep any {{variables}} unchanged. Return only the improved prompt."
        }
        return buildString {
            append(task).append('\n')
            if (cut) append("(Only the first $MAX_NOTE_CHARS characters of the note are included.)\n")
            append("\n---\n").append(text).append("\n---")
        }
    }
}
