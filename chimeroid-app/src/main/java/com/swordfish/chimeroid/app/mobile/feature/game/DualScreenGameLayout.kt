package com.swordfish.chimeroid.app.mobile.feature.game

import android.graphics.RectF as AndroidRectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel
import kotlin.math.roundToInt

/**
 * Invisible tracking panels for the dual-screen GL viewport.
 * Must be placed as a direct child of the full-screen PadKit Box
 * (sibling of ConstraintLayout, NOT inside the CONSTRAINTS_GAME_VIEW Box),
 * so that panels can span the full surface including the controls area.
 */
@Composable
fun DualScreenPanels(
    fullScreenPosition: State<Rect?>,
    layout: DualScreenLayout,
    viewModel: BaseGameScreenViewModel,
) {
    val topPanelPos    = remember { mutableStateOf<Rect?>(null) }
    val bottomPanelPos = remember { mutableStateOf<Rect?>(null) }

    val fullPos = fullScreenPosition.value
    val top     = topPanelPos.value
    val bot     = bottomPanelPos.value

    LaunchedEffect(fullPos, top, bot) {
        if (fullPos == null || top == null || bot == null) return@LaunchedEffect
        viewModel.applyDualScreenLayout(
            AndroidRectF(fullPos.left, fullPos.top, fullPos.right, fullPos.bottom),
            top,
            bot,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val density    = LocalDensity.current

        // Top screen tracking box
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (layout.top.xFraction * containerW).roundToInt(),
                        (layout.top.yFraction * containerH).roundToInt(),
                    )
                }
                .requiredSize(
                    with(density) { (layout.top.widthFraction  * containerW).toDp() },
                    with(density) { (layout.top.heightFraction * containerH).toDp() },
                )
                .onGloballyPositioned { topPanelPos.value = it.boundsInRoot() },
        )

        // Bottom screen tracking box
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (layout.bottom.xFraction * containerW).roundToInt(),
                        (layout.bottom.yFraction * containerH).roundToInt(),
                    )
                }
                .requiredSize(
                    with(density) { (layout.bottom.widthFraction  * containerW).toDp() },
                    with(density) { (layout.bottom.heightFraction * containerH).toDp() },
                )
                .onGloballyPositioned { bottomPanelPos.value = it.boundsInRoot() },
        )
    }
}
