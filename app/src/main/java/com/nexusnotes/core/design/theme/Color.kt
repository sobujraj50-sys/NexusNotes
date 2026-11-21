package com.nexusnotes.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw design tokens. Screens must not use these directly — read colors from
 * MaterialTheme.colorScheme so light, dark and dynamic color all work.
 * The only exception is [NoteLabelColors], which are user-chosen data colors.
 */
object NexusPalette {
    // Dark (primary experience)
    val Night = Color(0xFF0B1020)
    val NightLowest = Color(0xFF080C18)
    val NightLow = Color(0xFF111729)
    val NightSurface = Color(0xFF151C30)
    val NightHigh = Color(0xFF1C2439)
    val NightHighest = Color(0xFF242D45)
    val NightOutline = Color(0xFF3A4566)
    val NightOutlineVariant = Color(0xFF262F4A)

    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF6D28D9)
    val VioletContainer = Color(0xFF2D2259)
    val OnVioletContainer = Color(0xFFE9DDFF)

    val Cyan = Color(0xFF22D3EE)
    val CyanDeep = Color(0xFF0E7490)
    val CyanContainer = Color(0xFF0B3A46)
    val OnCyanContainer = Color(0xFFCFFAFE)

    val Emerald = Color(0xFF34D399)
    val EmeraldDeep = Color(0xFF047857)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)

    // Light
    val Mist = Color(0xFFF6F7FB)
    val Paper = Color(0xFFFFFFFF)
    val MistLow = Color(0xFFF1F3F9)
    val MistContainer = Color(0xFFEBEEF6)
    val MistHigh = Color(0xFFE4E8F2)
    val MistHighest = Color(0xFFDDE2EE)
    val Ink = Color(0xFF0F172A)
    val Slate = Color(0xFF475569)
    val LightOutline = Color(0xFFA3ADC2)
    val LightOutlineVariant = Color(0xFFD5DAE5)
    val VioletContainerLight = Color(0xFFEDE7FF)
    val CyanContainerLight = Color(0xFFD7F6FB)

    // Status
    val ErrorDark = Color(0xFFF87171)
    val ErrorContainerDark = Color(0xFF5B1A1A)
    val ErrorLight = Color(0xFFDC2626)
    val ErrorContainerLight = Color(0xFFFEE2E2)
}

/** User-selectable note/folder label colors (Milestone 2+). Order is stable; index is persisted. */
val NoteLabelColors: List<Color> = listOf(
    Color(0xFF8B5CF6), // violet
    Color(0xFF22D3EE), // cyan
    Color(0xFF34D399), // emerald
    Color(0xFFFBBF24), // amber
    Color(0xFFFB7185), // rose
    Color(0xFF60A5FA), // sky
    Color(0xFFA3A3A3), // neutral
)
