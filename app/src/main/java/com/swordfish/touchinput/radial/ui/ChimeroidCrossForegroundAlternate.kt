package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.swordfish.chimeroid.R
import gg.padkit.ui.DefaultCrossForeground

@Composable
fun ChimeroidCrossForegroundAlternate(directionState: State<Offset>) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = false,
        leftDial = {
            ChimeroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_left,
            )
        },
        rightDial = {
            ChimeroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_right,
            )
        },
        topDial = {
            ChimeroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_up,
            )
        },
        bottomDial = {
            ChimeroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_down,
            )
        },
        foregroundComposite = {
            ChimeroidCompositeForeground(it)
        },
    )
}
