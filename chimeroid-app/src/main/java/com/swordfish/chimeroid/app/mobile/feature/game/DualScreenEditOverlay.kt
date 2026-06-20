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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.controller.R
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Drag target enum
// ─────────────────────────────────────────────────────────────────────────────

private enum class DragTarget { TOP_MOVE, TOP_RESIZE, BOTTOM_MOVE, BOTTOM_RESIZE }

// ─────────────────────────────────────────────────────────────────────────────
// Hit-test helper (no Compose context needed)
// ─────────────────────────────────────────────────────────────────────────────

private fun hitTest(
    offset: Offset,
    layout: DualScreenLayout,
    cW: Float,
    cH: Float,
    handleSizePx: Float,
): DragTarget? {
    fun PanelLayout.left()   = xFraction * cW
    fun PanelLayout.top()    = yFraction * cH
    fun PanelLayout.right()  = (xFraction + widthFraction) * cW
    fun PanelLayout.bottom() = (yFraction + heightFraction) * cH

    // Resize handles have priority — check them first
    fun PanelLayout.inHandle(o: Offset) =
        o.x >= right() - handleSizePx && o.x <= right() &&
        o.y >= bottom() - handleSizePx && o.y <= bottom()

    fun PanelLayout.inBody(o: Offset) =
        o.x >= left() && o.x <= right() && o.y >= top() && o.y <= bottom()

    if (layout.top.inHandle(offset))    return DragTarget.TOP_RESIZE
    if (layout.bottom.inHandle(offset)) return DragTarget.BOTTOM_RESIZE
    if (layout.top.inBody(offset))      return DragTarget.TOP_MOVE
    if (layout.bottom.inBody(offset))   return DragTarget.BOTTOM_MOVE
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// Drag handler (pure function, no Compose context)
// ─────────────────────────────────────────────────────────────────────────────

private fun handleDrag(
    target: DragTarget,
    drag: Offset,
    layout: DualScreenLayout,
    cW: Float,
    cH: Float,
): DualScreenLayout = when (target) {
    DragTarget.TOP_MOVE -> {
        val p = layout.top
        layout.copy(top = p.copy(
            xFraction = (p.xFraction + drag.x / cW).coerceIn(0f, (1f - p.widthFraction).coerceAtLeast(0f)),
            yFraction = (p.yFraction + drag.y / cH).coerceIn(0f, (1f - p.heightFraction).coerceAtLeast(0f)),
        ))
    }
    DragTarget.TOP_RESIZE -> {
        val p = layout.top
        layout.copy(top = p.copy(
            widthFraction  = (p.widthFraction  + drag.x / cW).coerceIn(PanelLayout.MIN_W, 1f - p.xFraction),
            heightFraction = (p.heightFraction + drag.y / cH).coerceIn(PanelLayout.MIN_H, 1f - p.yFraction),
        ))
    }
    DragTarget.BOTTOM_MOVE -> {
        val p = layout.bottom
        layout.copy(bottom = p.copy(
            xFraction = (p.xFraction + drag.x / cW).coerceIn(0f, (1f - p.widthFraction).coerceAtLeast(0f)),
            yFraction = (p.yFraction + drag.y / cH).coerceIn(0f, (1f - p.heightFraction).coerceAtLeast(0f)),
        ))
    }
    DragTarget.BOTTOM_RESIZE -> {
        val p = layout.bottom
        layout.copy(bottom = p.copy(
            widthFraction  = (p.widthFraction  + drag.x / cW).coerceIn(PanelLayout.MIN_W, 1f - p.xFraction),
            heightFraction = (p.heightFraction + drag.y / cH).coerceIn(PanelLayout.MIN_H, 1f - p.yFraction),
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Public overlay composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen edit overlay for NDS / 3DS dual-screen layout.
 *
 * FIX: a SINGLE [detectDragGestures] handler covers the full screen.
 * On [onDragStart] we manually hit-test to determine which panel/handle was
 * touched.  There are NO nested gesture handlers, so there is NO conflict.
 * Visual panels are purely decorative — they carry no [pointerInput] at all.
 */
@Composable
fun DualScreenEditOverlay(
    layout: DualScreenLayout,
    onLayoutChange: (DualScreenLayout) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    val primaryColor  = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cW          = constraints.maxWidth.toFloat()
        val cH          = constraints.maxHeight.toFloat()
        val density     = LocalDensity.current
        val handleSizePx = with(density) { 44.dp.toPx() }

        var dragTarget by remember { mutableStateOf<DragTarget?>(null) }

        // ── 1. Dim background (no gestures) ──────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        // ── 2. Single full-screen gesture detector ────────────────────────────
        //    Place this BELOW visual panels and buttons in Z-order so buttons
        //    rendered later can still intercept their own taps.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout, cW, cH, handleSizePx) {
                    detectDragGestures(
                        onDragStart  = { offset ->
                            dragTarget = hitTest(offset, layout, cW, cH, handleSizePx)
                        },
                        onDragEnd    = { dragTarget = null },
                        onDragCancel = { dragTarget = null },
                        onDrag       = { _, drag ->
                            val t = dragTarget ?: return@detectDragGestures
                            onLayoutChange(handleDrag(t, drag, layout, cW, cH))
                        },
                    )
                },
        )

        // ── 3. Visual panels (no pointerInput — touch passes through) ─────────
        VisualPanel(
            label        = stringResource(R.string.dual_screen_top_label),
            color        = primaryColor,
            panel        = layout.top,
            containerW   = cW,
            containerH   = cH,
            handleSizePx = handleSizePx,
        )
        VisualPanel(
            label        = stringResource(R.string.dual_screen_bottom_label),
            color        = tertiaryColor,
            panel        = layout.bottom,
            containerW   = cW,
            containerH   = cH,
            handleSizePx = handleSizePx,
        )

        // ── 4. Title bar ──────────────────────────────────────────────────────
        Surface(
            modifier       = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = stringResource(R.string.dual_screen_edit_title),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── 5. Button bar (highest Z — on top of gesture handler) ────────────
        Surface(
            modifier       = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color          = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment     = Alignment.CenterVertically,
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
// Visual-only panel (ZERO pointerInput — gestures pass through to the handler)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VisualPanel(
    label: String,
    color: Color,
    panel: PanelLayout,
    containerW: Float,
    containerH: Float,
    handleSizePx: Float,
) {
    val density  = LocalDensity.current
    val xPx      = (panel.xFraction * containerW).roundToInt()
    val yPx      = (panel.yFraction * containerH).roundToInt()
    val wPx      = (panel.widthFraction  * containerW).roundToInt()
    val hPx      = (panel.heightFraction * containerH).roundToInt()
    val hSizePx  = handleSizePx.roundToInt()

    // Panel body
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .requiredSize(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .border(2.dp, color, RoundedCornerShape(6.dp)),
    ) {
        Text(
            text       = label,
            color      = color,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }

    // Resize handle (visual only)
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx + wPx - hSizePx, yPx + hPx - hSizePx) }
            .requiredSize(with(density) { hSizePx.toDp() })
            .background(color.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Default.OpenInFull,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(22.dp),
        )
    }
}
