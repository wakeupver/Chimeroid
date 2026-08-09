package com.swordfish.chimeroid.app.shared.game.viewmodel

import android.view.KeyEvent
import gg.padkit.inputevents.InputEvent

fun GameViewModelRetroGameView.dispatchButtonEvent(event: InputEvent.Button) {
    val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
    retroGameView?.sendKeyEvent(action, event.id)
}
