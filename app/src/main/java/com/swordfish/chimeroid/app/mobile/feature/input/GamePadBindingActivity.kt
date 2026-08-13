package com.swordfish.chimeroid.app.mobile.feature.input

import com.swordfish.chimeroid.app.shared.input.InputBindingUpdater
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.shared.input.KeyBindingUpdater
import javax.inject.Inject

class GamePadBindingActivity : AbstractGamePadBindingActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    override fun createBindingUpdater(): KeyBindingUpdater =
        InputBindingUpdater(inputDeviceManager, intent)

    @dagger.Module
    abstract class Module
}
