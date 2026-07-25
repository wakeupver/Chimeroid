package com.swordfish.chimeroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.savesync.SaveSyncWork
import com.swordfish.chimeroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.chimeroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.chimeroid.app.mobile.feature.games.GamesScreen
import com.swordfish.chimeroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.chimeroid.app.mobile.feature.home.HomeScreen
import com.swordfish.chimeroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.chimeroid.app.mobile.feature.search.SearchScreen
import com.swordfish.chimeroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.chimeroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.chimeroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.chimeroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.chimeroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.chimeroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.chimeroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.chimeroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.chimeroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.chimeroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.chimeroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.chimeroid.app.mobile.feature.onboarding.OnboardingScreen
import com.swordfish.chimeroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.chimeroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.chimeroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.enableEdgeToEdgeForTheme
import com.swordfish.chimeroid.app.shared.GameInteractor
import com.swordfish.chimeroid.app.shared.game.BaseGameActivity
import com.swordfish.chimeroid.app.shared.game.GameLauncher
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.shared.main.BusyActivity
import com.swordfish.chimeroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.chimeroid.app.shared.settings.SettingsInteractor
import com.swordfish.chimeroid.common.coroutines.safeLaunch
import com.swordfish.chimeroid.ext.feature.review.ReviewManager
import com.swordfish.chimeroid.lib.android.RetrogradeComponentActivity
import com.swordfish.chimeroid.lib.bios.BiosManager
import com.swordfish.chimeroid.lib.injection.PerActivity
import com.swordfish.chimeroid.lib.library.MetaSystemID
import com.swordfish.chimeroid.lib.library.SystemID
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import com.swordfish.chimeroid.lib.library.db.entity.Game
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.chimeroid.lib.savesync.SaveSyncManager
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import dagger.Provides
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeForTheme()
        super.onCreate(savedInstanceState)

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            val onboardingCompleted =
                remember {
                    mutableStateOf(
                        SharedPreferencesHelper.getLegacySharedPreferences(applicationContext)
                            .getBoolean(getString(R.string.pref_key_onboarding_completed), false),
                    )
                }
            AppTheme {
                if (!onboardingCompleted.value) {
                    OnboardingScreen(
                        onComplete = { onboardingCompleted.value = true },
                    )
                } else {
                    MainScreen(navController)
                }
            }
        }
    }

    @Composable
    private fun MainScreen(navController: NavHostController) {
        val navBackStackEntry = navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry.value?.destination
        val currentRoute =
            currentDestination?.route
                ?.let { MainRoute.findByRoute(it) }
                ?: MainRoute.HOME

        val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            Scaffold(
                topBar = {
                    // HOME manages its own collapsing header — hide shared TopBar there
                    if (currentRoute != MainRoute.HOME) {
                        MainTopBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            onHelpPressed = onHelpPressed,
                            mainUIState = mainUIState,
                            onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                        )
                    }
                },

            ) { padding ->
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                ) {
                    composable(MainRoute.HOME) {
                        val layoutDirection = LocalLayoutDirection.current
                        HomeScreen(
                            // top intentionally excluded: HomeCollapsingHeader
                            // self-handles the status-bar inset directly, decoupled
                            // from Scaffold's shared `padding` (see MainTopBar's
                            // empty-topBar-for-HOME comment above) so Home's layout
                            // can't jump when another route's topBar mounts/unmounts.
                            modifier = Modifier.padding(
                                start = padding.calculateStartPadding(layoutDirection),
                                end = padding.calculateEndPadding(layoutDirection),
                                bottom = padding.calculateBottomPadding(),
                            ),
                            viewModel =
                                viewModel(
                                    factory =
                                        HomeViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            directoriesManager,
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onOpenSystems = { navController.navigateToRoute(MainRoute.SYSTEMS) },
                            onOpenFavorites = { navController.navigateToRoute(MainRoute.FAVORITES) },
                            onHelpPressed = onHelpPressed,
                            onSettingsClick = { navController.navigate(MainRoute.SETTINGS.route) },
                            saveSyncEnabled = mainUIState.saveSyncEnabled,
                            operationInProgress = mainUIState.operationInProgress,
                            onSyncClick = { SaveSyncWork.enqueueManualWork(applicationContext) },
                        )
                    }
                    composable(MainRoute.FAVORITES) {
                        FavoritesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = FavoritesViewModel.Factory(retrogradeDb),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                    composable(MainRoute.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SYSTEMS) {
                        MetaSystemsScreen(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            viewModel =
                                viewModel(
                                    factory =
                                        MetaSystemsViewModel.Factory(
                                            retrogradeDb,
                                            applicationContext,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SYSTEM_GAMES) { entry ->
                        val metaSystemId = entry.arguments?.getString("metaSystemId")
                        GamesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        GamesViewModel.Factory(
                                            retrogradeDb,
                                            MetaSystemID.valueOf(metaSystemId!!),
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getLegacySharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_ADVANCED) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            directoriesManager,
                                        ),
                                ),
                            navController = navController,
                            directoriesManager = directoriesManager,
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        BiosScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
            )

            if (infoDialogDisplayed.value) {
                val message =
                    remember {
                        val systemFolders =
                            SystemID.entries
                                .joinToString(", ") { "<i>${it.dbname}</i>" }

                        getString(R.string.chimeroid_help_content)
                            .replace("\$SYSTEMS", systemFolders)
                    }

                WindowDialog(
                    show = infoDialogDisplayed.value,
                    onDismissRequest = { infoDialogDisplayed.value = false },
                ) {
                    Text(text = AnnotatedString.fromHtml(message))
                }
            }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
            com.swordfish.chimeroid.app.shared.settings.StorageBaseDirPicker.REQUEST_CODE_PICK_DIR -> {
                // Result already processed inside StorageBaseDirPicker itself.
                // Nothing extra needed here.
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
            ) = GameInteractor(activity, retrogradeDb, shortcutsGenerator, gameLauncher)
        }
    }
}
