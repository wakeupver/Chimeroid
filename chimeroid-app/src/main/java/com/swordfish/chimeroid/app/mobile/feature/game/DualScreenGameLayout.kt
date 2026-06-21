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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel

// ─────────────────────────────────────────────────────────────────────────────

private const val SPLIT_MIN     = 0.20f
private const val SPLIT_MAX     = 0.80f
private const val SPLIT_DEFAULT = 0.50f
private val DIVIDER_H = 22.dp

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders two resizable panels (top = primary screen, bottom = secondary)
 * separated by a drag handle, pushing viewport coordinates to the GL layer.
 *
 * Panel bounds are computed mathematically from the composable's own container
 * bounds ([containerPos]) + [splitFraction], rather than waiting for each sub-panel
 * to report via [onGloballyPositioned].  This means:
 *
 * - **No startup stutter**: as soon as both [fullScreenPosition] and the container
 *   position are known (same layout pass → same recomposition), the GL dual-screen
 *   config is applied — no 2-5 frame delay waiting for sub-panels.
 * - **Drag works correctly**: [splitFraction] changes trigger a recompose →
 *   [SideEffect] runs with the new value → GL matches the visual split exactly.
 * - **Accurate layout**: panel Y coordinates are derived from the container's
 *   own height (not the full GL surface), so they match what Compose renders.
 */
@Composable
fun DualScreenPanels(
    fullScreenPosition: State<Rect?>,
    viewModel: BaseGameScreenViewModel,
) {
    val density = LocalDensity.current
    var splitFraction by remember { mutableFloatStateOf(SPLIT_DEFAULT) }

    // Track our own container bounds (set in the same layout pass as fullScreenPosition).
    val containerPos = remember { mutableStateOf<Rect?>(null) }

    val fullPos   = fullScreenPosition.value
    val container = containerPos.value

    // ── Push to GL whenever fullPos, container, or splitFraction changes ──────
    // Both fullPos and containerPos become non-null in the first layout pass,
    // so SideEffect fires on the very first recomposition after that pass.
    // SideEffect is synchronous (no coroutine overhead) and reads splitFraction
    // at execution time, so drag updates are applied immediately each recompose.
    if (fullPos != null && container != null) {
        SideEffect {
            val dividerPx  = with(density) { DIVIDER_H.toPx() }
            // Column distributes (containerH - dividerH) across the two weight slots.
            // Within SPLIT_MIN..SPLIT_MAX both weights sum to 1.0, so top panel height
            // = splitFraction × availableH exactly.
            val availableH = container.height - dividerPx
            val topH       = splitFraction * availableH
            val splitY     = container.top + topH

            viewModel.applyDualScreenLayout(
                AndroidRectF(fullPos.left, fullPos.top, fullPos.right, fullPos.bottom),
                Rect(container.left, container.top,        container.right, splitY),
                Rect(container.left, splitY + dividerPx,  container.right, container.bottom),
            )
        }
    }

    // ── Visual layout ─────────────────────────────────────────────────────────
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerPos.value = it.boundsInRoot() },
    ) {
        val totalPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Column(modifier = Modifier.fillMaxSize()) {

            // Top screen panel
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
        Surface(
            modifier = Modifier.size(width = 48.dp, height = 5.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        ) {}
    }
}
