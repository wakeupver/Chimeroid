package com.swordfish.chimeroid.app.shared.game.macro

import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.chimeroid.app.shared.game.viewmodel.dispatchButtonEvent
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

    // ------------------------------------------------------------------
    // Controller key wiring
    // ------------------------------------------------------------------

    fun setControllerKey(key: String) {
        if (key == controllerKey) return
        controllerKey = key
        _macroButtons.value = macroButtonsManager.getMacroButtons(key)
        Timber.d("MacroButtons: loaded ${_macroButtons.value.size} macros for $key")
    }

    /**
     * Forces a re-read of the current controller's macros from persisted storage.
     *
     * [setControllerKey] intentionally no-ops when the key is unchanged — it only exists to
     * react to controller *switches*. The Game Menu's macro manager runs as a separate screen
     * with its own in-memory list over the same [MacroButtonsManager]-backed store, so edits
     * made there (add/delete/reposition) never reach this live copy on their own; without this,
     * newly added macros persist correctly but never render on the live game overlay until the
     * controller key happens to change or the session restarts. Call this whenever control
     * returns from a place that could have mutated macros for [controllerKey].
     */
    fun reloadMacros() {
        _macroButtons.value = macroButtonsManager.getMacroButtons(controllerKey)
        Timber.d("MacroButtons: reloaded ${_macroButtons.value.size} macros for $controllerKey")
    }

    // ------------------------------------------------------------------
    // Edit-mode toggle
    // ------------------------------------------------------------------

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    fun addOrUpdateMacro(macro: MacroButton) {
        val current = _macroButtons.value.toMutableList()
        val idx = current.indexOfFirst { it.id == macro.id }
        if (idx >= 0) current[idx] = macro else current.add(macro)
        persist(current)
    }

    fun deleteMacro(macroId: String) {
        val updated = _macroButtons.value.filter { it.id != macroId }
        persist(updated)
    }

    fun updateMacroPosition(macroId: String, xFraction: Float, yFraction: Float) {
        val updated = _macroButtons.value.map { btn ->
            if (btn.id == macroId) btn.copy(xFraction = xFraction, yFraction = yFraction)
            else btn
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

    // ------------------------------------------------------------------
    // Key firing — press/release split for hold support
    // ------------------------------------------------------------------

    /**
     * Called when the user's finger touches the macro button: sends ACTION_DOWN for every
     * key immediately, via the same [InputEvent.Button] dispatch real touch controls use.
     * Keys stay held until [releaseMacro] is called on finger-up.
     */
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

    /**
     * Called when the user's finger lifts off the macro button: sends ACTION_UP for every
     * held key (reversed order), via the same [InputEvent.Button] dispatch real touch controls use.
     */
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
