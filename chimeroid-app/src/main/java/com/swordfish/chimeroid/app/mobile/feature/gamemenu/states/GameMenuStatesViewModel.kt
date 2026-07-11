package com.swordfish.chimeroid.app.mobile.feature.gamemenu.states

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.chimeroid.app.shared.gamemenu.GameMenuHelper
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.lib.saves.StatesManager
import com.swordfish.chimeroid.lib.saves.StatesPreviewManager
import kotlinx.coroutines.flow.flow

class GameMenuStatesViewModel(
    private val application: Application,
    private val gameMenuRequest: GameMenuActivity.GameMenuRequest,
    private val statesManager: StatesManager,
    private val disableMissingEntries: Boolean,
    private val statesPreviewManager: StatesPreviewManager,
) : ViewModel() {
    class Factory(
        application: Application,
        gameMenuRequest: GameMenuActivity.GameMenuRequest,
        statesManager: StatesManager,
        disableMissingEntries: Boolean,
        statesPreviewManager: StatesPreviewManager,
    ) : ViewModelProvider.Factory by viewModelFactory({
            GameMenuStatesViewModel(
                application,
                gameMenuRequest,
                statesManager,
                disableMissingEntries,
                statesPreviewManager,
            )
        })

    data class StateEntry(
        val title: String,
        val description: String,
        val enabled: Boolean,
        val preview: Bitmap?,
    )

    data class State(val entries: List<StateEntry> = emptyList())

    val uiStates =
        flow {
            val slotsInfo = statesManager.getSavedSlotsInfo(gameMenuRequest.game, gameMenuRequest.coreConfig.coreID)

            val entries =
                slotsInfo.mapIndexed { index, slotInfo ->
                    val title =
                        application.applicationContext.getString(
                            R.string.game_menu_state,
                            (index + 1).toString(),
                        )
                    val description = GameMenuHelper.getSaveStateDescription(slotInfo)
                    val isEnabled = !disableMissingEntries || slotInfo.exists
                    val preview =
                        GameMenuHelper.getSaveStateBitmap(
                            application.applicationContext,
                            statesPreviewManager,
                            slotInfo,
                            gameMenuRequest.game,
                            gameMenuRequest.coreConfig.coreID,
                            index,
                        )

                    StateEntry(title, description, isEnabled, preview)
                }

            emit(State(entries))
        }
}
