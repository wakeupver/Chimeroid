package com.swordfish.chimeroid.app.mobile.feature.input

import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.shared.input.KeyBindingUpdater
import com.swordfish.chimeroid.app.shared.input.ShortcutBindingUpdater
import javax.inject.Inject

class GamePadShortcutBindingActivity : AbstractGamePadBindingActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    override fun createBindingUpdater(): KeyBindingUpdater =
        ShortcutBindingUpdater(inputDeviceManager, intent)

    @dagger.Module
    abstract class Module
}
