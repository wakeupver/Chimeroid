package com.swordfish.chimeroid.app.shared.input.chimeroiddevice

import android.content.Context
import android.view.InputDevice
import com.swordfish.chimeroid.app.shared.input.InputKey
import com.swordfish.chimeroid.app.shared.input.RetroKey
import com.swordfish.chimeroid.app.shared.settings.GameShortcutType

interface ChimeroidInputDevice {
    fun getCustomizableKeys(): List<RetroKey>

    fun getDefaultBindings(): Map<InputKey, RetroKey>

    fun isSupported(): Boolean

    fun isEnabledByDefault(appContext: Context): Boolean

    fun getSupportedShortcuts(): List<GameShortcutType>
}

fun InputDevice?.getChimeroidInputDevice(): ChimeroidInputDevice {
    return when {
        this == null -> ChimeroidInputDeviceUnknown
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD -> ChimeroidInputDeviceGamePad(this)
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD -> ChimeroidInputDeviceKeyboard(this)
        else -> ChimeroidInputDeviceUnknown
    }
}
