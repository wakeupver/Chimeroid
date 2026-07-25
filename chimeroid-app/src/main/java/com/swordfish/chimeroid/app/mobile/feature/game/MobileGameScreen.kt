package com.swordfish.chimeroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.mutedButtonColors
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.chimeroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.chimeroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.ChimeroidPadTheme
import com.swordfish.touchinput.radial.LocalChimeroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.ChimeroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState
import kotlin.math.roundToInt

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }

            // Determine once whether this system uses dual-screen rendering
            val isDualScreen = remember { viewModel.getSystem().isDualScreen }

            // Master visibility flag shared by the virtual gamepad AND the
            // dual-screen divider, so the divider never pops in ahead of the
            // pads while controllerConfigState/touchControlsVisibleState are
            // still emitting their initial values.
            val isVisible =
                touchControllerSettings != null &&
                    currentControllerConfig != null &&
                    touchControlsVisibleState.value

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value

            // Single-viewport path (non-dual-screen systems)
            LaunchedEffect(fullPos, viewPos) {
                if (isDualScreen) return@LaunchedEffect   // handled by DualScreenPanels
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                gameView.viewport = viewport
            }

            // Clear dual-screen config on the GL layer when leaving
            LaunchedEffect(isDualScreen) {
                if (!isDualScreen) viewModel.clearDualScreenLayout()
            }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                ) {
                    // ── Dual-screen split panels (NDS / 3DS only) ───────────
                    // Gated on isVisible so the divider appears in sync with
                    // the virtual gamepad instead of showing up first on open.
                    if (isDualScreen && isVisible) {
                        DualScreenPanels(
                            fullScreenPosition = fullScreenPosition,
                            viewModel = viewModel,
                        )
                    }
                }

                if (isVisible) {
                    CompositionLocalProvider(LocalChimeroidPadTheme provides ChimeroidPadTheme()) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

                        leftGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                            touchControllerSettings,
                        )
                        rightGamePad?.invoke(
                            this,
                            Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                            touchControllerSettings,
                        )

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                        )
                    }
                }
            }

        } // end PadKit

        // ── OUTSIDE PadKit – macro overlay handles its own touch events ──
        // PadKit swallows all raw input; placing the overlay here ensures
        // detectDragGestures / detectTapGestures receive unfiltered events.
        val macroEditMode by viewModel.getMacroEditMode().collectAsState(false)
        val editDialogShown by viewModel.isEditControlShown().collectAsState(false)

        if (touchControlsVisibleState.value) {
            MacroButtonOverlay(viewModel = viewModel)
        }
        if (macroEditMode && !editDialogShown) {
            MacroDragModeBanner(onDone = { viewModel.exitMacroDragMode() })
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Drag-mode banner
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroDragModeBanner(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(8.dp),
            color = MiuixTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.macro_position_button),
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onDone,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.macro_done_positioning))
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Pad container background
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalChimeroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

// ────────────────────────────────────────────────────────────────────────────
// Central menu area (menu button + Edit Controls dialog)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        ChimeroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
        MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Edit Controls dialog (sliders + macro management)
// ────────────────────────────────────────────────────────────────────────────

// Backs the data-driven loop below — avoids repeating the same
// MenuEditTouchControlRow + Slider wiring once per settable dimension.
private data class SliderRowSpec(
    val icon: ImageVector,
    val label: String,
    val iconRotation: Float = 0f,
    val value: Float,
    val onValueChange: (Float) -> Unit,
)

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)

    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // ── Dialog header ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.touch_customize_title),
                            style = MiuixTheme.textStyles.title4,
                        )
                        Text(
                            text = stringResource(R.string.touch_customize_subtitle),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                HorizontalDivider()

                // ── Scrollable body ────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {

                // ── Layout section ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.touch_customize_layout_section).uppercase(),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    val sliderRows = buildList {
                        add(
                            SliderRowSpec(
                                icon = Icons.Default.OpenInFull,
                                label = stringResource(R.string.touch_customize_scale),
                                value = touchControllerSettings.scale,
                                onValueChange = {
                                    viewModel.updateTouchControllerSettings(touchControllerSettings.copy(scale = it))
                                },
                            ),
                        )
                        add(
                            SliderRowSpec(
                                icon = Icons.Default.Height,
                                label = stringResource(R.string.touch_customize_margin_h),
                                iconRotation = 90f,
                                value = touchControllerSettings.marginX,
                                onValueChange = {
                                    viewModel.updateTouchControllerSettings(touchControllerSettings.copy(marginX = it))
                                },
                            ),
                        )
                        add(
                            SliderRowSpec(
                                icon = Icons.Default.Height,
                                label = stringResource(R.string.touch_customize_margin_v),
                                value = touchControllerSettings.marginY,
                                onValueChange = {
                                    viewModel.updateTouchControllerSettings(touchControllerSettings.copy(marginY = it))
                                },
                            ),
                        )
                        if (controllerConfig.allowTouchRotation) {
                            add(
                                SliderRowSpec(
                                    icon = Icons.AutoMirrored.Filled.RotateLeft,
                                    label = stringResource(R.string.touch_customize_rotate),
                                    value = touchControllerSettings.rotation,
                                    onValueChange = {
                                        viewModel.updateTouchControllerSettings(
                                            touchControllerSettings.copy(rotation = it),
                                        )
                                    },
                                ),
                            )
                        }
                    }
                    sliderRows.forEach { row ->
                        key(row.label) {
                            MenuEditTouchControlRow(
                                icon = row.icon,
                                label = row.label,
                                iconRotation = row.iconRotation,
                                value = row.value,
                            ) {
                                Slider(value = row.value, onValueChange = row.onValueChange)
                            }
                        }
                    }
                }

                } // end scrollable body

                // ── Sticky footer: Reset / Done ────────────────────────
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { viewModel.resetTouchControls() }, colors = mutedButtonColors()) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    Button(onClick = { viewModel.showEditControls(false) }) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Slider row helper
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    iconRotation: Float = 0f,
    value: Float? = null,
    slider: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .rotate(iconRotation)
                    .size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        slider()
    }
}
