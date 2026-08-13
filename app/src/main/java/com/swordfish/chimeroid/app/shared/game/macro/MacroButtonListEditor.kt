package com.swordfish.chimeroid.app.shared.game.macro

object MacroButtonListEditor {
    fun addOrUpdate(
        buttons: List<MacroButton>,
        macro: MacroButton,
    ): List<MacroButton> {
        val current = buttons.toMutableList()
        val idx = current.indexOfFirst { it.id == macro.id }
        if (idx >= 0) current[idx] = macro else current.add(macro)
        return current
    }

    fun delete(
        buttons: List<MacroButton>,
        macroId: String,
    ): List<MacroButton> {
        return buttons.filter { it.id != macroId }
    }
}
