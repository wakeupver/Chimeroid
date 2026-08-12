package com.swordfish.chimeroid.app.mobile.feature.gamemenu.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.app.shared.game.macro.MacroButton
import com.swordfish.chimeroid.app.shared.game.macro.MacroButtonListEditor
import com.swordfish.chimeroid.app.shared.game.macro.MacroButtonsManager
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

        viewModelScope.launch(Dispatchers.IO) {
            _macroButtons.value = macroButtonsManager.getMacroButtons(controllerId)
        }
    }

    fun addOrUpdateMacro(macro: MacroButton) {
        persist(MacroButtonListEditor.addOrUpdate(_macroButtons.value, macro))
    }

    fun deleteMacro(macroId: String) {
        persist(MacroButtonListEditor.delete(_macroButtons.value, macroId))
    }

    private fun persist(buttons: List<MacroButton>) {
        _macroButtons.value = buttons
        macroButtonsManager.saveMacroButtons(controllerId, buttons)
    }
}
