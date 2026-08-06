package com.swordfish.touchinput.radial.layouts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.controls.ChimeroidControlCross
import com.swordfish.touchinput.radial.layouts.shared.ABYXFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryAnalogLeft
import com.swordfish.touchinput.radial.layouts.shared.SecondaryAnalogRight
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonAnalogL
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonAnalogR
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonZ
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id

// GameCube has only one analog-capable trigger per side, so it reuses the JOYPAD_L2/R2-
// bound buttons the same way DreamcastLeft/Right do. Unlike Dreamcast it has no Select, but
// it does have a real second stick (the C-Stick) -- which is what gives this layout
// PSX_DUALSHOCK's dual-analog shape rather than Dreamcast's single-stick one. The Z shoulder
// button has no RetroPad id of its own (see SecondaryButtonZ) and there's no on-console
// touch screen, so ABYXFaceButtons' plain lettered diamond is reused as-is for A/B/X/Y.

@Composable
fun PadKitScope.GameCubeLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { ChimeroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            SecondaryButtonAnalogL()
            SecondaryButtonMenuPlaceholder(settings)
            SecondaryAnalogLeft()
        },
    )
}

@Composable
fun PadKitScope.GameCubeRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            ABYXFaceButtons()
        },
        secondaryDials = {
            SecondaryButtonZ()
            SecondaryButtonAnalogR()
            SecondaryButtonStart(position = 2)
            SecondaryAnalogRight()
            SecondaryButtonMenu(settings)
        },
    )
}
