package com.swordfish.chimeroid.app.shared.game

import android.app.Activity
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.chimeroid.common.displayToast
import com.swordfish.chimeroid.lib.core.CoresSelection
import com.swordfish.chimeroid.lib.library.GameSystem
import com.swordfish.chimeroid.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        GlobalScope.launch {
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(activity, coreConfig, game, loadSave)
        }

        return true
    }
}
