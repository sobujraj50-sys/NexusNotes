package com.nexusnotes.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    @Test
    fun findsVariablesInOrderWithoutDuplicates() {
        assertEquals(
            listOf("subject", "style", "aspect_ratio"),
            PromptTemplate.variables("A {{subject}} in {{ style }} style, {{subject}} again, {{aspect_ratio}}"),
        )
    }

    @Test
    fun invalidNamesAreNotVariables() {
        assertEquals(emptyList<String>(), PromptTemplate.variables("{{}} {{1abc}} {{a b}} { {x} } {{-x}}"))
    }

    @Test
    fun banglaVariableNames() {
        assertEquals(listOf("বিষয়"), PromptTemplate.variables("ছবি: {{বিষয়}}"))
        assertEquals("ছবি: বিড়াল", PromptTemplate.fill("ছবি: {{বিষয়}}", mapOf("বিষয়" to "বিড়াল")).text)
    }

    @Test
    fun fillReplacesAllOccurrences() {
        val r = PromptTemplate.fill("{{a}} and {{ a }} and {{b}}", mapOf("a" to "X", "b" to "Y"))
        assertEquals("X and X and Y", r.text)
        assertTrue(r.missing.isEmpty())
        assertEquals(listOf(0..0, 6..6, 12..12), r.filledRanges)
    }

    @Test
    fun missingValuesStayAsPlaceholdersAndAreReported() {
        val r = PromptTemplate.fill("Hi {{name}}, {{day}} {{name}}", mapOf("day" to "Monday", "name" to "  "))
        assertEquals("Hi {{name}}, Monday {{name}}", r.text)
        assertEquals(listOf("name"), r.missing)
        assertEquals(2, r.unfilledRanges.size)
        assertEquals("{{name}}", r.text.substring(r.unfilledRanges[0].first, r.unfilledRanges[0].last + 1))
    }

    @Test
    fun valuesAreNotRescanned() {
        val r = PromptTemplate.fill("{{a}} {{b}}", mapOf("a" to "{{b}}", "b" to "B"))
        assertEquals("{{b}} B", r.text)
    }

    @Test
    fun escapedBracesAreLiteral() {
        assertEquals(listOf("y"), PromptTemplate.variables("\\{{x}} {{y}}"))
        assertEquals("{{x}} Y", PromptTemplate.fill("\\{{x}} {{y}}", mapOf("x" to "X", "y" to "Y")).text)
    }

    @Test
    fun templateWithoutVariablesIsUnchanged() {
        val t = "Plain prompt with { single } braces and }} stray"
        assertEquals(t, PromptTemplate.fill(t, mapOf("a" to "b")).text)
    }

    @Test
    fun sourceTemplateStringIsNotModified() {
        val template = "{{x}}"
        PromptTemplate.fill(template, mapOf("x" to "filled"))
        assertEquals("{{x}}", template)
    }

    @Test
    fun deterministic() {
        val t = "{{a}}-{{b}}-{{c}}"
        val v = mapOf("c" to "3", "a" to "1", "b" to "2")
        assertEquals(PromptTemplate.fill(t, v), PromptTemplate.fill(t, v.toList().reversed().toMap()))
    }
}
