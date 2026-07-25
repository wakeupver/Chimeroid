package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * App-wide Miuix theme controller.
 *
 * [ColorSchemeMode.MonetSystem] is Miuix's built-in equivalent of the previous manual
 * `Build.VERSION.SDK_INT >= S` + `dynamicDarkColorScheme`/`dynamicLightColorScheme` branch:
 * on Android 12+ it derives the palette from the system wallpaper/theme (Android 13+ reads the
 * user's actual Monet style+seed; 12 falls back to the accent1/2/3 system colors), and on older
 * devices it seeds a static Monet palette instead — so no explicit SDK-version branch or
 * `LocalContext`-keyed `remember` is needed here anymore, Miuix's [ThemeController] does the
 * equivalent memoisation internally.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = rememberThemeModePreference(),
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> null
        }

    val controller =
        remember(isDark) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.MonetSystem,
                isDark = isDark,
            )
        }

    MiuixTheme(
        controller = controller,
        content = content,
    )
}

/**
 * Muted (surfaceContainerHigh-toned) [ButtonColors] for a secondary-emphasis [Button] —
 * Miuix's flat button set has no bordered "OutlinedButton" variant to reach for, so this
 * is the shared stand-in wherever a lower-emphasis action sits next to a primary one
 * (e.g. Onboarding's Back button, the touch-controls Reset button).
 */
@Composable
fun mutedButtonColors(): ButtonColors =
    ButtonColors(
        color = MiuixTheme.colorScheme.surfaceContainerHigh,
        disabledColor = MiuixTheme.colorScheme.surfaceContainerHigh,
        contentColor = MiuixTheme.colorScheme.onSurface,
        disabledContentColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    )
