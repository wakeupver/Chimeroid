package com.swordfish.touchinput.radial.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.controls.ChimeroidControlCross
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryAnalogLeft
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonAnalogL
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonAnalogR
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.layouts.shared.PSXFaceButtons
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id

// Same as PSPLeft/PSPRight (flycast's RetroPad mapping is button-for-button identical to
// PSP's for the D-pad/analog stick/ABXY/Start), except L/R: Dreamcast's are analog-capable
// triggers, which flycast reads as JOYPAD_L2/R2 rather than the plain digital JOYPAD_L/R
// PSP's shoulder buttons use, so SecondaryButtonAnalogL/R replace SecondaryButtonL/R here.

@Composable
fun PadKitScope.DreamcastLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { ChimeroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            SecondaryButtonAnalogL()
            SecondaryButtonSelect(position = 2)
            SecondaryButtonMenuPlaceholder(settings)
            SecondaryAnalogLeft()
        },
    )
}

@Composable
fun PadKitScope.DreamcastRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            PSXFaceButtons()
        },
        secondaryDials = {
            SecondaryButtonAnalogR()
            SecondaryButtonStart(position = 2)
            Box(
                modifier =
                    Modifier
                        .radialPosition(+80f - 180f)
                        .radialScale(2.0f),
            )
            SecondaryButtonMenu(settings)
        },
    )
}
