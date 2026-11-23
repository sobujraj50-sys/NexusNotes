package com.nexusnotes.domain.prompt

/**
 * Minimal, strict JSON reader/writer (RFC 8259 subset: no comments, no trailing commas).
 * Used for prompt import/export so no extra library is needed. Values map to:
 * object → Map<String, Any?>, array → List<Any?>, string → String, number → Long or Double,
 * true/false → Boolean, null → null.
 */
object MiniJson {

    class ParseException(message: String) : Exception(message)

    // ---------------- Writing ----------------

    fun write(value: Any?): String = StringBuilder().also { writeTo(it, value) }.toString()

    private fun writeTo(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long -> sb.append(value.toString())
            is Double -> {
                require(value.isFinite()) { "JSON cannot hold $value" }
                sb.append(value.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    writeTo(sb, v)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    writeTo(sb, v)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class.simpleName}")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        s.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ---------------- Reading ----------------

    fun parse(text: String, maxDepth: Int = 64): Any? {
        val p = Parser(text, maxDepth)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (p.i != text.length) throw ParseException("Unexpected data after JSON at position ${p.i}")
        return v
    }

    private class Parser(val s: String, val maxDepth: Int) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i] in " \t\r\n") i++
        }

        fun value(depth: Int): Any? {
            if (depth > maxDepth) throw ParseException("JSON nested too deeply")
            if (i >= s.length) throw ParseException("Unexpected end of JSON")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unexpected '$c' at position $i")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("Invalid value at position $i")
            i += word.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') throw ParseException("Expected a key at position $i")
                val key = str()
                skipWs()
                if (i >= s.length || s[i] != ':') throw ParseException("Expected ':' at position $i")
                i++
                skipWs()
                map[key] = value(depth + 1)
                skipWs()
                if (i >= s.length) throw ParseException("Unexpected end of JSON")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> throw ParseException("Expected ',' or '}' at position $i")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                skipWs()
                list += value(depth + 1)
                skipWs()
                if (i >= s.length) throw ParseException("Unexpected end of JSON")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw ParseException("Expected ',' or ']' at position $i")
                }
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (true) {
                if (i >= s.length) throw ParseException("Unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw ParseException("Unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("Bad unicode escape")
                                val hex = s.substring(i, i + 4)
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw ParseException("Bad unicode escape"))
                                i += 4
                            }
                            else -> throw ParseException("Bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> throw ParseException("Control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var isDouble = false
            if (i < s.length && s[i] == '.') { isDouble = true; i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val text = s.substring(start, i)
            return if (isDouble) {
                text.toDoubleOrNull() ?: throw ParseException("Bad number '$text'")
            } else {
                text.toLongOrNull() ?: text.toDoubleOrNull() ?: throw ParseException("Bad number '$text'")
            }
        }
    }
}
