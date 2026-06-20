package com.swordfish.chimeroid.app.shared.game.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.chimeroid.BuildConfig
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.chimeroid.app.shared.game.ShaderChooser
import com.swordfish.chimeroid.app.shared.rumble.RumbleManager
import com.swordfish.chimeroid.app.shared.settings.HDModeQuality
import com.swordfish.chimeroid.common.coroutines.MutableStateProperty
import com.swordfish.chimeroid.common.coroutines.launchOnState
import com.swordfish.chimeroid.common.view.disableTouchEvents
import com.swordfish.chimeroid.lib.core.CoreVariable
import com.swordfish.chimeroid.lib.core.CoreVariablesManager
import com.swordfish.chimeroid.lib.game.GameLoader
import com.swordfish.chimeroid.lib.game.GameLoaderError
import com.swordfish.chimeroid.lib.game.GameLoaderException
import com.swordfish.chimeroid.lib.library.GameSystem
import com.swordfish.chimeroid.lib.library.SystemCoreConfig
import com.swordfish.chimeroid.lib.library.db.entity.Game
import com.swordfish.chimeroid.lib.storage.RomFiles
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ImmersiveMode
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class GameViewModelRetroGameView(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val settingsManager: SettingsManager,
    private val coreVariablesManager: CoreVariablesManager,
    private val sideEffects: GameViewModelSideEffects,
    private val rumbleManager: RumbleManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    sealed interface GameState {
        /** Initial state — no load has been requested yet. */
        data object Uninitialized : GameState

        /** A human-readable [message] describes the current loading step. */
        data class Loading(val message: String) : GameState

        /**
         * Core + ROM data are ready; the [GLRetroView] has not been created yet.
         * This state is transient — [createRetroView] moves it to [Ready] immediately.
         */
        data class Loaded(
            val gameData: GameLoader.GameData,
            val retroViewData: GLRetroViewData,
        ) : GameState

        /** The [GLRetroView] is running and the session is fully active. */
        data object Ready : GameState

        /** A terminal error occurred; [message] is user-displayable. */
        data class Error(val message: String) : GameState
    }

    private val gameState: MutableStateFlow<GameState> = MutableStateFlow(GameState.Uninitialized)

    /** Guards [initialize] so concurrent or repeated calls are no-ops after the first. */
    private val initMutex = Mutex()

    private val retroGameViewFlow = MutableStateFlow<GLRetroView?>(null)
    var retroGameView: GLRetroView? by MutableStateProperty(retroGameViewFlow)

    /**
     * Raw Linux file-descriptor integers detached via
     * [android.os.ParcelFileDescriptor.detachFd] for the PPSSPP-style direct-load path.
     *
     * Stored *outside* [gameState] so [closeOpenFds] can reach them regardless of which
     * state the machine is in when [BaseGameScreenViewModel.onCleared] fires.
     * By the time onCleared() runs the state is [GameState.Ready], not [GameState.Loaded],
     * so reading from gameState for this purpose would always return an empty list.
     */
    private var detachedFds: List<Int> = emptyList()

    /**
     * Returns the game state stream.
     *
     * No debounce is applied here — callers observe raw transitions and can apply their own
     * debounce policy if needed.  The previous 200 ms debounce was hiding rapid Loading→Loaded
     * transitions, causing the UI to briefly show stale states.
     */
    fun getGameState(): Flow<GameState> = gameState

    /**
     * Closes raw Linux file-descriptor integers that were detached via
     * [android.os.ParcelFileDescriptor.detachFd] during the PPSSPP-style direct load.
     *
     * Call this from [BaseGameScreenViewModel.onCleared] when the game session ends.
     *
     * NOTE: We read from [detachedFds] — a dedicated field — not from [gameState].
     * By the time onCleared() fires the state machine is in [GameState.Ready], not
     * [GameState.Loaded], so casting gameState would always yield null here.
     */
    fun closeOpenFds() {
        detachedFds.forEach { rawFd ->
            runCatching {
                android.os.ParcelFileDescriptor.adoptFd(rawFd).close()
                Timber.d("GameViewModelRetroGameView: closed detached fd=$rawFd")
            }.onFailure {
                Timber.w(it, "GameViewModelRetroGameView: failed to close fd=$rawFd")
            }
        }
        detachedFds = emptyList()
    }

    /**
     * Begins the load pipeline.  Safe to call from any coroutine; concurrent/re-entrant calls
     * after the first are silently ignored thanks to [initMutex].
     */
    suspend fun initialize(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
    ) {
        // Fast-path: if we've already started, do nothing.
        if (gameState.value != GameState.Uninitialized) return

        // Serialize access so concurrent callers can't both pass the check above and
        // both kick off a duplicate load.
        initMutex.withLock {
            if (gameState.value != GameState.Uninitialized) return

            val autoSaveEnabled = settingsManager.autoSave()
            val filter = settingsManager.screenFilter()
            val hdMode = settingsManager.hdMode()
            val hdModeQuality = settingsManager.hdModeQuality()
            val lowLatencyAudio = settingsManager.lowLatencyAudio()
            val enableRumble = settingsManager.enableRumble()
            val directLoad = settingsManager.allowDirectGameLoad()
            val enableImmersiveMode = settingsManager.enableImmersiveMode()
            val aspectRatioMode = settingsManager.aspectRatioMode()

            val hasMicrophonePermission =
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED

            val enableMicrophone = systemCoreConfig.supportsMicrophone && hasMicrophonePermission

            gameLoader
                .load(
                    applicationContext,
                    game,
                    requestLoadSave && autoSaveEnabled,
                    systemCoreConfig,
                    directLoad,
                )
                .flowOn(Dispatchers.IO)
                .catch { throwable ->
                    val errorMsg =
                        if (throwable is GameLoaderException) getErrorMessage(throwable.error)
                        else appContext.getString(R.string.game_loader_error_generic)

                    Timber.e(throwable, "GameLoader pipeline failed")
                    // Publish the error both to the state machine (for structured observation)
                    // and via side-effects (for the Activity to act on).
                    gameState.value = GameState.Error(errorMsg)
                    sideEffects.requestFailureFinish(errorMsg)
                }
                .collect { loadingState ->
                    gameState.value =
                        when (loadingState) {
                            is GameLoader.LoadingState.Ready -> {
                                Timber.i("GameLoader: data ready, building RetroViewData")
                                // Capture detached fds now, before state transitions to Ready.
                                // closeOpenFds() fires in onCleared() when state == Ready,
                                // so reading detachedFds from gameState there would always miss.
                                detachedFds = (loadingState.gameData.gameFiles as? RomFiles.Standard)
                                    ?.detachedFds ?: emptyList()
                                val retroViewData =
                                    buildRetroViewData(
                                        applicationContext,
                                        systemCoreConfig,
                                        loadingState.gameData,
                                        hdMode,
                                        hdModeQuality,
                                        filter,
                                        lowLatencyAudio,
                                        enableRumble,
                                        enableMicrophone,
                                        enableImmersiveMode,
                                        aspectRatioMode,
                                    )
                                GameState.Loaded(
                                    gameData = loadingState.gameData,
                                    retroViewData = retroViewData,
                                )
                            }
                            else -> GameState.Loading(getLoadingMessage(loadingState))
                        }
                }
        }
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): Pair<GameLoader.GameData, GLRetroView> {
        val currentState = gameState.value
        check(currentState is GameState.Loaded) {
            "createRetroView called in invalid state: $currentState"
        }

        val result =
            GLRetroView(context, currentState.retroViewData)
                .apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }

        if (!system.hasTouchScreen) {
            result.disableTouchEvents()
        }

        lifecycle.lifecycle.addObserver(result)

        if (BuildConfig.DEBUG) {
            runCatching { printRetroVariables(result) }
        }

        retroGameViewFlow.value = result
        // Advance state before returning so observers see Ready immediately.
        gameState.value = GameState.Ready

        return currentState.gameData to result
    }

    suspend fun retroGameViewFlow() =
        retroGameViewFlow
            .filterNotNull()
            .first()

    suspend fun waitRetroGameViewInitialized() {
        retroGameViewFlow()
    }

    suspend inline fun <reified T> waitGLEvent() {
        val retroView = retroGameViewFlow()
        retroView.getGLRetroEvents()
            .filterIsInstance<T>()
            .first()
    }

    /**
     * Applies (or clears) the dual-screen split on the live [GLRetroView].
     *
     * Converts Compose pixel positions → 0-1 GL surface fractions, then
     * pushes a [GLRetroView.DualScreenConfig] to the view.  Safe to call
     * from the Compose thread; the observable property queues to the GL thread.
     */
    fun applyDualScreenLayout(
        fullPos: RectF,
        topPanelPos: Rect,
        bottomPanelPos: Rect,
        system: GameSystem,
    ) {
        val view = retroGameView ?: return
        val uvCfg = system.dualScreenUVConfig ?: return

        if (fullPos.width() == 0f || fullPos.height() == 0f) return

        fun Rect.toGLViewport(): RectF {
            val l = (left   - fullPos.left) / fullPos.width()
            val t = (top    - fullPos.top)  / fullPos.height()
            val r = (right  - fullPos.left) / fullPos.width()
            val b = (bottom - fullPos.top)  / fullPos.height()
            return RectF(l, t, r, b)
        }

        view.dualScreenConfig = GLRetroView.DualScreenConfig(
            primaryViewport   = topPanelPos.toGLViewport(),
            secondaryViewport = bottomPanelPos.toGLViewport(),
            primaryUVxMin     = uvCfg.primaryUVxMin,
            primaryUVyMin     = uvCfg.primaryUVyMin,
            primaryUVxMax     = uvCfg.primaryUVxMax,
            primaryUVyMax     = uvCfg.primaryUVyMax,
            secondaryUVxMin   = uvCfg.secondaryUVxMin,
            secondaryUVyMin   = uvCfg.secondaryUVyMin,
            secondaryUVxMax   = uvCfg.secondaryUVxMax,
            secondaryUVyMax   = uvCfg.secondaryUVyMax,
        )
    }

    /**
     * Directly apply a [DualScreenLayout] to the live [GLRetroView] without
     * going through tracking boxes.  The layout fractions are relative to the
     * GL surface, so they map 1:1 to the GL viewport fractions.
     * Suspends until the GLRetroView is ready.
     */
    suspend fun applyDualScreenLayoutDirect(
        layout: DualScreenLayout,
        uvCfg: com.swordfish.chimeroid.lib.library.GameSystem.DualScreenUVConfig,
    ) {
        val view = retroGameViewFlow()
        val t = layout.top
        val b = layout.bottom
        view.dualScreenConfig = GLRetroView.DualScreenConfig(
            primaryViewport   = RectF(
                t.xFraction,
                t.yFraction,
                t.xFraction + t.widthFraction,
                t.yFraction + t.heightFraction,
            ),
            secondaryViewport = RectF(
                b.xFraction,
                b.yFraction,
                b.xFraction + b.widthFraction,
                b.yFraction + b.heightFraction,
            ),
            primaryUVxMin   = uvCfg.primaryUVxMin,
            primaryUVyMin   = uvCfg.primaryUVyMin,
            primaryUVxMax   = uvCfg.primaryUVxMax,
            primaryUVyMax   = uvCfg.primaryUVyMax,
            secondaryUVxMin = uvCfg.secondaryUVxMin,
            secondaryUVyMin = uvCfg.secondaryUVyMin,
            secondaryUVxMax = uvCfg.secondaryUVxMax,
            secondaryUVyMax = uvCfg.secondaryUVyMax,
        )
    }

    /** Disable dual-screen mode and return to normal rendering. */
    fun clearDualScreenLayout() {
        retroGameView?.dualScreenConfig = null
    }

    private fun buildRetroViewData(
        appContext: Context,
        systemCoreConfig: SystemCoreConfig,
        gameData: GameLoader.GameData,
        hdMode: Boolean,
        hdModeQuality: HDModeQuality,
        screenFilter: String,
        lowLatencyAudio: Boolean,
        requestRumble: Boolean,
        requestMicrophone: Boolean,
        enableImmersiveMode: Boolean,
        aspectRatioMode: String,
    ): GLRetroViewData {
        return GLRetroViewData(appContext).apply {
            coreFilePath = gameData.coreLibrary

            when (val gameFiles = gameData.gameFiles) {
                is RomFiles.Standard -> {
                    gameFilePath = gameFiles.files.first().absolutePath
                }

                is RomFiles.Virtual -> {
                    gameVirtualFiles = gameFiles.files.map { VirtualFile(it.filePath, it.fd) }
                }
            }

            systemDirectory = gameData.systemDirectory.absolutePath
            savesDirectory = gameData.savesDirectory.absolutePath
            variables = gameData.coreVariables.map { Variable(it.key, it.value) }.toTypedArray()
            saveRAMState = gameData.saveRAMData
            shader =
                ShaderChooser.getShaderForSystem(
                    appContext,
                    hdMode,
                    hdModeQuality,
                    screenFilter,
                    GameSystem.findById(gameData.game.systemId),
                )
            preferLowLatencyAudio = lowLatencyAudio
            rumbleEventsEnabled = requestRumble
            skipDuplicateFrames = systemCoreConfig.skipDuplicateFrames
            enableMicrophone = requestMicrophone
            immersiveMode = buildImmersiveModeConfiguration(enableImmersiveMode)
            stretchToFill = (aspectRatioMode == "stretch")
        }
    }

    private fun buildImmersiveModeConfiguration(enableImmersiveMode: Boolean): ImmersiveMode? {
        return if (enableImmersiveMode) ImmersiveMode(blendFactor = 0.05f) else null
    }

    private fun getLoadingMessage(loadingState: GameLoader.LoadingState): String {
        return when (loadingState) {
            is GameLoader.LoadingState.LoadingCore ->
                appContext.getString(com.swordfish.chimeroid.ext.R.string.game_loading_download_core)
            is GameLoader.LoadingState.LoadingGame ->
                appContext.getString(R.string.game_loading_preparing_game)
            else -> ""
        }
    }

    private fun printRetroVariables(retroGameView: GLRetroView) {
        scope.launch {
            // Some cores do not immediately call SET_VARIABLES so we might need to wait a little.
            delay(1.seconds)
            retroGameView.getVariables().forEach {
                Timber.i("Libretro variable: $it")
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.launchOnState(Lifecycle.State.STARTED) {
            initializeRetroGameViewErrorsFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeCoreVariablesFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeRumbleFlow()
        }
    }

    private suspend fun initializeCoreVariablesFlow() {
        try {
            waitRetroGameViewInitialized()
            val options = coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig)
            updateCoreVariables(options)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize core variables")
        }
    }

    private suspend fun initializeRumbleFlow() {
        val retroGameView = retroGameViewFlow()
        val rumbleEvents = retroGameView.getRumbleEvents()
        rumbleManager.collectAndProcessRumbleEvents(systemCoreConfig, rumbleEvents)
    }

    private suspend fun initializeRetroGameViewErrorsFlow() {
        retroGameViewFlow().getGLRetroErrors()
            .catch { Timber.e(it, "Exception in GLRetroErrors stream.") }
            .collect { handleRetroViewError(it) }
    }

    private fun updateCoreVariables(options: List<CoreVariable>) {
        val updatedVariables =
            options.map { Variable(it.key, it.value) }.toTypedArray()

        updatedVariables.forEach {
            Timber.i("Updating core variable: ${it.key} ${it.value}")
        }

        retroGameView?.updateVariables(*updatedVariables)
    }

    private fun handleRetroViewError(errorCode: Int) {
        Timber.e("Error in GLRetroView errorCode=$errorCode")
        val gameLoaderError =
            when (errorCode) {
                GLRetroView.ERROR_GL_NOT_COMPATIBLE -> GameLoaderError.GLIncompatible
                GLRetroView.ERROR_LOAD_GAME -> GameLoaderError.LoadGame
                GLRetroView.ERROR_LOAD_LIBRARY -> GameLoaderError.LoadCore
                GLRetroView.ERROR_SERIALIZATION -> GameLoaderError.Saves
                else -> GameLoaderError.Generic
            }

        val message = getErrorMessage(gameLoaderError)
        gameState.value = GameState.Error(message)
        sideEffects.requestFailureFinish(message)
    }

    private fun getErrorMessage(gameError: GameLoaderError): String =
        when (gameError) {
            is GameLoaderError.GLIncompatible ->
                appContext.getString(R.string.game_loader_error_gl_incompatible)
            is GameLoaderError.Generic ->
                appContext.getString(R.string.game_loader_error_generic)
            is GameLoaderError.LoadCore ->
                appContext.getString(com.swordfish.chimeroid.ext.R.string.game_loader_error_load_core)
            is GameLoaderError.LoadGame ->
                appContext.getString(R.string.game_loader_error_load_game)
            is GameLoaderError.Saves ->
                appContext.getString(R.string.game_loader_error_save)
            is GameLoaderError.UnsupportedArchitecture ->
                appContext.getString(R.string.game_loader_error_unsupported_architecture)
            is GameLoaderError.MissingBiosFiles ->
                appContext.getString(R.string.game_loader_error_missing_bios, gameError.missingFiles)
        }
}
