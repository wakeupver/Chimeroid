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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel

// ─────────────────────────────────────────────────────────────────────────────

private const val SPLIT_MIN     = 0.20f
private const val SPLIT_MAX     = 0.80f
private const val SPLIT_DEFAULT = 0.50f
private val DIVIDER_H = 22.dp

// ─────────────────────────────────────────────────────────────────────────────
// Public composable – drop inside the GAME_VIEW Box in MobileGameScreen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders two resizable panels separated by a drag handle, and immediately
 * pushes viewport coordinates to the GL layer via [viewModel.applyDualScreenLayout].
 *
 * Panel positions are computed **mathematically** from [fullScreenPosition] +
 * [splitFraction] rather than waiting for individual Compose layout measurements.
 * This eliminates the 2-5 frame delay that caused a visible stutter/freeze at
 * game start: the GL dual-screen config is applied on the very first frame that
 * [fullScreenPosition] is non-null.
 *
 * @param fullScreenPosition  Bounds of the full-screen GLRetroView surface.
 * @param viewModel           Receives dual-screen layout updates.
 */
@Composable
fun DualScreenPanels(
    fullScreenPosition: State<Rect?>,
    viewModel: BaseGameScreenViewModel,
) {
    val density = LocalDensity.current
    var splitFraction by remember { mutableFloatStateOf(SPLIT_DEFAULT) }

    // ── Push to GL every recompose where fullPos is known ────────────────────
    // SideEffect runs synchronously after each successful recomposition, so the
    // GL config is updated on the same frame that splitFraction or fullPos change.
    // No need to wait for onGloballyPositioned on each sub-panel.
    val fullPos = fullScreenPosition.value
    if (fullPos != null) {
        SideEffect {
            val dividerPx = with(density) { DIVIDER_H.toPx() }
            val splitY    = fullPos.top + fullPos.height * splitFraction

            val topPanel = Rect(fullPos.left, fullPos.top,        fullPos.right, splitY)
            val botPanel = Rect(fullPos.left, splitY + dividerPx, fullPos.right, fullPos.bottom)

            viewModel.applyDualScreenLayout(
                AndroidRectF(fullPos.left, fullPos.top, fullPos.right, fullPos.bottom),
                topPanel,
                botPanel,
            )
        }
    }

    // ── Visual layout (divider + two weight-based boxes) ─────────────────────
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Column(modifier = Modifier.fillMaxSize()) {

            // Top screen panel (transparent — GLRetroView renders behind it)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(splitFraction),
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
                    .weight((1f - splitFraction).coerceAtLeast(SPLIT_MIN)),
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
