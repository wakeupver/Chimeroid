package com.swordfish.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.controls.ChimeroidControlAnalog
import com.swordfish.touchinput.radial.controls.ChimeroidControlButton
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonSelect(position: Int = 0) {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(120f - 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_select,
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonL1() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L1",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonL2() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
        label = "L2",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonR1() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R1",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonR2() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
        label = "R2",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonL() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonR() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R",
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonStart(position: Int = 0) {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(60f + 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        icon = R.drawable.button_start,
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonMenu(settings: TouchControllerSettingsManager.Settings) {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(-60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        icon = R.drawable.button_menu,
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonMenuPlaceholder(settings: TouchControllerSettingsManager.Settings) {
    Box(
        modifier =
            Modifier.radialPosition(
                -120f - 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
            ),
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryAnalogLeft() {
    ChimeroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(-80f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryAnalogRight() {
    ChimeroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(+80f - 180f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
    )
}

@Composable
fun LayoutRadialSecondaryDialsScope.SecondaryButtonCoin() {
    ChimeroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_coin,
    )
}

object ComposeTouchLayouts {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_LEFT_STICK = 1
    const val MOTION_SOURCE_RIGHT_STICK = 2
    const val MOTION_SOURCE_DPAD_AND_LEFT_STICK = 3
    const val MOTION_SOURCE_RIGHT_DPAD = 4
}
