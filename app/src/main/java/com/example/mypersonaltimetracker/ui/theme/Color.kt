package com.example.mypersonaltimetracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palettes (used below Android 12, where Material You dynamic color is
 * unavailable). Generated around a deep teal seed — fits "work time" and keeps a
 * distinct identity instead of the default M3 purple.
 */
private val mdLightPrimary = Color(0xFF00696E)
private val mdLightOnPrimary = Color(0xFFFFFFFF)
private val mdLightPrimaryContainer = Color(0xFF9CF1F7)
private val mdLightOnPrimaryContainer = Color(0xFF002022)
private val mdLightSecondary = Color(0xFF4A6364)
private val mdLightOnSecondary = Color(0xFFFFFFFF)
private val mdLightSecondaryContainer = Color(0xFFCCE8E9)
private val mdLightOnSecondaryContainer = Color(0xFF051F20)
private val mdLightTertiary = Color(0xFF50607E)
private val mdLightOnTertiary = Color(0xFFFFFFFF)
private val mdLightTertiaryContainer = Color(0xFFD8E4FF)
private val mdLightOnTertiaryContainer = Color(0xFF0C1D36)
private val mdLightError = Color(0xFFBA1A1A)
private val mdLightOnError = Color(0xFFFFFFFF)
private val mdLightErrorContainer = Color(0xFFFFDAD6)
private val mdLightOnErrorContainer = Color(0xFF410002)
private val mdLightBackground = Color(0xFFF7FAFA)
private val mdLightOnBackground = Color(0xFF191C1C)
private val mdLightSurface = Color(0xFFF7FAFA)
private val mdLightOnSurface = Color(0xFF191C1C)
private val mdLightSurfaceVariant = Color(0xFFDAE4E5)
private val mdLightOnSurfaceVariant = Color(0xFF3F494A)
private val mdLightOutline = Color(0xFF6F797A)
private val mdLightOutlineVariant = Color(0xFFBEC8C9)
private val mdLightInverseSurface = Color(0xFF2D3131)
private val mdLightInverseOnSurface = Color(0xFFEFF1F1)
private val mdLightInversePrimary = Color(0xFF80D4DB)

private val mdDarkPrimary = Color(0xFF80D4DB)
private val mdDarkOnPrimary = Color(0xFF00363A)
private val mdDarkPrimaryContainer = Color(0xFF004F53)
private val mdDarkOnPrimaryContainer = Color(0xFF9CF1F7)
private val mdDarkSecondary = Color(0xFFB0CCCD)
private val mdDarkOnSecondary = Color(0xFF1B3436)
private val mdDarkSecondaryContainer = Color(0xFF324B4C)
private val mdDarkOnSecondaryContainer = Color(0xFFCCE8E9)
private val mdDarkTertiary = Color(0xFFB8C8EA)
private val mdDarkOnTertiary = Color(0xFF22324C)
private val mdDarkTertiaryContainer = Color(0xFF384863)
private val mdDarkOnTertiaryContainer = Color(0xFFD8E4FF)
private val mdDarkError = Color(0xFFFFB4AB)
private val mdDarkOnError = Color(0xFF690005)
private val mdDarkErrorContainer = Color(0xFF93000A)
private val mdDarkOnErrorContainer = Color(0xFFFFDAD6)
private val mdDarkBackground = Color(0xFF101414)
private val mdDarkOnBackground = Color(0xFFDFE4E4)
private val mdDarkSurface = Color(0xFF101414)
private val mdDarkOnSurface = Color(0xFFDFE4E4)
private val mdDarkSurfaceVariant = Color(0xFF3F494A)
private val mdDarkOnSurfaceVariant = Color(0xFFBEC8C9)
private val mdDarkOutline = Color(0xFF899394)
private val mdDarkOutlineVariant = Color(0xFF3F494A)
private val mdDarkInverseSurface = Color(0xFFDFE4E4)
private val mdDarkInverseOnSurface = Color(0xFF2D3131)
private val mdDarkInversePrimary = Color(0xFF00696E)

val LightColors = lightColorScheme(
    primary = mdLightPrimary,
    onPrimary = mdLightOnPrimary,
    primaryContainer = mdLightPrimaryContainer,
    onPrimaryContainer = mdLightOnPrimaryContainer,
    secondary = mdLightSecondary,
    onSecondary = mdLightOnSecondary,
    secondaryContainer = mdLightSecondaryContainer,
    onSecondaryContainer = mdLightOnSecondaryContainer,
    tertiary = mdLightTertiary,
    onTertiary = mdLightOnTertiary,
    tertiaryContainer = mdLightTertiaryContainer,
    onTertiaryContainer = mdLightOnTertiaryContainer,
    error = mdLightError,
    onError = mdLightOnError,
    errorContainer = mdLightErrorContainer,
    onErrorContainer = mdLightOnErrorContainer,
    background = mdLightBackground,
    onBackground = mdLightOnBackground,
    surface = mdLightSurface,
    onSurface = mdLightOnSurface,
    surfaceVariant = mdLightSurfaceVariant,
    onSurfaceVariant = mdLightOnSurfaceVariant,
    outline = mdLightOutline,
    outlineVariant = mdLightOutlineVariant,
    inverseSurface = mdLightInverseSurface,
    inverseOnSurface = mdLightInverseOnSurface,
    inversePrimary = mdLightInversePrimary,
    surfaceTint = mdLightPrimary,
)

val DarkColors = darkColorScheme(
    primary = mdDarkPrimary,
    onPrimary = mdDarkOnPrimary,
    primaryContainer = mdDarkPrimaryContainer,
    onPrimaryContainer = mdDarkOnPrimaryContainer,
    secondary = mdDarkSecondary,
    onSecondary = mdDarkOnSecondary,
    secondaryContainer = mdDarkSecondaryContainer,
    onSecondaryContainer = mdDarkOnSecondaryContainer,
    tertiary = mdDarkTertiary,
    onTertiary = mdDarkOnTertiary,
    tertiaryContainer = mdDarkTertiaryContainer,
    onTertiaryContainer = mdDarkOnTertiaryContainer,
    error = mdDarkError,
    onError = mdDarkOnError,
    errorContainer = mdDarkErrorContainer,
    onErrorContainer = mdDarkOnErrorContainer,
    background = mdDarkBackground,
    onBackground = mdDarkOnBackground,
    surface = mdDarkSurface,
    onSurface = mdDarkOnSurface,
    surfaceVariant = mdDarkSurfaceVariant,
    onSurfaceVariant = mdDarkOnSurfaceVariant,
    outline = mdDarkOutline,
    outlineVariant = mdDarkOutlineVariant,
    inverseSurface = mdDarkInverseSurface,
    inverseOnSurface = mdDarkInverseOnSurface,
    inversePrimary = mdDarkInversePrimary,
    surfaceTint = mdDarkPrimary,
)
