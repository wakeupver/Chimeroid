package com.swordfish.chimeroid.app.shared.game.macro

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

class MacroButtonsManager(private val sharedPreferences: SharedPreferences) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getMacroButtons(controllerID: String): List<MacroButton> {
        val key = buildKey(controllerID)
        val raw = sharedPreferences.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(MacroButton.serializer()), raw)
        } catch (e: Exception) {
            Timber.e(e, "MacroButtonsManager: failed to decode macros for $controllerID")
            emptyList()
        }
    }

    fun saveMacroButtons(controllerID: String, macros: List<MacroButton>) {
        val key = buildKey(controllerID)
        try {
            val serialized = json.encodeToString(ListSerializer(MacroButton.serializer()), macros)
            sharedPreferences.edit { putString(key, serialized) }
        } catch (e: Exception) {
            Timber.e(e, "MacroButtonsManager: failed to encode macros for $controllerID")
        }
    }

    fun clearMacroButtons(controllerID: String) {
        sharedPreferences.edit { remove(buildKey(controllerID)) }
    }

    private fun buildKey(controllerID: String) = "macro_buttons_$controllerID"
}
