package com.nexusnotes.domain.markdown

/** Text plus a normalized selection (start <= end). */
data class TextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart) {
    init {
        require(selectionStart in 0..text.length) { "selectionStart out of range" }
        require(selectionEnd in selectionStart..text.length) { "selectionEnd out of range" }
    }
}

enum class InlineStyle(val open: String, val close: String) {
    BOLD("**", "**"),
    ITALIC("*", "*"),
    STRIKETHROUGH("~~", "~~"),
    UNDERLINE("<u>", "</u>"),
    INLINE_CODE("`", "`"),
}

enum class LineStyle { H1, H2, H3, BULLET, NUMBERED, TASK, QUOTE }

enum class FormatAction {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, INLINE_CODE,
    H1, H2, H3, BULLET, NUMBERED, TASK, QUOTE,
    CODE_BLOCK, LINK, TABLE,
}

/**
 * Pure Markdown editing operations used by the formatting toolbar.
 * Every operation only inserts or removes Markdown syntax; user text is never rewritten.
 */
object MarkdownFormatter {

    fun apply(edit: TextEdit, action: FormatAction): TextEdit = when (action) {
        FormatAction.BOLD -> toggleInline(edit, InlineStyle.BOLD)
        FormatAction.ITALIC -> toggleInline(edit, InlineStyle.ITALIC)
        FormatAction.UNDERLINE -> toggleInline(edit, InlineStyle.UNDERLINE)
        FormatAction.STRIKETHROUGH -> toggleInline(edit, InlineStyle.STRIKETHROUGH)
        FormatAction.INLINE_CODE -> toggleInline(edit, InlineStyle.INLINE_CODE)
        FormatAction.H1 -> toggleLineStyle(edit, LineStyle.H1)
        FormatAction.H2 -> toggleLineStyle(edit, LineStyle.H2)
        FormatAction.H3 -> toggleLineStyle(edit, LineStyle.H3)
        FormatAction.BULLET -> toggleLineStyle(edit, LineStyle.BULLET)
        FormatAction.NUMBERED -> toggleLineStyle(edit, LineStyle.NUMBERED)
        FormatAction.TASK -> toggleLineStyle(edit, LineStyle.TASK)
        FormatAction.QUOTE -> toggleLineStyle(edit, LineStyle.QUOTE)
        FormatAction.CODE_BLOCK -> insertCodeBlock(edit)
        FormatAction.LINK -> insertLink(edit)
        FormatAction.TABLE -> insertTable(edit)
    }

    // ---------------- Inline styles ----------------

    fun toggleInline(edit: TextEdit, style: InlineStyle): TextEdit {
        val t = edit.text
        val s = edit.selectionStart
        val e = edit.selectionEnd
        val o = style.open
        val c = style.close

        // 1) Markers sit just outside the selection: remove them.
        if (isWrappedOutside(t, s, e, style)) {
            val text = t.substring(0, s - o.length) + t.substring(s, e) + t.substring(e + c.length)
            return TextEdit(text, s - o.length, e - o.length)
        }
        // 2) The selection itself includes the markers: remove them.
        if (isWrappedInside(t.substring(s, e), style)) {
            val inner = t.substring(s + o.length, e - c.length)
            val text = t.substring(0, s) + inner + t.substring(e)
            return TextEdit(text, s, s + inner.length)
        }
        // 3) Wrap. With no selection the cursor lands between the markers.
        val text = t.substring(0, s) + o + t.substring(s, e) + c + t.substring(e)
        return TextEdit(text, s + o.length, e + o.length)
    }

    private fun isWrappedOutside(t: String, s: Int, e: Int, style: InlineStyle): Boolean {
        val o = style.open
        val c = style.close
        if (s < o.length || e + c.length > t.length) return false
        if (!t.regionMatches(s - o.length, o, 0, o.length, ignoreCase = true)) return false
        if (!t.regionMatches(e, c, 0, c.length, ignoreCase = true)) return false
        return when (style) {
            InlineStyle.ITALIC -> starRunBefore(t, s) % 2 == 1 && starRunAfter(t, e) % 2 == 1
            InlineStyle.BOLD -> starRunBefore(t, s) >= 2 && starRunAfter(t, e) >= 2
            else -> true
        }
    }

    private fun isWrappedInside(selected: String, style: InlineStyle): Boolean {
        val o = style.open
        val c = style.close
        if (selected.length < o.length + c.length) return false
        if (!selected.startsWith(o, ignoreCase = true) || !selected.endsWith(c, ignoreCase = true)) return false
        return when (style) {
            InlineStyle.ITALIC -> leadingStars(selected) % 2 == 1 && trailingStars(selected) % 2 == 1
            InlineStyle.BOLD -> leadingStars(selected) >= 2 && trailingStars(selected) >= 2
            else -> true
        }
    }

    private fun starRunBefore(t: String, index: Int): Int {
        var n = 0
        var i = index - 1
        while (i >= 0 && t[i] == '*') { n++; i-- }
        return n
    }

    private fun starRunAfter(t: String, index: Int): Int {
        var n = 0
        var i = index
        while (i < t.length && t[i] == '*') { n++; i++ }
        return n
    }

    private fun leadingStars(s: String) = s.takeWhile { it == '*' }.length
    private fun trailingStars(s: String) = s.takeLastWhile { it == '*' }.length

    // ---------------- Line styles ----------------

    private val headingRegex = Regex("^(#{1,6}) ")
    private val taskRegex = Regex("^[-*+] \\[[ xX]] ")
    private val bulletRegex = Regex("^[-*+] ")
    private val numberedRegex = Regex("^\\d+[.)] ")
    private val quoteRegex = Regex("^> ?")

    fun toggleLineStyle(edit: TextEdit, style: LineStyle): TextEdit {
        val t = edit.text
        val lineStart = t.lastIndexOf('\n', edit.selectionStart - 1) + 1
        var lineEnd = t.indexOf('\n', edit.selectionEnd).let { if (it == -1) t.length else it }
        // A selection ending at the very start of a line does not include that line.
        if (edit.selectionEnd > edit.selectionStart && edit.selectionEnd > lineStart &&
            t[edit.selectionEnd - 1] == '\n'
        ) {
            lineEnd = edit.selectionEnd - 1
        }
        val lines = t.substring(lineStart, lineEnd).split('\n')
        val contentLines = lines.filter { it.isNotBlank() }.ifEmpty { lines }
        val removing = contentLines.all { hasStyle(it, style) }

        var number = 0
        val newLines = lines.map { line ->
            if (lines.size > 1 && line.isBlank()) return@map line
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val rest = line.substring(indent.length)
            if (removing) {
                indent + stripPrefix(rest)
            } else {
                number++
                indent + prefixFor(style, number) + stripPrefix(rest)
            }
        }
        val newBlock = newLines.joinToString("\n")
        val text = t.substring(0, lineStart) + newBlock + t.substring(lineEnd)

        if (lines.size == 1) {
            val delta = newBlock.length - (lineEnd - lineStart)
            val lineEndNew = lineStart + newBlock.length
            val prefixEnd = lineStart + (newBlock.length - newBlock.trimStart().length) +
                linePrefixLength(newBlock.trimStart())
            val start = (edit.selectionStart + delta).coerceIn(minOf(prefixEnd, lineEndNew), lineEndNew)
            val end = (edit.selectionEnd + delta).coerceIn(start, lineEndNew)
            return TextEdit(text, start, end)
        }
        return TextEdit(text, lineStart, lineStart + newBlock.length)
    }

    private fun prefixFor(style: LineStyle, number: Int): String = when (style) {
        LineStyle.H1 -> "# "
        LineStyle.H2 -> "## "
        LineStyle.H3 -> "### "
        LineStyle.BULLET -> "- "
        LineStyle.NUMBERED -> "$number. "
        LineStyle.TASK -> "- [ ] "
        LineStyle.QUOTE -> "> "
    }

    private fun hasStyle(line: String, style: LineStyle): Boolean {
        val rest = line.trimStart(' ', '\t')
        return when (style) {
            LineStyle.H1 -> headingRegex.find(rest)?.groupValues?.get(1)?.length == 1
            LineStyle.H2 -> headingRegex.find(rest)?.groupValues?.get(1)?.length == 2
            LineStyle.H3 -> headingRegex.find(rest)?.groupValues?.get(1)?.length == 3
            LineStyle.TASK -> taskRegex.containsMatchIn(rest)
            LineStyle.BULLET -> bulletRegex.containsMatchIn(rest) && !taskRegex.containsMatchIn(rest)
            LineStyle.NUMBERED -> numberedRegex.containsMatchIn(rest)
            LineStyle.QUOTE -> quoteRegex.containsMatchIn(rest)
        }
    }

    /** Length of a single block prefix (heading, task, bullet, number or quote) at the start of [rest]. */
    private fun linePrefixLength(rest: String): Int =
        (
            headingRegex.find(rest) ?: taskRegex.find(rest) ?: bulletRegex.find(rest)
                ?: numberedRegex.find(rest) ?: quoteRegex.find(rest)
            )?.value?.length ?: 0

    private fun stripPrefix(rest: String): String = rest.substring(linePrefixLength(rest))

    // ---------------- Blocks ----------------

    fun insertLink(edit: TextEdit): TextEdit {
        val t = edit.text
        val selected = t.substring(edit.selectionStart, edit.selectionEnd)
        return if (selected.isEmpty()) {
            val label = "link text"
            val insert = "[$label](https://)"
            val text = t.substring(0, edit.selectionStart) + insert + t.substring(edit.selectionEnd)
            TextEdit(text, edit.selectionStart + 1, edit.selectionStart + 1 + label.length)
        } else {
            val insert = "[$selected](https://)"
            val text = t.substring(0, edit.selectionStart) + insert + t.substring(edit.selectionEnd)
            val cursor = edit.selectionStart + insert.length - 1
            TextEdit(text, cursor)
        }
    }

    fun insertCodeBlock(edit: TextEdit): TextEdit {
        val t = edit.text
        val lineStart = t.lastIndexOf('\n', edit.selectionStart - 1) + 1
        val lineEnd = t.indexOf('\n', edit.selectionEnd).let { if (it == -1) t.length else it }
        val block = t.substring(lineStart, lineEnd)
        val wrapped = "```\n$block\n```"
        val text = t.substring(0, lineStart) + wrapped + t.substring(lineEnd)
        val innerStart = lineStart + 4
        return if (block.isEmpty()) TextEdit(text, innerStart) else TextEdit(text, innerStart, innerStart + block.length)
    }

    fun insertTable(edit: TextEdit): TextEdit {
        val t = edit.text
        val lineEnd = t.indexOf('\n', edit.selectionEnd).let { if (it == -1) t.length else it }
        val before = t.substring(0, lineEnd)
        val lead = when {
            before.isEmpty() -> ""
            before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val table = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"
        val text = before + lead + table + t.substring(lineEnd)
        val firstCell = before.length + lead.length + table.lastIndexOf("|  |") + 2
        return TextEdit(text, firstCell)
    }

    // ---------------- Typing helpers ----------------

    private val listContinuationRegex =
        Regex("^([ \\t]*)([-*+] \\[[ xX]] |[-*+] |(\\d+)([.)]) |> )")

    /**
     * Called after each body change. If the user just pressed Enter at the end of (or inside)
     * a list item, continue the list on the new line. Pressing Enter on an empty item ends the
     * list. Returns null when no change is needed.
     */
    fun continueListOnEnter(old: TextEdit, new: TextEdit): TextEdit? {
        if (old.selectionStart != old.selectionEnd) return null
        if (new.selectionStart != new.selectionEnd) return null
        val pos = old.selectionStart
        if (new.text.length != old.text.length + 1 || new.selectionStart != pos + 1) return null
        if (new.text[pos] != '\n') return null
        if (!new.text.regionMatches(0, old.text, 0, pos)) return null
        if (!new.text.regionMatches(pos + 1, old.text, pos, old.text.length - pos)) return null

        val lineStart = old.text.lastIndexOf('\n', pos - 1) + 1
        val lineBeforeCursor = old.text.substring(lineStart, pos)
        val match = listContinuationRegex.find(lineBeforeCursor) ?: return null
        val prefix = match.value

        if (lineBeforeCursor == prefix && old.text.substring(pos).substringBefore('\n').isBlank()) {
            // Empty item: remove its marker instead of adding another.
            val text = old.text.substring(0, lineStart) + old.text.substring(pos)
            return TextEdit(text, lineStart)
        }

        val indent = match.groupValues[1]
        val marker = match.groupValues[2]
        val next = when {
            marker.contains('[') -> marker.first() + " [ ] "
            match.groupValues[3].isNotEmpty() -> {
                val n = match.groupValues[3].toLongOrNull()?.plus(1) ?: 1
                "$n${match.groupValues[4]} "
            }
            else -> marker
        }
        val insert = indent + next
        val text = new.text.substring(0, pos + 1) + insert + new.text.substring(pos + 1)
        return TextEdit(text, pos + 1 + insert.length)
    }

    private val taskToggleRegex = Regex("^([ \\t]*[-*+] \\[)([ xX])(])")

    /** Toggles the checkbox on line [lineIndex] (0-based). Returns null if that line is not a task. */
    fun toggleTask(text: String, lineIndex: Int): String? {
        val lines = text.split('\n').toMutableList()
        if (lineIndex !in lines.indices) return null
        val line = lines[lineIndex]
        val m = taskToggleRegex.find(line) ?: return null
        val checked = m.groupValues[2] != " "
        lines[lineIndex] = m.groupValues[1] + (if (checked) " " else "x") + m.groupValues[3] +
            line.substring(m.range.last + 1)
        return lines.joinToString("\n")
    }

    /** All case-insensitive occurrences of [query] in [text] (at most [limit]). */
    fun findMatches(text: String, query: String, limit: Int = 1_000): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val result = ArrayList<IntRange>()
        var i = text.indexOf(query, 0, ignoreCase = true)
        while (i >= 0 && result.size < limit) {
            result += i until i + query.length
            i = text.indexOf(query, i + query.length, ignoreCase = true)
        }
        return result
    }
}
