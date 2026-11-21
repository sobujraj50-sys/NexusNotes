package com.nexusnotes.domain

import com.nexusnotes.domain.model.PromptCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCategoryTest {

    @Test
    fun allElevenRequiredCategoriesExist() {
        val expected = listOf(
            "Image Generation",
            "Video Generation",
            "Image-to-Video",
            "Video Reverse Engineering",
            "Character Consistency",
            "Claude AI",
            "Gemini AI",
            "Android Development",
            "Windows Development",
            "Automation",
            "General Prompts",
        )
        assertEquals(expected, PromptCategory.entries.map { it.displayName })
    }

    @Test
    fun persistedKeysAreStable() {
        // These names will be stored in the database. Changing one breaks existing data.
        assertTrue(PromptCategory.entries.any { it.name == "IMAGE_GENERATION" })
        assertTrue(PromptCategory.entries.any { it.name == "GENERAL" })
        assertEquals(PromptCategory.entries.size, PromptCategory.entries.map { it.name }.toSet().size)
    }
}
