package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.chimeroid.app.utils.settings.safeGetString
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper

/**
 * Single source of truth for the user-selectable app theme.
 *
 * Persisted through the same Harmony-backed preferences used by every other setting, so the
 * value can be read safely from both the main process and the `:game` process (plain
 * [android.content.SharedPreferences] is not reliably observable across processes).
 */
enum class ThemeMode(val prefValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        val prefValues: List<String> = entries.map { it.prefValue }

        fun fromPrefValue(value: String?): ThemeMode = entries.firstOrNull { it.prefValue == value } ?: SYSTEM
    }
}

/** Plain (non-Compose) read. Safe to call from `onCreate()` before content is set. */
fun readThemeModePreference(activity: ComponentActivity): ThemeMode {
    val preferences = SharedPreferencesHelper.getSharedPreferences(activity)
    val key = activity.getString(R.string.pref_key_theme_mode)
    return ThemeMode.fromPrefValue(preferences.safeGetString(key, ThemeMode.SYSTEM.prefValue))
}

/** Resolves a [ThemeMode] to an actual dark/light boolean, following system config when requested. */
fun resolveIsDarkTheme(
    activity: ComponentActivity,
    themeMode: ThemeMode,
): Boolean =
    when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM ->
            (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

/**
 * Reactive Compose read: backed by the same preference [ChimeroidSettingsList] writes to, so
 * every screen using [AppTheme] live-updates the instant the user changes the setting.
 */
@Composable
fun rememberThemeModePreference(): ThemeMode {
    val state =
        indexPreferenceState(
            id = R.string.pref_key_theme_mode,
            default = ThemeMode.SYSTEM.prefValue,
            values = ThemeMode.prefValues,
        )
    return ThemeMode.entries.getOrElse(state.value) { ThemeMode.SYSTEM }
}

/**
 * Replaces the previous hardcoded `SystemBarStyle.dark(...)` calls duplicated in every
 * edge-to-edge Activity. Those forced dark (light-colored) system bar icons unconditionally,
 * which made status/navigation bar icons invisible once light theme was actually reachable.
 * Call this instead of [enableEdgeToEdge] directly so bar icon contrast always tracks the
 * resolved theme.
 */
fun ComponentActivity.enableEdgeToEdgeForTheme() {
    val isDark = resolveIsDarkTheme(this, readThemeModePreference(this))
    val style =
        if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            // minSdk 23 is exactly the floor where light system-bar icons are supported,
            // so the pre-M fallback scrim SystemBarStyle.light() requires is never drawn.
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
