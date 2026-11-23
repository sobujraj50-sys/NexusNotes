package com.nexusnotes.domain.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptExchangeTest {

    private val sample = PromptExchange.ExportedPrompt(
        id = "p1",
        title = "Cinematic",
        template = "A {{subject}} shot\nline 2 \"q\"",
        category = "IMAGE_GENERATION",
        isCustomCategory = false,
        isFavorite = true,
        createdAt = 10,
        updatedAt = 20,
        variableDefaults = mapOf("subject" to "cat"),
    )

    @Test
    fun exportThenImportIsLossless() {
        val json = PromptExchange.encode(listOf(sample, sample.copy(id = "p2", category = "My stuff", isCustomCategory = true)), 99)
        val bundle = PromptExchange.decode(json).getOrThrow()
        assertEquals(99, bundle.exportedAt)
        assertEquals(listOf(sample, sample.copy(id = "p2", category = "My stuff", isCustomCategory = true)), bundle.prompts)
        assertEquals(0, bundle.skippedInvalid)
    }

    @Test
    fun rejectsOtherFiles() {
        assertTrue(PromptExchange.decode("{\"hello\":1}").isFailure)
        assertTrue(PromptExchange.decode("not json").isFailure)
        assertTrue(PromptExchange.decode("[]").isFailure)
    }

    @Test
    fun rejectsNewerVersion() {
        val r = PromptExchange.decode("{\"format\":\"nexus-prompts\",\"version\":99,\"prompts\":[]}")
        assertTrue(r.exceptionOrNull()!!.message!!.contains("newer"))
    }

    @Test
    fun brokenEntriesAreSkippedAndCounted() {
        val json = "{\"format\":\"nexus-prompts\",\"version\":1,\"prompts\":[" +
            "{\"title\":\"ok\",\"template\":\"t\"}," +
            "{\"title\":\"no template\"}," +
            "42," +
            "{\"title\":\"\",\"template\":\"\"}" +
            "]}"
        val b = PromptExchange.decode(json).getOrThrow()
        assertEquals(1, b.prompts.size)
        assertEquals(3, b.skippedInvalid)
        assertEquals("", b.prompts[0].id)
        assertEquals(false, b.prompts[0].isFavorite)
    }

    @Test
    fun examplesAreValidTemplates() {
        PromptExamples.all.forEach { ex ->
            val vars = PromptTemplate.variables(ex.template)
            assertTrue(ex.title, vars.isNotEmpty())
            assertTrue(ex.title, vars.containsAll(ex.defaults.keys))
        }
    }
}
