package com.nexusnotes.domain.search

/** Turns what the user typed into a safe SQLite FTS4 query and builds highlighted snippets. */
object SearchQuery {

    private val splitter = Regex("\\s+")

    /** Characters with special meaning in FTS4 query syntax. They are removed from user input. */
    private val ftsSpecial = Regex("[\"*^():{}\\[\\]\\-+~|&<>=!@#$%;,.?/\\\\']")

    /** Search terms the user typed (lower-cased, special characters removed, max 8 terms). */
    fun terms(input: String): List<String> =
        input.replace(ftsSpecial, " ")
            .split(splitter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .distinct()
            .take(8)

    /**
     * FTS MATCH expression: every term must appear, each as a prefix ("নোট" finds "নোটের").
     * Returns null if nothing searchable remains.
     */
    fun toFtsMatch(input: String): String? {
        val t = terms(input)
        if (t.isEmpty()) return null
        return t.joinToString(" ") { "\"$it*\"" }
    }

    data class Snippet(val text: String, val highlights: List<IntRange>)

    /**
     * A short piece of [body] around the first match, with every term occurrence marked.
     * Falls back to the start of the body when no term appears in it (e.g. matched the title).
     */
    fun snippet(body: String, terms: List<String>, maxLength: Int = 140): Snippet {
        val flat = body.replace(splitter, " ").trim()
        if (flat.isEmpty()) return Snippet("", emptyList())
        val first = terms.mapNotNull { term -> flat.indexOf(term, ignoreCase = true).takeIf { it >= 0 } }.minOrNull()
        val start = if (first == null) 0 else (first - maxLength / 4).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(flat.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < flat.length) "…" else ""
        val text = prefix + flat.substring(start, end) + suffix
        return Snippet(text, highlightRanges(text, terms))
    }

    /** All non-overlapping, case-insensitive occurrences of any term, sorted. */
    fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        terms.filter { it.isNotEmpty() }.forEach { term ->
            var i = text.indexOf(term, 0, ignoreCase = true)
            while (i >= 0) {
                ranges += i until i + term.length
                i = text.indexOf(term, i + term.length, ignoreCase = true)
            }
        }
        val sorted = ranges.sortedBy { it.first }
        val merged = ArrayList<IntRange>()
        sorted.forEach { r ->
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                merged += r
            }
        }
        return merged
    }
}
