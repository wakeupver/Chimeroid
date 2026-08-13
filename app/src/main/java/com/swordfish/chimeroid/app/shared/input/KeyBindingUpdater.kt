package com.swordfish.chimeroid.app.shared.input

import android.content.Context
import android.view.KeyEvent

interface KeyBindingUpdater {
    fun getTitle(context: Context): String

    fun getMessage(context: Context): String

    fun handleKeyEvent(event: KeyEvent): Boolean
}
