package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tight, bold, character-rich typography stack
private val AppTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Black,    fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

private val DarkColorScheme = darkColorScheme(
    primary                = md_theme_dark_primary,
    onPrimary              = md_theme_dark_onPrimary,
    primaryContainer       = md_theme_dark_primaryContainer,
    onPrimaryContainer     = md_theme_dark_onPrimaryContainer,
    secondary              = md_theme_dark_secondary,
    onSecondary            = md_theme_dark_onSecondary,
    secondaryContainer     = md_theme_dark_secondaryContainer,
    onSecondaryContainer   = md_theme_dark_onSecondaryContainer,
    tertiary               = md_theme_dark_tertiary,
    onTertiary             = md_theme_dark_onTertiary,
    tertiaryContainer      = md_theme_dark_tertiaryContainer,
    onTertiaryContainer    = md_theme_dark_onTertiaryContainer,
    error                  = md_theme_dark_error,
    errorContainer         = md_theme_dark_errorContainer,
    onError                = md_theme_dark_onError,
    onErrorContainer       = md_theme_dark_onErrorContainer,
    background             = md_theme_dark_background,
    onBackground           = md_theme_dark_onBackground,
    surface                = md_theme_dark_surface,
    onSurface              = md_theme_dark_onSurface,
    surfaceVariant         = md_theme_dark_surfaceVariant,
    onSurfaceVariant       = md_theme_dark_onSurfaceVariant,
    outline                = md_theme_dark_outline,
    inverseOnSurface       = md_theme_dark_inverseOnSurface,
    inverseSurface         = md_theme_dark_inverseSurface,
    inversePrimary         = md_theme_dark_inversePrimary,
    surfaceTint            = md_theme_dark_surfaceTint,
    outlineVariant         = md_theme_dark_outlineVariant,
    scrim                  = md_theme_dark_scrim,
)

private val LightColorScheme = lightColorScheme(
    primary                = md_theme_light_primary,
    onPrimary              = md_theme_light_onPrimary,
    primaryContainer       = md_theme_light_primaryContainer,
    onPrimaryContainer     = md_theme_light_onPrimaryContainer,
    secondary              = md_theme_light_secondary,
    onSecondary            = md_theme_light_onSecondary,
    secondaryContainer     = md_theme_light_secondaryContainer,
    onSecondaryContainer   = md_theme_light_onSecondaryContainer,
    tertiary               = md_theme_light_tertiary,
    onTertiary             = md_theme_light_onTertiary,
    tertiaryContainer      = md_theme_light_tertiaryContainer,
    onTertiaryContainer    = md_theme_light_onTertiaryContainer,
    error                  = md_theme_light_error,
    errorContainer         = md_theme_light_errorContainer,
    onError                = md_theme_light_onError,
    onErrorContainer       = md_theme_light_onErrorContainer,
    background             = md_theme_light_background,
    onBackground           = md_theme_light_onBackground,
    surface                = md_theme_light_surface,
    onSurface              = md_theme_light_onSurface,
    surfaceVariant         = md_theme_light_surfaceVariant,
    onSurfaceVariant       = md_theme_light_onSurfaceVariant,
    outline                = md_theme_light_outline,
    inverseOnSurface       = md_theme_light_inverseOnSurface,
    inverseSurface         = md_theme_light_inverseSurface,
    inversePrimary         = md_theme_light_inversePrimary,
    surfaceTint            = md_theme_light_surfaceTint,
    outlineVariant         = md_theme_light_outlineVariant,
    scrim                  = md_theme_light_scrim,
)

enum class ChimeroidThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AppTheme(
    themeMode: ChimeroidThemeMode = ChimeroidThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ChimeroidThemeMode.SYSTEM -> isSystemInDarkTheme()
        ChimeroidThemeMode.LIGHT  -> false
        ChimeroidThemeMode.DARK   -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
