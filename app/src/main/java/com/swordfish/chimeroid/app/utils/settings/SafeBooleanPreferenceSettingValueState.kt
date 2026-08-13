package com.swordfish.chimeroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

typealias SafeBooleanPreferenceSettingValueState = SafePrimitivePreferenceSettingValueState<Boolean>

@Composable
fun rememberSafePreferenceBooleanSettingState(
    key: String,
    defaultValue: Boolean,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SafeBooleanPreferenceSettingValueState {
    return remember {
        SafeBooleanPreferenceSettingValueState(
            preferences = preferences,
            key = key,
            defaultValue = defaultValue,
            read = { prefs, k, default -> prefs.safeGetBoolean(k, default) },
            write = { editor, k, v -> editor.putBoolean(k, v) },
        )
    }
}
