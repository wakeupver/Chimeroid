package com.swordfish.chimeroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.controller.R
import kotlin.math.roundToInt

private val HANDLE_SIZE = 44.dp

/**
 * Full-screen drag-and-resize editor for NDS / 3DS dual-screen layout.
 * Placed OUTSIDE PadKit so gestures are not intercepted by the input system.
 *
 * FIX: the resize handle is rendered as a SIBLING of the panel body (not a
 * child), so there is no parent–child gesture conflict in detectDragGestures.
 */
@Composable
fun DualScreenEditOverlay(
    layout: DualScreenLayout,
    onLayoutChange: (DualScreenLayout) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Consume every raw touch event so nothing reaches the game
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cW = constraints.maxWidth.toFloat()
            val cH = constraints.maxHeight.toFloat()

            // Top screen panel + resize handle (both siblings inside BoxWithConstraints)
            EditablePanel(
                label          = stringResource(R.string.dual_screen_top_label),
                color          = MaterialTheme.colorScheme.primary,
                panel          = layout.top,
                containerW     = cW,
                containerH     = cH,
                onPanelChange  = { onLayoutChange(layout.copy(top = it)) },
            )

            // Bottom screen panel + resize handle
            EditablePanel(
                label          = stringResource(R.string.dual_screen_bottom_label),
                color          = MaterialTheme.colorScheme.tertiary,
                panel          = layout.bottom,
                containerW     = cW,
                containerH     = cH,
                onPanelChange  = { onLayoutChange(layout.copy(bottom = it)) },
            )
        }

        // Top bar title
        Surface(
            modifier       = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = stringResource(R.string.dual_screen_edit_title),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Bottom bar buttons
        Surface(
            modifier       = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onReset) {
                    Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.dual_screen_reset_ratio), modifier = Modifier.padding(start = 4.dp))
                }
                FilledTonalButton(onClick = onDone) {
                    Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.touch_customize_button_done), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel body + resize handle emitted as TWO siblings in the parent Box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditablePanel(
    label: String,
    color: Color,
    panel: PanelLayout,
    containerW: Float,
    containerH: Float,
    onPanelChange: (PanelLayout) -> Unit,
) {
    val density = LocalDensity.current

    val xPx      = (panel.xFraction      * containerW).roundToInt()
    val yPx      = (panel.yFraction      * containerH).roundToInt()
    val wPx      = (panel.widthFraction  * containerW).roundToInt()
    val hPx      = (panel.heightFraction * containerH).roundToInt()
    val handlePx = with(density) { HANDLE_SIZE.toPx() }.roundToInt()

    // ── 1. Panel body — draggable to move ────────────────────────────────────
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .requiredSize(
                with(density) { wPx.toDp() },
                with(density) { hPx.toDp() },
            )
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .border(2.dp, color, RoundedCornerShape(6.dp))
            .pointerInput(panel, containerW, containerH) {
                detectDragGestures { _, drag ->
                    val newX = (panel.xFraction + drag.x / containerW)
                        .coerceIn(0f, (1f - panel.widthFraction).coerceAtLeast(0f))
                    val newY = (panel.yFraction + drag.y / containerH)
                        .coerceIn(0f, (1f - panel.heightFraction).coerceAtLeast(0f))
                    onPanelChange(panel.copy(xFraction = newX, yFraction = newY))
                }
            },
    ) {
        Text(
            text       = label,
            color      = color,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }

    // ── 2. Resize handle — SIBLING at bottom-right corner of panel ───────────
    //    By being a sibling (not a child), there is no gesture conflict with
    //    the panel body's detectDragGestures.
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    xPx + wPx - handlePx,
                    yPx + hPx - handlePx,
                )
            }
            .requiredSize(HANDLE_SIZE)
            .background(color.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .pointerInput(panel, containerW, containerH) {
                detectDragGestures { _, drag ->
                    val newW = (panel.widthFraction  + drag.x / containerW)
                        .coerceIn(PanelLayout.MIN_W, 1f - panel.xFraction)
                    val newH = (panel.heightFraction + drag.y / containerH)
                        .coerceIn(PanelLayout.MIN_H, 1f - panel.yFraction)
                    onPanelChange(panel.copy(widthFraction = newW, heightFraction = newH))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.OpenInFull,
            contentDescription = stringResource(R.string.dual_screen_drag_handle),
            tint               = Color.White,
            modifier           = Modifier.size(22.dp),
        )
    }
}
