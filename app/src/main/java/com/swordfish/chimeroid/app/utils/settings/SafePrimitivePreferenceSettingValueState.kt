package com.swordfish.chimeroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.alorma.compose.settings.storage.base.SettingValueState

class SafePrimitivePreferenceSettingValueState<T>(
    private val preferences: SharedPreferences,
    val key: String,
    private val defaultValue: T,
    private val read: (preferences: SharedPreferences, key: String, default: T) -> T,
    private val write: (editor: SharedPreferences.Editor, key: String, value: T) -> Unit,
) : SettingValueState<T> {
    private var _value by mutableStateOf(read(preferences, key, defaultValue))

    override var value: T
        set(newValue) {
            _value = newValue
            preferences.edit { write(this, key, newValue) }
        }
        get() = _value

    override fun reset() {
        value = defaultValue
    }
}
