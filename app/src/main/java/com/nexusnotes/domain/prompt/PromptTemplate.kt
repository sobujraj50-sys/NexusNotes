package com.nexusnotes.domain.prompt

/**
 * `{{variable}}` templates.
 *
 * Rules (deterministic, documented in docs/MILESTONE_5.md):
 * - A variable is `{{name}}`; spaces inside the braces are allowed: `{{ name }}`.
 * - Names start with a letter or `_`, then letters, vowel signs, digits or `_` (any script, so Bangla works).
 * - `\{{` is a literal `{{` and is never treated as a variable.
 * - Filling is a single pass: text inserted for a variable is never scanned again, so a value
 *   that itself contains `{{x}}` stays literal.
 * - A variable with no value is left in place as `{{name}}` and reported as missing.
 * - The source template is never modified; filling always returns new text.
 */
object PromptTemplate {

    private val variableRegex = Regex("""(\\)?\{\{\s*([\p{L}_][\p{L}\p{M}\p{N}_]*)\s*}}""")

    data class Token(val name: String, val range: IntRange)

    /** Variables in order of first appearance, without duplicates. */
    fun variables(template: String): List<String> = tokens(template).map { it.name }.distinct()

    /** Every variable occurrence with its position (escaped ones excluded). */
    fun tokens(template: String): List<Token> =
        variableRegex.findAll(template)
            .filter { it.groupValues[1].isEmpty() }
            .map { Token(it.groupValues[2], it.range) }
            .toList()

    data class FillResult(
        val text: String,
        /** Variables that had no (or a blank) value and were left as `{{name}}`. */
        val missing: List<String>,
        /** Ranges in [text] that came from variable values (for highlighting). */
        val filledRanges: List<IntRange>,
        /** Ranges in [text] of placeholders that are still unfilled. */
        val unfilledRanges: List<IntRange>,
    )

    fun fill(template: String, values: Map<String, String>): FillResult {
        val out = StringBuilder()
        val missing = LinkedHashSet<String>()
        val filled = ArrayList<IntRange>()
        val unfilled = ArrayList<IntRange>()
        var last = 0
        variableRegex.findAll(template).forEach { m ->
            out.append(template, last, m.range.first)
            if (m.groupValues[1].isNotEmpty()) {
                // Escaped: output the literal braces without the backslash.
                out.append(m.value.substring(1))
            } else {
                val name = m.groupValues[2]
                val value = values[name]
                if (value.isNullOrBlank()) {
                    missing += name
                    val start = out.length
                    out.append("{{").append(name).append("}}")
                    unfilled += start until out.length
                } else {
                    val start = out.length
                    out.append(value)
                    filled += start until out.length
                }
            }
            last = m.range.last + 1
        }
        out.append(template, last, template.length)
        return FillResult(out.toString(), missing.toList(), filled, unfilled)
    }
}
