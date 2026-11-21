package com.nexusnotes.core.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NexusDarkColors = darkColorScheme(
    primary = NexusPalette.Violet,
    onPrimary = Color.White,
    primaryContainer = NexusPalette.VioletContainer,
    onPrimaryContainer = NexusPalette.OnVioletContainer,
    inversePrimary = NexusPalette.VioletDeep,
    secondary = NexusPalette.Cyan,
    onSecondary = Color(0xFF042F37),
    secondaryContainer = NexusPalette.CyanContainer,
    onSecondaryContainer = NexusPalette.OnCyanContainer,
    tertiary = NexusPalette.Emerald,
    onTertiary = Color(0xFF052E1F),
    background = NexusPalette.Night,
    onBackground = NexusPalette.TextPrimary,
    surface = NexusPalette.Night,
    onSurface = NexusPalette.TextPrimary,
    surfaceVariant = NexusPalette.NightSurface,
    onSurfaceVariant = NexusPalette.TextSecondary,
    surfaceContainerLowest = NexusPalette.NightLowest,
    surfaceContainerLow = NexusPalette.NightLow,
    surfaceContainer = NexusPalette.NightSurface,
    surfaceContainerHigh = NexusPalette.NightHigh,
    surfaceContainerHighest = NexusPalette.NightHighest,
    outline = NexusPalette.NightOutline,
    outlineVariant = NexusPalette.NightOutlineVariant,
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = NexusPalette.Ink,
    error = NexusPalette.ErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = NexusPalette.ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA),
)

private val NexusLightColors = lightColorScheme(
    primary = NexusPalette.VioletDeep,
    onPrimary = Color.White,
    primaryContainer = NexusPalette.VioletContainerLight,
    onPrimaryContainer = Color(0xFF2E1065),
    inversePrimary = NexusPalette.Violet,
    secondary = NexusPalette.CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = NexusPalette.CyanContainerLight,
    onSecondaryContainer = Color(0xFF083344),
    tertiary = NexusPalette.EmeraldDeep,
    onTertiary = Color.White,
    background = NexusPalette.Mist,
    onBackground = NexusPalette.Ink,
    surface = NexusPalette.Mist,
    onSurface = NexusPalette.Ink,
    surfaceVariant = NexusPalette.MistContainer,
    onSurfaceVariant = NexusPalette.Slate,
    surfaceContainerLowest = NexusPalette.Paper,
    surfaceContainerLow = NexusPalette.MistLow,
    surfaceContainer = NexusPalette.MistContainer,
    surfaceContainerHigh = NexusPalette.MistHigh,
    surfaceContainerHighest = NexusPalette.MistHighest,
    outline = NexusPalette.LightOutline,
    outlineVariant = NexusPalette.LightOutlineVariant,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = NexusPalette.TextPrimary,
    error = NexusPalette.ErrorLight,
    onError = Color.White,
    errorContainer = NexusPalette.ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
)

/**
 * App theme. Follows the system light/dark setting.
 * Dynamic (wallpaper) color is supported on Android 12+ but off by default so the
 * brand palette is used; a persisted user toggle arrives with DataStore in Milestone 2.
 */
@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NexusDarkColors
        else -> NexusLightColors
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexusTypography,
            shapes = NexusShapes,
            content = content,
        )
    }
}
