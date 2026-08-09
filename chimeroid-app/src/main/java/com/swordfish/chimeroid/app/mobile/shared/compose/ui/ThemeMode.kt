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

fun readThemeModePreference(activity: ComponentActivity): ThemeMode {
    val preferences = SharedPreferencesHelper.getSharedPreferences(activity)
    val key = activity.getString(R.string.pref_key_theme_mode)
    return ThemeMode.fromPrefValue(preferences.safeGetString(key, ThemeMode.SYSTEM.prefValue))
}

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

fun ComponentActivity.enableEdgeToEdgeForTheme() {
    val isDark = resolveIsDarkTheme(this, readThemeModePreference(this))
    val style =
        if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {

            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
