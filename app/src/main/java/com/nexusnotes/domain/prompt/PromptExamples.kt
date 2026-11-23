package com.nexusnotes.domain.prompt

import com.nexusnotes.domain.model.PromptCategory

/** Optional starter prompts the user can add with one tap from an empty library (never added automatically). */
object PromptExamples {
    data class Example(val title: String, val category: PromptCategory, val template: String, val defaults: Map<String, String>)

    val all: List<Example> = listOf(
        Example(
            "Cinematic still",
            PromptCategory.IMAGE_GENERATION,
            "A cinematic photo of {{subject}}, {{style}} style, {{camera}}, {{lighting}} lighting, highly detailed, " +
                "aspect ratio {{aspect_ratio}}. Avoid: {{negative_prompt}}",
            mapOf("style" to "photorealistic", "camera" to "35mm lens, shallow depth of field", "lighting" to "golden hour", "aspect_ratio" to "16:9", "negative_prompt" to "text, watermark, blur"),
        ),
        Example(
            "Short video shot",
            PromptCategory.VIDEO_GENERATION,
            "{{duration}} second shot of {{subject}} {{action}}. Camera: {{camera}}. Mood: {{mood}}. Aspect ratio {{aspect_ratio}}.",
            mapOf("duration" to "8", "camera" to "slow dolly-in", "mood" to "calm", "aspect_ratio" to "9:16"),
        ),
        Example(
            "Animate a still image",
            PromptCategory.IMAGE_TO_VIDEO,
            "Animate the provided image: {{motion}}. Keep {{subject}} consistent. Camera {{camera}}. Duration {{duration}} seconds.",
            mapOf("camera" to "static", "duration" to "5"),
        ),
        Example(
            "Consistent character sheet",
            PromptCategory.CHARACTER_CONSISTENCY,
            "Character: {{name}}, {{age}}, {{appearance}}, wearing {{outfit}}. Keep face, hairstyle and outfit identical in every shot. Scene: {{scene}}.",
            emptyMap(),
        ),
        Example(
            "Android code review",
            PromptCategory.ANDROID_DEVELOPMENT,
            "Review this Kotlin / Jetpack Compose code for bugs, performance and readability. Target SDK {{target_sdk}}. " +
                "Explain each issue and show the fix.\n\n```kotlin\n{{code}}\n```",
            mapOf("target_sdk" to "36"),
        ),
        Example(
            "Summarize for me",
            PromptCategory.CLAUDE_AI,
            "Summarize the following in {{language}} as {{format}} for {{audience}}:\n\n{{text}}",
            mapOf("language" to "Bangla", "format" to "5 bullet points", "audience" to "a busy reader"),
        ),
    )
}
