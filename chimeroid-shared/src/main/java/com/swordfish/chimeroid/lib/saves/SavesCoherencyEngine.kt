package com.swordfish.chimeroid.lib.saves

import com.swordfish.chimeroid.lib.library.CoreID
import com.swordfish.chimeroid.lib.library.db.entity.Game

class SavesCoherencyEngine(val savesManager: SavesManager, val statesManager: StatesManager) {
    suspend fun shouldDiscardAutoSaveState(
        game: Game,
        coreID: CoreID,
    ): Boolean {
        val autoSRAM = savesManager.getSaveRAMInfo(game)
        val autoSave = statesManager.getAutoSaveInfo(game, coreID)
        return autoSRAM.exists && autoSave.exists && autoSRAM.date > autoSave.date + TOLERANCE
    }

    companion object {
        private const val TOLERANCE = 30L * 1000L
    }
}
