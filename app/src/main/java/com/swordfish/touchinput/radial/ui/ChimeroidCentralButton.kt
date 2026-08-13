package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme

@Composable
fun ChimeroidCentralButton(
    pressedState: State<Boolean>,
    label: String? = null,
) {
    val theme = LocalChimeroidPadTheme.current
    Box(modifier = Modifier.padding(theme.padding)) {
        ChimeroidControlBackground()
        ChimeroidButtonForeground(
            pressed = pressedState,
            label = label,
        )
    }
}
