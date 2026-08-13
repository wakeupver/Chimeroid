package com.swordfish.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.swordfish.chimeroid.R
import com.swordfish.touchinput.radial.controls.ChimeroidControlFaceButtons
import com.swordfish.touchinput.radial.ui.ChimeroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.ABYXFaceButtons() {
    ChimeroidControlFaceButtons(
        ids =
            persistentListOf(
                Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                Id.Key(KeyEvent.KEYCODE_BUTTON_X),
            ),
        idsForegrounds =
            persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { ChimeroidButtonForeground(pressed = it, label = "A") },
                Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { ChimeroidButtonForeground(pressed = it, label = "B") },
                Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { ChimeroidButtonForeground(pressed = it, label = "Y") },
                Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { ChimeroidButtonForeground(pressed = it, label = "X") },
            ),
    )
}

@Composable
fun PadKitScope.PSXFaceButtons() {
    ChimeroidControlFaceButtons(
        ids =
            persistentListOf(
                Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                Id.Key(KeyEvent.KEYCODE_BUTTON_X),
            ),
        idsForegrounds =
            persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { ChimeroidButtonForeground(pressed = it, icon = R.drawable.psx_circle) },
                Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { ChimeroidButtonForeground(pressed = it, icon = R.drawable.psx_cross) },
                Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { ChimeroidButtonForeground(pressed = it, icon = R.drawable.psx_square) },
                Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { ChimeroidButtonForeground(pressed = it, icon = R.drawable.psx_triangle) },
            ),
    )
}
