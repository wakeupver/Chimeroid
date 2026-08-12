package com.swordfish.chimeroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

typealias SafeIntPreferenceSettingValueState = SafePrimitivePreferenceSettingValueState<Int>

@Composable
fun rememberSafePreferenceIntSettingState(
    key: String,
    defaultValue: Int,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SafeIntPreferenceSettingValueState {
    return remember {
        SafeIntPreferenceSettingValueState(
            preferences = preferences,
            key = key,
            defaultValue = defaultValue,
            read = { prefs, k, default -> prefs.safeGetInt(k, default) },
            write = { editor, k, v -> editor.putInt(k, v) },
        )
    }
}
