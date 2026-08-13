package com.swordfish.chimeroid.app.mobile.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.chimeroid.app.shared.game.macro.MacroButton
import com.swordfish.touchinput.radial.ChimeroidPadTheme
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.ChimeroidButtonForeground
import com.swordfish.touchinput.radial.ui.ChimeroidControlBackground
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val BUTTON_SIZE    = 52.dp
private val DELETE_BADGE   = 18.dp
private val RESIZE_HANDLE  = 18.dp

private const val RESIZE_SENSITIVITY_PX = 300f

@Composable
fun MacroButtonOverlay(viewModel: BaseGameScreenViewModel) {
    val macroButtons by viewModel.getMacroButtons().collectAsState(emptyList())
    val editMode     by viewModel.getMacroEditMode().collectAsState(false)

    if (macroButtons.isEmpty()) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density  = LocalDensity.current
        val screenW  = with(density) { maxWidth.toPx() }
        val screenH  = with(density) { maxHeight.toPx() }
        val btnPx    = with(density) { BUTTON_SIZE.toPx() }
        val badgePx  = with(density) { DELETE_BADGE.toPx() }

        macroButtons.forEach { btn ->
            key(btn.id) {
                MacroButtonItem(
                    btn       = btn,
                    editMode  = editMode,
                    screenW   = screenW,
                    screenH   = screenH,
                    btnPx     = btnPx,
                    badgePx   = badgePx,
                    onPress   = { viewModel.pressMacro(btn) },
                    onRelease = { viewModel.releaseMacro(btn) },
                    onMoved   = { x, y -> viewModel.updateMacroPosition(btn.id, x, y) },
                    onScaled  = { s -> viewModel.updateMacroScale(btn.id, s) },
                    onDelete  = { viewModel.deleteMacro(btn.id) },
                )
            }
        }
    }
}

@Composable
private fun MacroButtonItem(
    btn: MacroButton,
    editMode: Boolean,
    screenW: Float,
    screenH: Float,
    btnPx: Float,
    badgePx: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onMoved: (xFrac: Float, yFrac: Float) -> Unit,
    onScaled: (scale: Float) -> Unit,
    onDelete: () -> Unit,
) {

    var scale by remember(btn.id) { mutableFloatStateOf(btn.scale) }
    val scaledBtnPx = btnPx * scale
    val half = scaledBtnPx / 2f

    var px by remember(btn.id) { mutableFloatStateOf(btn.xFraction * screenW - half) }
    var py by remember(btn.id) { mutableFloatStateOf(btn.yFraction * screenH - half) }

    LaunchedEffect(btn.xFraction, btn.yFraction, btn.scale, screenW, screenH) {
        scale = btn.scale
        val syncedHalf = (btnPx * btn.scale) / 2f
        px = (btn.xFraction * screenW - syncedHalf).coerceIn(0f, (screenW - btnPx * btn.scale).coerceAtLeast(0f))
        py = (btn.yFraction * screenH - syncedHalf).coerceIn(0f, (screenH - btnPx * btn.scale).coerceAtLeast(0f))
    }

    val pressedState = remember { mutableStateOf(false) }

    val gestureModifier = Modifier.pointerInput(editMode, btn.id) {
        if (editMode) {
            detectDragGestures(
                onDrag = { change, delta ->
                    change.consume()
                    px = (px + delta.x).coerceIn(0f, screenW - scaledBtnPx)
                    py = (py + delta.y).coerceIn(0f, screenH - scaledBtnPx)
                },
                onDragEnd = {
                    onMoved(
                        ((px + half) / screenW).coerceIn(0f, 1f),
                        ((py + half) / screenH).coerceIn(0f, 1f),
                    )
                },
                onDragCancel = {
                    onMoved(
                        ((px + half) / screenW).coerceIn(0f, 1f),
                        ((py + half) / screenH).coerceIn(0f, 1f),
                    )
                },
            )
        } else {
            detectTapGestures(
                onPress = { _ ->
                    pressedState.value = true
                    onPress()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressedState.value = false
                        onRelease()
                    }
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(px.roundToInt(), py.roundToInt()) }
            .size(BUTTON_SIZE * scale)
            .then(gestureModifier),
    ) {

        NativeStyleButton(
            label       = btn.label,
            pressedState = pressedState,
            editMode    = editMode,
            modifier    = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                modifier = Modifier
                    .size(DELETE_BADGE)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(Color.Red) }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDelete() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = "Delete macro",
                    tint               = Color.White,
                    modifier           = Modifier.size(10.dp),
                )
            }
        }

        AnimatedVisibility(
            visible  = editMode,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                modifier = Modifier
                    .size(RESIZE_HANDLE)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(Color(0xFF2196F3)) }
                    .pointerInput(btn.id) {
                        detectDragGestures(
                            onDrag = { change, delta ->
                                change.consume()
                                val magnitude = delta.x + delta.y
                                scale = (scale + magnitude / RESIZE_SENSITIVITY_PX).coerceIn(
                                    TouchControllerSettingsManager.MIN_SCALE,
                                    TouchControllerSettingsManager.MAX_SCALE,
                                )
                            },
                            onDragEnd = { onScaled(scale) },
                            onDragCancel = { onScaled(scale) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.OpenInFull,
                    contentDescription = "Resize macro",
                    tint               = Color.White,
                    modifier           = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun NativeStyleButton(
    label: String,
    pressedState: State<Boolean>,
    editMode: Boolean,
    modifier: Modifier = Modifier,
) {

    val theme = remember { ChimeroidPadTheme() }
    CompositionLocalProvider(LocalChimeroidPadTheme provides theme) {
        Box(
            modifier = modifier
                .then(
                    if (editMode)
                        Modifier.border(1.5.dp, Color.Yellow.copy(alpha = 0.80f), CircleShape)
                    else
                        Modifier,
                )
                .padding(theme.padding),
        ) {
            ChimeroidControlBackground()
            ChimeroidButtonForeground(
                pressed = pressedState,
                label   = label,
            )
        }
    }
}
