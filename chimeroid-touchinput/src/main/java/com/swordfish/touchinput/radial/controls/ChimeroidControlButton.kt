package com.swordfish.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme
import com.swordfish.touchinput.radial.ui.ChimeroidButtonForeground
import com.swordfish.touchinput.radial.ui.ChimeroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id

@Composable
fun PadKitScope.ChimeroidControlButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String? = null,
    icon: Int? = null,
) {
    val theme = LocalChimeroidPadTheme.current
    ControlButton(
        modifier = modifier.padding(theme.padding),
        id = id,
        foreground = { ChimeroidButtonForeground(pressed = it, icon = icon, label = label) },
        background = { ChimeroidControlBackground() },
    )
}
