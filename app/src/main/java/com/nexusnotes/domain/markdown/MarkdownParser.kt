package com.nexusnotes.domain.markdown

sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class Strike(val children: List<MdInline>) : MdInline
    data class Underline(val children: List<MdInline>) : MdInline
    data class Code(val text: String) : MdInline
    data class Link(val children: List<MdInline>, val url: String) : MdInline
}

sealed interface ListMarker {
    data object Bullet : ListMarker
    data class Ordered(val number: Long) : ListMarker
    data class Task(val checked: Boolean) : ListMarker
}

sealed interface MdBlock {
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock

    /** [sourceLine] is the 0-based line in the note, used to toggle checkboxes from the preview. */
    data class ListItem(
        val indent: Int,
        val marker: ListMarker,
        val inlines: List<MdInline>,
        val sourceLine: Int,
    ) : MdBlock

    data class Quote(val lines: List<List<MdInline>>) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class Table(val header: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock
    data object Rule : MdBlock
}

/**
 * Parser for the Nexus Markdown v1 subset (see docs/EDITOR_FORMAT.md).
 * Small and predictable on purpose: unknown syntax is shown as plain text, never dropped.
 */
object MarkdownParser {

    private val headingRegex = Regex("^(#{1,6})[ \\t]+(.*?)[ \\t]*#*[ \\t]*$")
    private val ruleRegex = Regex("^[ \\t]*([-*_])([ \\t]*\\1){2,}[ \\t]*$")
    private val taskRegex = Regex("^([ \\t]*)[-*+] \\[([ xX])](?:[ \\t]+(.*))?$")
    private val bulletRegex = Regex("^([ \\t]*)[-*+](?:[ \\t]+(.*))?$")
    private val orderedRegex = Regex("^([ \\t]*)(\\d{1,9})[.)](?:[ \\t]+(.*))?$")
    private val quoteRegex = Regex("^[ \\t]*> ?(.*)$")
    private val tableSeparatorRegex = Regex("^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$")

    fun parse(markdown: String): List<MdBlock> {
        val lines = markdown.split('\n').map { it.removeSuffix("\r") }
        val blocks = ArrayList<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++

                line.trimStart().startsWith("```") -> {
                    val language = line.trimStart().removePrefix("```").trim()
                    val code = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code += lines[i]
                        i++
                    }
                    i++ // closing fence (or end of text)
                    blocks += MdBlock.CodeBlock(language, code.joinToString("\n"))
                }

                headingRegex.matches(line) -> {
                    val m = headingRegex.find(line)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2]))
                    i++
                }

                ruleRegex.matches(line) -> {
                    blocks += MdBlock.Rule
                    i++
                }

                isTableStart(lines, i) -> {
                    val header = splitRow(lines[i]).map(::parseInline)
                    i += 2
                    val rows = ArrayList<List<List<MdInline>>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        val cells = splitRow(lines[i])
                        val padded = List(header.size) { idx -> cells.getOrElse(idx) { "" } }
                        rows += padded.map(::parseInline)
                        i++
                    }
                    blocks += MdBlock.Table(header, rows)
                }

                quoteRegex.matches(line) -> {
                    val quoteLines = ArrayList<List<MdInline>>()
                    while (i < lines.size && quoteRegex.matches(lines[i])) {
                        quoteLines += parseInline(quoteRegex.find(lines[i])!!.groupValues[1])
                        i++
                    }
                    blocks += MdBlock.Quote(quoteLines)
                }

                taskRegex.matches(line) -> {
                    val m = taskRegex.find(line)!!
                    blocks += MdBlock.ListItem(
                        indent = indentWidth(m.groupValues[1]),
                        marker = ListMarker.Task(m.groupValues[2] != " "),
                        inlines = parseInline(m.groupValues[3]),
                        sourceLine = i,
                    )
                    i++
                }

                bulletRegex.matches(line) -> {
                    val m = bulletRegex.find(line)!!
                    blocks += MdBlock.ListItem(indentWidth(m.groupValues[1]), ListMarker.Bullet, parseInline(m.groupValues[2]), i)
                    i++
                }

                orderedRegex.matches(line) -> {
                    val m = orderedRegex.find(line)!!
                    blocks += MdBlock.ListItem(
                        indentWidth(m.groupValues[1]),
                        ListMarker.Ordered(m.groupValues[2].toLong()),
                        parseInline(m.groupValues[3]),
                        i,
                    )
                    i++
                }

                else -> {
                    val paragraph = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() && (paragraph.isEmpty() || !startsBlock(lines, i))) {
                        paragraph += lines[i]
                        i++
                    }
                    blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
                }
            }
        }
        return blocks
    }

    private fun startsBlock(lines: List<String>, i: Int): Boolean {
        val line = lines[i]
        return line.trimStart().startsWith("```") || headingRegex.matches(line) || ruleRegex.matches(line) ||
            quoteRegex.matches(line) || taskRegex.matches(line) || bulletRegex.matches(line) ||
            orderedRegex.matches(line) || isTableStart(lines, i)
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size || !lines[i].contains('|')) return false
        val separator = lines[i + 1]
        // A separator needs a pipe or at least three dashes, so "a | b" followed by "-" is not a table.
        return tableSeparatorRegex.matches(separator) && (separator.contains('|') || separator.count { it == '-' } >= 3)
    }

    private fun splitRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
                current.append('|')
                i += 2
                continue
            }
            if (c == '|') {
                cells += current.toString().trim()
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        cells += current.toString().trim()
        return cells
    }

    private fun indentWidth(indent: String): Int = indent.fold(0) { acc, ch -> acc + if (ch == '\t') 4 else 1 } / 2

    // ---------------- Inline ----------------

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    fun parseInline(s: String): List<MdInline> {
        val out = ArrayList<MdInline>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += MdInline.Text(buf.toString())
                buf.clear()
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]

            if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
                buf.append(s[i + 1])
                i += 2
                continue
            }

            if (c == '`') {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    out += MdInline.Code(s.substring(i + 1, end))
                    i = end + 1
                    continue
                }
            }

            if (s.startsWith("***", i)) {
                val end = s.indexOf("***", i + 3)
                if (end > i + 3) {
                    flush()
                    out += MdInline.Bold(listOf(MdInline.Italic(parseInline(s.substring(i + 3, end)))))
                    i = end + 3
                    continue
                }
            }

            if (s.startsWith("**", i) || s.startsWith("__", i)) {
                val marker = s.substring(i, i + 2)
                var end = s.indexOf(marker, i + 2)
                if (end > i + 2) {
                    // Use the last two characters of a run, e.g. "**a *b***".
                    while (end + 2 < s.length && s[end + 2] == marker[0]) end++
                    flush()
                    out += MdInline.Bold(parseInline(s.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            if (s.startsWith("~~", i)) {
                val end = s.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    out += MdInline.Strike(parseInline(s.substring(i + 2, end)))
                    i = end + 2
                    continue
                }
            }

            if (s.startsWith("<u>", i, ignoreCase = true)) {
                val end = s.indexOf("</u>", i + 3, ignoreCase = true)
                if (end > i + 3) {
                    flush()
                    out += MdInline.Underline(parseInline(s.substring(i + 3, end)))
                    i = end + 4
                    continue
                }
            }

            if ((c == '*' || (c == '_' && (i == 0 || !s[i - 1].isLetterOrDigit()))) &&
                i + 1 < s.length && !s[i + 1].isWhitespace()
            ) {
                val end = findItalicEnd(s, i + 1, c)
                if (end > i + 1) {
                    flush()
                    out += MdInline.Italic(parseInline(s.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            if (c == '[') {
                val close = s.indexOf(']', i + 1)
                if (close > i && close + 1 < s.length && s[close + 1] == '(') {
                    val paren = s.indexOf(')', close + 2)
                    if (paren > close) {
                        flush()
                        out += MdInline.Link(parseInline(s.substring(i + 1, close)), s.substring(close + 2, paren).trim())
                        i = paren + 1
                        continue
                    }
                }
            }

            buf.append(c)
            i++
        }
        flush()
        return out
    }

    /** Finds the closing single-character italic marker, skipping over "**bold**" spans. */
    private fun findItalicEnd(s: String, from: Int, marker: Char): Int {
        var j = from
        while (j < s.length) {
            if (marker == '*' && s.startsWith("**", j)) {
                val close = s.indexOf("**", j + 2)
                if (close < 0) return -1
                j = close + 2
                continue
            }
            if (s[j] == marker && !s[j - 1].isWhitespace()) {
                val nextOk = marker != '_' || j + 1 >= s.length || !s[j + 1].isLetterOrDigit()
                if (nextOk) return j
            }
            j++
        }
        return -1
    }

    /** Plain text of inline content (for accessibility and tests). */
    fun plainText(inlines: List<MdInline>): String = buildString {
        inlines.forEach { node ->
            when (node) {
                is MdInline.Text -> append(node.text)
                is MdInline.Code -> append(node.text)
                is MdInline.Bold -> append(plainText(node.children))
                is MdInline.Italic -> append(plainText(node.children))
                is MdInline.Strike -> append(plainText(node.children))
                is MdInline.Underline -> append(plainText(node.children))
                is MdInline.Link -> append(plainText(node.children))
            }
        }
    }
}
