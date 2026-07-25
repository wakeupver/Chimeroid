@file:Suppress("UNUSED")

package com.swordfish.chimeroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.macros.GameMenuMacrosScreen
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.macros.GameMenuMacrosViewModel
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.patchcodes.GameMenuPatchCodesScreen
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.patchcodes.GameMenuPatchCodesViewModel
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.enableEdgeToEdgeForTheme
import com.swordfish.chimeroid.app.shared.GameMenuContract
import com.swordfish.chimeroid.app.shared.coreoptions.ChimeroidCoreOption
import com.swordfish.chimeroid.app.shared.game.macro.MacroButtonsManager
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.common.kotlin.serializable
import com.swordfish.chimeroid.lib.android.RetrogradeComponentActivity
import com.swordfish.chimeroid.lib.library.SystemCoreConfig
import com.swordfish.chimeroid.lib.library.db.dao.PatchCodeDao
import com.swordfish.chimeroid.lib.library.db.entity.Game
import com.swordfish.chimeroid.lib.saves.StatesManager
import com.swordfish.chimeroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import java.security.InvalidParameterException
import javax.inject.Inject

class GameMenuActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var patchCodeDao: PatchCodeDao

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    data class GameMenuRequest(
        val coreOptions: List<ChimeroidCoreOption>,
        val advancedCoreOptions: List<ChimeroidCoreOption>,
        val autoDetectedCoreOptions: List<ChimeroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val fastForwardEnabled: Boolean,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
        val currentTouchControllerId: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdgeForTheme()

        // Sembunyikan status bar saat game menu terbuka
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val extras = intent.extras

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<ChimeroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<ChimeroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                autoDetectedCoreOptions =
                    intent.serializable<Array<ChimeroidCoreOption>>(GameMenuContract.EXTRA_AUTO_DETECTED_CORE_OPTIONS)
                        ?.toList()
                        ?: emptyList(),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                fastForwardEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
                currentTouchControllerId =
                    extras?.getString(GameMenuContract.EXTRA_CURRENT_TOUCH_CONTROLLER_ID) ?: "default",
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination

            val currentRoute =
                currentDestination?.route
                    ?.let { GameMenuRoute.findByRoute(it) }
                    ?: GameMenuRoute.HOME

            // Miuix's TopAppBar title is a plain String (no composable slot), so the two-line
            // "Game Menu" label + game title used for HOME collapses to just the game title —
            // still the important information, and it's the one rendered with title emphasis.
            val topBarTitle =
                if (currentRoute == GameMenuRoute.HOME) {
                    gameMenuRequest.game.title
                } else {
                    stringResource(currentRoute.titleId)
                }

            SideMenu {
                TopAppBar(
                    title = topBarTitle,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        AnimatedContent(targetState = currentRoute.canGoBack(), label = "Back") { canGoBack ->
                            if (canGoBack) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.back),
                                    )
                                }
                            } else {
                                IconButton(onClick = { onResult { } }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        stringResource(R.string.close),
                                    )
                                }
                            }
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    navController = navController,
                    startDestination = GameMenuRoute.HOME.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(GameMenuRoute.HOME) {
                        GameMenuHomeScreen(navController, gameMenuRequest, ::onResult)
                    }
                    composable(GameMenuRoute.SAVE) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        false,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.LOAD) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        true,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.OPTIONS) {
                        GameMenuCoreOptionsScreen(
                            viewModel(
                                factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                            ),
                            gameMenuRequest,
                        )
                    }
                    composable(GameMenuRoute.MACROS) {
                        GameMenuMacrosScreen(
                            viewModel(
                                factory =
                                    GameMenuMacrosViewModel.Factory(
                                        MacroButtonsManager(sharedPreferences),
                                        gameMenuRequest.currentTouchControllerId,
                                    ),
                            ),
                            onPositionOnScreen = {
                                onResult { putExtra(GameMenuContract.RESULT_POSITION_MACROS, true) }
                            },
                        )
                    }
                    composable(GameMenuRoute.PATCH_CODES) {
                        GameMenuPatchCodesScreen(
                            viewModel(
                                factory =
                                    GameMenuPatchCodesViewModel.Factory(
                                        gameId = gameMenuRequest.game.id,
                                        patchCodeDao = patchCodeDao,
                                    ),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SideMenu(content: @Composable () -> Unit) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Area transparan: tap di luar panel untuk dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onResult { } },
            )

            val panelWidth = remember(maxWidth) {
                minOf(maxWidth * 0.85f, 420.dp)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth)
                    .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    @dagger.Module
    abstract class Module
}
