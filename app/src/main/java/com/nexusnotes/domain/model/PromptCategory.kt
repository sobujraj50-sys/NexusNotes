package com.nexusnotes.domain.model

/**
 * Built-in prompt categories. The enum NAME is the stable key that will be
 * persisted in Room (Milestone 5) — never rename an entry; only add new ones.
 * Display names may change freely.
 */
enum class PromptCategory(val displayName: String, val description: String) {
    IMAGE_GENERATION("Image Generation", "Stills, illustrations and concept art"),
    VIDEO_GENERATION("Video Generation", "Text-to-video shots and scenes"),
    IMAGE_TO_VIDEO("Image-to-Video", "Animate a still frame into motion"),
    VIDEO_REVERSE_ENGINEERING("Video Reverse Engineering", "Describe an existing clip as a reusable prompt"),
    CHARACTER_CONSISTENCY("Character Consistency", "Keep characters stable across shots"),
    CLAUDE_AI("Claude AI", "Prompts written for Claude"),
    GEMINI_AI("Gemini AI", "Prompts written for Gemini"),
    ANDROID_DEVELOPMENT("Android Development", "Kotlin, Compose and app architecture"),
    WINDOWS_DEVELOPMENT("Windows Development", "Desktop apps and tooling for Windows"),
    AUTOMATION("Automation", "Workflows, scripts and agents"),
    GENERAL("General Prompts", "Everything else"),
}
