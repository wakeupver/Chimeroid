package com.swordfish.chimeroid.app.mobile.feature.game

import android.graphics.RectF as AndroidRectF
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel

// ─────────────────────────────────────────────────────────────────────────────

private const val SPLIT_MIN     = 0.20f
private const val SPLIT_MAX     = 0.80f
private const val SPLIT_DEFAULT = 0.50f
private val DIVIDER_H: Dp = 22.dp

// ─────────────────────────────────────────────────────────────────────────────
// Public composable – drop inside the GAME_VIEW Box in MobileGameScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders two resizable panels (top = primary screen, bottom = secondary screen)
 * separated by a drag handle.  Tracks Compose layout bounds and pushes viewport
 * coordinates to the GL layer via [viewModel.applyDualScreenLayout].
 *
 * Designed to be placed inside the same Box that normally tracks the single
 * viewport, so it inherits the game-view area (above the touch pads).
 *
 * @param fullScreenPosition  Bounds of the full-screen GLRetroView surface,
 *                            tracked by the parent composable.
 * @param viewModel           Receives dual-screen layout updates.
 */
@Composable
fun DualScreenPanels(
    fullScreenPosition: State<Rect?>,
    viewModel: BaseGameScreenViewModel,
) {
    // ── State ─────────────────────────────────────────────────────────────────
    var splitFraction by remember { mutableFloatStateOf(SPLIT_DEFAULT) }

    val topPanelPos    = remember { mutableStateOf<Rect?>(null) }
    val bottomPanelPos = remember { mutableStateOf<Rect?>(null) }

    // ── Push to GL whenever positions update ──────────────────────────────────
    val fullPos = fullScreenPosition.value
    val top     = topPanelPos.value
    val bot     = bottomPanelPos.value

    LaunchedEffect(fullPos, top, bot) {
        if (fullPos == null || top == null || bot == null) return@LaunchedEffect
        val fullRectF = AndroidRectF(fullPos.left, fullPos.top, fullPos.right, fullPos.bottom)
        viewModel.applyDualScreenLayout(fullRectF, top, bot)
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Column(modifier = Modifier.fillMaxSize()) {

            // Top screen panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(splitFraction)
                    .onGloballyPositioned { topPanelPos.value = it.boundsInRoot() },
            )

            // Drag-handle divider
            DualScreenDivider(
                totalAvailablePx = totalPx,
                onDrag = { delta ->
                    splitFraction = (splitFraction + delta).coerceIn(SPLIT_MIN, SPLIT_MAX)
                },
            )

            // Bottom screen panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight((1f - splitFraction).coerceAtLeast(SPLIT_MIN))
                    .onGloballyPositioned { bottomPanelPos.value = it.boundsInRoot() },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Divider / drag handle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DualScreenDivider(
    totalAvailablePx: Float,
    onDrag: (fractionDelta: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DIVIDER_H)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .pointerInput(totalAvailablePx) {
                detectVerticalDragGestures { _, dragAmountPx ->
                    onDrag(dragAmountPx / totalAvailablePx)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Visual pill
        Surface(
            modifier = Modifier.size(width = 48.dp, height = 5.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        ) {}
    }
}
