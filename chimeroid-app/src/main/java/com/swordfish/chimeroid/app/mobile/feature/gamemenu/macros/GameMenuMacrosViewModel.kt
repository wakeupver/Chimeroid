package com.swordfish.chimeroid.app.mobile.feature.gamemenu.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.app.shared.game.macro.MacroButton
import com.swordfish.chimeroid.app.shared.game.macro.MacroButtonsManager
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Game Menu's "Macros" screen (list / add / delete).
 *
 * Reads and writes go through the same [MacroButtonsManager] (SharedPreferences-backed)
 * used by the live game session, keyed by [controllerId] — edits made here are picked up
 * by the running game's own macro view-model the next time it (re)applies that controller
 * key. Live drag-to-reposition stays on the game screen itself, since it requires the
 * running emulator view to be visible; this view-model only owns list CRUD.
 */
class GameMenuMacrosViewModel(
    private val macroButtonsManager: MacroButtonsManager,
    private val controllerId: String,
) : ViewModel() {

    class Factory(
        macroButtonsManager: MacroButtonsManager,
        controllerId: String,
    ) : ViewModelProvider.Factory by viewModelFactory({
            GameMenuMacrosViewModel(macroButtonsManager, controllerId)
        })

    private val _macroButtons = MutableStateFlow<List<MacroButton>>(emptyList())
    val macroButtons: StateFlow<List<MacroButton>> = _macroButtons.asStateFlow()

    init {
        // Dispatched off the main thread: avoids a first-access SharedPreferences
        // stall blocking the UI while the Game Menu opens.
        viewModelScope.launch(Dispatchers.IO) {
            _macroButtons.value = macroButtonsManager.getMacroButtons(controllerId)
        }
    }

    fun addOrUpdateMacro(macro: MacroButton) {
        val current = _macroButtons.value.toMutableList()
        val idx = current.indexOfFirst { it.id == macro.id }
        if (idx >= 0) current[idx] = macro else current.add(macro)
        persist(current)
    }

    fun deleteMacro(macroId: String) {
        persist(_macroButtons.value.filter { it.id != macroId })
    }

    /**
     * [MacroButtonsManager.saveMacroButtons] persists via [android.content.SharedPreferences.Editor.apply],
     * which is already asynchronous — no extra dispatcher hop is needed here (mirrors the
     * live-game macro view-model's own persistence call).
     */
    private fun persist(buttons: List<MacroButton>) {
        _macroButtons.value = buttons
        macroButtonsManager.saveMacroButtons(controllerId, buttons)
    }
}
