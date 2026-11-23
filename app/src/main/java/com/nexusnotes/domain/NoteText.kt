package com.nexusnotes.domain

/** Pure text helpers for notes. No Android dependencies so they are unit-tested on the JVM. */
object NoteText {
    private val whitespace = Regex("\\s+")
    private const val UNTITLED = "Untitled"

    /** Words separated by whitespace. Works for English and Bangla (both use spaces between words). */
    fun wordCount(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.split(whitespace).size
    }

    /** Unicode code points, so an emoji counts as 1 rather than 2. */
    fun characterCount(text: String): Int = text.codePointCount(0, text.length)

    /** Title for lists: the title, else the first non-blank line of the body, else "Untitled". */
    fun displayTitle(title: String, body: String): String {
        val t = title.trim()
        if (t.isNotEmpty()) return t
        val firstLine = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return firstLine?.take(80) ?: UNTITLED
    }

    /** One-paragraph preview of the body for list cards. */
    fun preview(body: String, maxChars: Int = 180): String =
        body.trim().replace(whitespace, " ").take(maxChars)
}
