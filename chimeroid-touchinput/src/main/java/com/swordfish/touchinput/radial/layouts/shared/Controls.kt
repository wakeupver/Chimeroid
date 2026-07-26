package com.swordfish.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.controls.ChimeroidControlAnalog
import com.swordfish.touchinput.radial.controls.ChimeroidControlButton
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonSelect(position: Int = 0) {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(120f - 30f * position),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
                icon = R.drawable.button_select,
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL1() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(90f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
                label = "L1",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL2() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                label = "L2",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR1() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(90f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
                label = "R1",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR2() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
                label = "R2",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
                label = "L",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
                label = "R",
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonStart(position: Int = 0) {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(60f + 30f * position),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
                icon = R.drawable.button_start,
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenu(settings: TouchControllerSettingsManager.Settings) {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(-60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
                icon = R.drawable.button_menu,
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenuPlaceholder(settings: TouchControllerSettingsManager.Settings) {
    with(padKitScope) {
        with(layoutScope) {
            Box(
                modifier =
                    Modifier.radialPosition(
                        -120f - 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
                    ),
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogLeft() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlAnalog(
                modifier =
                    Modifier
                        .radialPosition(-80f)
                        .radialScale(2.0f),
                id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK),
                analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogRight() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlAnalog(
                modifier =
                    Modifier
                        .radialPosition(+80f - 180f)
                        .radialScale(2.0f),
                id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
                analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
            )
        }
    }
}

context(padKitScope: PadKitScope, layoutScope: LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonCoin() {
    with(padKitScope) {
        with(layoutScope) {
            ChimeroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
                icon = R.drawable.button_coin,
            )
        }
    }
}

object ComposeTouchLayouts {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_LEFT_STICK = 1
    const val MOTION_SOURCE_RIGHT_STICK = 2
    const val MOTION_SOURCE_DPAD_AND_LEFT_STICK = 3
    const val MOTION_SOURCE_RIGHT_DPAD = 4
}
