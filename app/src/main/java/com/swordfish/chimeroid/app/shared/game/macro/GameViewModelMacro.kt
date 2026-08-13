package com.swordfish.chimeroid.app.shared.game.macro

import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.chimeroid.app.shared.game.viewmodel.dispatchButtonEvent
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class GameViewModelMacro(
    private val macroButtonsManager: MacroButtonsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
) {
    private var controllerKey: String = "default"

    private val _macroButtons = MutableStateFlow<List<MacroButton>>(emptyList())
    val macroButtons: StateFlow<List<MacroButton>> = _macroButtons.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun setControllerKey(key: String) {
        if (key == controllerKey) return
        controllerKey = key
        _macroButtons.value = macroButtonsManager.getMacroButtons(key)
        Timber.d("MacroButtons: loaded ${_macroButtons.value.size} macros for $key")
    }

    fun reloadMacros() {
        _macroButtons.value = macroButtonsManager.getMacroButtons(controllerKey)
        Timber.d("MacroButtons: reloaded ${_macroButtons.value.size} macros for $controllerKey")
    }

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    fun addOrUpdateMacro(macro: MacroButton) {
        persist(MacroButtonListEditor.addOrUpdate(_macroButtons.value, macro))
    }

    fun deleteMacro(macroId: String) {
        persist(MacroButtonListEditor.delete(_macroButtons.value, macroId))
    }

    fun updateMacroPosition(macroId: String, xFraction: Float, yFraction: Float) {
        val updated = _macroButtons.value.map { btn ->
            if (btn.id == macroId) btn.copy(xFraction = xFraction, yFraction = yFraction)
            else btn
        }
        persist(updated)
    }

    fun updateMacroScale(macroId: String, scale: Float) {
        val clamped = scale.coerceIn(
            TouchControllerSettingsManager.MIN_SCALE,
            TouchControllerSettingsManager.MAX_SCALE,
        )
        val updated = _macroButtons.value.map { btn ->
            if (btn.id == macroId) btn.copy(scale = clamped) else btn
        }
        persist(updated)
    }

    fun clearAll() {
        persist(emptyList())
    }

    private fun persist(buttons: List<MacroButton>) {
        _macroButtons.value = buttons
        macroButtonsManager.saveMacroButtons(controllerKey, buttons)
    }

    fun pressMacro(macro: MacroButton) {
        if (macro.keyCodes.isEmpty()) return
        scope.launch {
            try {
                macro.keyCodes.forEach {
                    retroGameView.dispatchButtonEvent(InputEvent.Button(id = it, pressed = true))
                }
            } catch (e: Exception) {
                Timber.e(e, "MacroButtons: error pressing macro '${macro.label}'")
            }
        }
    }

    fun releaseMacro(macro: MacroButton) {
        if (macro.keyCodes.isEmpty()) return
        scope.launch {
            try {
                macro.keyCodes.reversed().forEach {
                    retroGameView.dispatchButtonEvent(InputEvent.Button(id = it, pressed = false))
                }
            } catch (e: Exception) {
                Timber.e(e, "MacroButtons: error releasing macro '${macro.label}'")
            }
        }
    }
}
