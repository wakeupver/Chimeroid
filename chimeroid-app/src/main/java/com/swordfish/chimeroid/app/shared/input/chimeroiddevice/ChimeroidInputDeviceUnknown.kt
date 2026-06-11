package com.swordfish.chimeroid.app.shared.input.chimeroiddevice

import android.content.Context
import com.swordfish.chimeroid.app.shared.input.InputKey
import com.swordfish.chimeroid.app.shared.input.RetroKey
import com.swordfish.chimeroid.app.shared.settings.GameShortcutType

object ChimeroidInputDeviceUnknown : ChimeroidInputDevice {
    override fun getDefaultBindings(): Map<InputKey, RetroKey> = emptyMap()

    override fun isSupported(): Boolean = false

    override fun isEnabledByDefault(appContext: Context): Boolean = false

    override fun getSupportedShortcuts(): List<GameShortcutType> = emptyList()

    override fun getCustomizableKeys(): List<RetroKey> = emptyList()
}
