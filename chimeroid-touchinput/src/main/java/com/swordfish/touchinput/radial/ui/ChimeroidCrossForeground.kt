package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme
import gg.padkit.ui.DefaultCrossForeground
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun ChimeroidCrossForeground(
    allowDiagonals: Boolean,
    directionState: State<Offset>,
) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = allowDiagonals,
        leftDial = {
            ChimeroidCrossButton(it, Icons.AutoMirrored.Filled.KeyboardArrowLeft)
        },
        rightDial = {
            ChimeroidCrossButton(it, Icons.AutoMirrored.Filled.KeyboardArrowRight)
        },
        topDial = {
            ChimeroidCrossButton(it, Icons.Default.KeyboardArrowUp)
        },
        bottomDial = {
            ChimeroidCrossButton(it, Icons.Default.KeyboardArrowDown)
        },
        foregroundComposite = {
            ChimeroidCompositeForeground(it)
        },
    )
}

@Composable
private fun ChimeroidCrossButton(
    pressedState: State<Boolean>,
    imageVector: ImageVector,
) {
    ChimeroidButtonForeground(
        pressed = pressedState,
        label = { },
        icon = {
            Icon(
                modifier = Modifier.size(maxWidth * 0.5f, maxHeight * 0.5f),
                imageVector = imageVector,
                contentDescription = "",
                tint = LocalChimeroidPadTheme.current.icons(pressedState.value),
            )
        },
    )
}
