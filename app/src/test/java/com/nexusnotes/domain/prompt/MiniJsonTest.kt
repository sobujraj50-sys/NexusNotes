package com.nexusnotes.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun roundTripTypes() {
        val value = linkedMapOf(
            "s" to "text \"quoted\" \\ back\nnew\ttab",
            "bn" to "বাংলা 😀",
            "n" to 42L,
            "neg" to -7L,
            "d" to 1.5,
            "t" to true,
            "f" to false,
            "nul" to null,
            "list" to listOf(1L, "a", listOf<Any?>()),
            "obj" to mapOf<String, Any?>(),
        )
        assertEquals(value, MiniJson.parse(MiniJson.write(value)))
    }

    @Test
    fun controlCharsEscaped() {
        assertEquals("\"\\u0001\"", MiniJson.write("\u0001"))
        assertEquals("\u0001", MiniJson.parse("\"\\u0001\""))
    }

    @Test
    fun whitespaceAndUnicodeEscapes() {
        assertEquals(mapOf("a" to listOf("é")), MiniJson.parse(" {\n \"a\" : [ \"\\u00e9\" ] } "))
    }

    private fun fails(json: String) {
        try {
            MiniJson.parse(json)
            throw AssertionError("Expected failure for: $json")
        } catch (e: MiniJson.ParseException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test
    fun rejectsInvalidJson() {
        fails("")
        fails("{")
        fails("{\"a\":1,}")
        fails("[1,2")
        fails("\"unterminated")
        fails("tru")
        fails("{\"a\" 1}")
        fails("{} extra")
        fails("[" .repeat(100) + "]".repeat(100))
    }
}
