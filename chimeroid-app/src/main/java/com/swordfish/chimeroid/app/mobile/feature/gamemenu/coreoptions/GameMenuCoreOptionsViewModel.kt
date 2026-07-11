package com.swordfish.chimeroid.app.mobile.feature.gamemenu.coreoptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.map

class GameMenuCoreOptionsViewModel(val inputDeviceManager: InputDeviceManager) : ViewModel() {
    class Factory(
        inputDeviceManager: InputDeviceManager,
    ) : ViewModelProvider.Factory by viewModelFactory({ GameMenuCoreOptionsViewModel(inputDeviceManager) })

    val connectedGamePads = inputDeviceManager.getGamePadsObservable().map { it.size }
}
