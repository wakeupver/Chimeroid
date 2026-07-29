package com.swordfish.touchinput.radial.layouts

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.layouts.radial.LayoutRadial
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

val LocalPadKitScope =
    compositionLocalOf<PadKitScope> {
        error("PadKitScope is missing")
    }

@Composable
fun PadKitScope.BaseLayoutLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val interpolatedDialSize =
        remember(settings.scale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                settings.scale,
            )
        }

    CompositionLocalProvider(LocalPadKitScope provides this) {
        LayoutRadial(
            modifier =
                modifier
                    .absolutePadding(
                        left = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                        bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                    )
                    .padding(LocalChimeroidPadTheme.current.padding),
            primaryDial = primaryDial,
            secondaryDials = secondaryDials,
            primaryDialMaxSize = 160.dp * interpolatedDialSize,
            secondaryDialsBaseRotationInDegrees = settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
        )
    }
}

@Composable
fun PadKitScope.BaseLayoutRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val interpolatedDialSize =
        remember(settings.scale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                settings.scale,
            )
        }

    CompositionLocalProvider(LocalPadKitScope provides this) {
        LayoutRadial(
            modifier =
                modifier
                    .absolutePadding(
                        right = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginX,
                        bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * settings.marginY,
                    )
                    .padding(LocalChimeroidPadTheme.current.padding),
            primaryDial = primaryDial,
            secondaryDials = secondaryDials,
            primaryDialMaxSize = 160.dp * interpolatedDialSize,
            secondaryDialsBaseRotationInDegrees = -settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
        )
    }
}
