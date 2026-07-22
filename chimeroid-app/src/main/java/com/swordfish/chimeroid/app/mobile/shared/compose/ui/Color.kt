package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.ui.graphics.Color

val md_theme_light_primary = Color(0xFF006E28)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF6BFF84)
val md_theme_light_onPrimaryContainer = Color(0xFF002107)
val md_theme_light_secondary = Color(0xFF516350)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFD4E8D0)
val md_theme_light_onSecondaryContainer = Color(0xFF101F10)
val md_theme_light_tertiary = Color(0xFF39656C)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFBCEBF2)
val md_theme_light_onTertiaryContainer = Color(0xFF001F24)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFCFDF7)
val md_theme_light_onBackground = Color(0xFF1A1C19)
val md_theme_light_surface = Color(0xFFFCFDF7)
val md_theme_light_onSurface = Color(0xFF1A1C19)
val md_theme_light_surfaceVariant = Color(0xFFDEE5D9)
val md_theme_light_onSurfaceVariant = Color(0xFF424940)
val md_theme_light_outline = Color(0xFF72796F)
val md_theme_light_inverseOnSurface = Color(0xFFF0F1EB)
val md_theme_light_inverseSurface = Color(0xFF2F312D)
val md_theme_light_inversePrimary = Color(0xFF3EE366)
val md_theme_light_surfaceTint = Color(0xFF006E28)
val md_theme_light_outlineVariant = Color(0xFFC2C9BD)
val md_theme_light_scrim = Color(0xFF000000)

// Neutral-palette surface tones (surfaceDim/Bright + the surfaceContainer* ramp). Cards,
// menus, dialogs, nav bars and sheets default to these roles as of Material3 1.4; without
// them the static (non-dynamic-color) scheme silently fell back to M3's stock baseline
// tint instead of this app's own green-neutral undertone. Derived in Lab space from this
// app's own light/dark `surface` (see /tmp/tonal.py in the delivery notes) rather than
// picked by hand, so every step is tonally consistent with the existing palette.
val md_theme_light_surfaceDim = Color(0xFFD9DBD5)
val md_theme_light_surfaceBright = Color(0xFFF9FAF4)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFA)
val md_theme_light_surfaceContainerLow = Color(0xFFF3F4EE)
val md_theme_light_surfaceContainer = Color(0xFFEDEEE9)
val md_theme_light_surfaceContainerHigh = Color(0xFFE8E9E3)
val md_theme_light_surfaceContainerHighest = Color(0xFFE2E3DD)

val md_theme_dark_primary = Color(0xFF3EE366)
val md_theme_dark_onPrimary = Color(0xFF003911)
val md_theme_dark_primaryContainer = Color(0xFF00531C)
val md_theme_dark_onPrimaryContainer = Color(0xFF6BFF84)
val md_theme_dark_secondary = Color(0xFFB9CCB5)
val md_theme_dark_onSecondary = Color(0xFF243424)
val md_theme_dark_secondaryContainer = Color(0xFF3A4B39)
val md_theme_dark_onSecondaryContainer = Color(0xFFD4E8D0)
val md_theme_dark_tertiary = Color(0xFFA1CED6)
val md_theme_dark_onTertiary = Color(0xFF00363C)
val md_theme_dark_tertiaryContainer = Color(0xFF1F4D53)
val md_theme_dark_onTertiaryContainer = Color(0xFFBCEBF2)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1A1C19)
val md_theme_dark_onBackground = Color(0xFFE2E3DD)
val md_theme_dark_surface = Color(0xFF1A1C19)
val md_theme_dark_onSurface = Color(0xFFE2E3DD)
val md_theme_dark_surfaceVariant = Color(0xFF424940)
val md_theme_dark_onSurfaceVariant = Color(0xFFC2C9BD)
val md_theme_dark_outline = Color(0xFF8C9389)
val md_theme_dark_inverseOnSurface = Color(0xFF1A1C19)
val md_theme_dark_inverseSurface = Color(0xFFE2E3DD)
val md_theme_dark_inversePrimary = Color(0xFF006E28)
val md_theme_dark_surfaceTint = Color(0xFFDDDDDD)
val md_theme_dark_outlineVariant = Color(0xFF424940)
val md_theme_dark_scrim = Color(0xFF000000)

val md_theme_dark_surfaceDim = Color(0xFF121410)
val md_theme_dark_surfaceBright = Color(0xFF383A36)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0C0F0A)
val md_theme_dark_surfaceContainerLow = Color(0xFF1A1C19)
val md_theme_dark_surfaceContainer = Color(0xFF1E201D)
val md_theme_dark_surfaceContainerHigh = Color(0xFF292B27)
val md_theme_dark_surfaceContainerHighest = Color(0xFF333532)

// "Fixed" roles (Material3 ColorScheme, stable since 1.4): same value in both light and
// dark theme by spec, so containers that must stay legible/constant across a theme switch
// (e.g. a colored chip carried over from a differently-themed screen) don't flip. Every
// value below already exists elsewhere in this file at the matching tone step — T90/T80/
// T10/T30 of each palette — so this reuses, not invents, color.
val md_theme_primaryFixed = md_theme_light_primaryContainer
val md_theme_primaryFixedDim = md_theme_dark_primary
val md_theme_onPrimaryFixed = md_theme_light_onPrimaryContainer
val md_theme_onPrimaryFixedVariant = md_theme_dark_primaryContainer

val md_theme_secondaryFixed = md_theme_light_secondaryContainer
val md_theme_secondaryFixedDim = md_theme_dark_secondary
val md_theme_onSecondaryFixed = md_theme_light_onSecondaryContainer
val md_theme_onSecondaryFixedVariant = md_theme_dark_secondaryContainer

val md_theme_tertiaryFixed = md_theme_light_tertiaryContainer
val md_theme_tertiaryFixedDim = md_theme_dark_tertiary
val md_theme_onTertiaryFixed = md_theme_light_onTertiaryContainer
val md_theme_onTertiaryFixedVariant = md_theme_dark_tertiaryContainer

val seed = Color(0xFF00C64E)
