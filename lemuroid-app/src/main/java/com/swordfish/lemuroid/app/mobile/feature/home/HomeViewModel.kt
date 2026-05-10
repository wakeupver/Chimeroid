package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.StorageBaseDirPicker
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.common.coroutines.combine
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeViewModel(
    appContext: Context,
    retrogradeDb: RetrogradeDatabase,
    private val coresSelection: CoresSelection,
    private val directoriesManager: DirectoriesManager,
) : ViewModel() {

    companion object {
        const val CAROUSEL_MAX_ITEMS = 10
        const val DEBOUNCE_TIME = 100L
    }

    class Factory(
        val appContext: Context,
        val retrogradeDb: RetrogradeDatabase,
        val coresSelection: CoresSelection,
        val directoriesManager: DirectoriesManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(appContext, retrogradeDb, coresSelection, directoriesManager) as T
        }
    }

    data class UIState(
        val favoritesGames: List<Game> = emptyList(),
        val recentGames: List<Game> = emptyList(),
        // Discover: ALL games shuffled randomly — shown as horizontal scroll cards
        val discoveryGames: List<Game> = emptyList(),
        // Filtered: ALL games matching selected chip — shown as vertical list below discover
        val filteredGames: List<Game> = emptyList(),
        val availableSystems: List<SystemID> = emptyList(),
        val indexInProgress: Boolean = true,
        val showNoNotificationPermissionCard: Boolean = false,
        val showNoMicrophonePermissionCard: Boolean = false,
        val showNoGamesCard: Boolean = false,
        val showDesmumeDeprecatedCard: Boolean = false,
        val showStorageLocationCard: Boolean = false,
    )

    private val microphonePermissionEnabledState = MutableStateFlow(true)
    private val notificationsPermissionEnabledState = MutableStateFlow(true)
    private val storageLocationSetState = MutableStateFlow(directoriesManager.isBaseDirSet())
    private val uiStates = MutableStateFlow(UIState())

    // null = "All"
    val selectedSystemId = MutableStateFlow<String?>(null)

    // Bumped on open and on shuffle button press — triggers re-shuffle of discover
    private val shuffleSeed = MutableStateFlow(System.currentTimeMillis())

    fun selectSystem(systemId: String?) {
        selectedSystemId.value = systemId
    }

    fun reshuffle() {
        shuffleSeed.value = System.currentTimeMillis()
    }

    fun getViewStates(): Flow<UIState> = uiStates

    fun changeLocalStorageFolder(context: Context) {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun selectStorageLocation(context: Context) {
        StorageBaseDirPicker.launch(context)
    }

    fun updatePermissions(context: Context) {
        notificationsPermissionEnabledState.value = isNotificationsPermissionGranted(context)
        microphonePermissionEnabledState.value = isMicrophonePermissionGranted(context)
        storageLocationSetState.value = directoriesManager.isBaseDirSet()
    }

    private fun isNotificationsPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isMicrophonePermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildViewState(
        favoritesGames: List<Game>,
        recentGames: List<Game>,
        discoveryGames: List<Game>,
        filteredGames: List<Game>,
        availableSystems: List<SystemID>,
        indexInProgress: Boolean,
        notificationsPermissionEnabled: Boolean,
        showMicrophoneCard: Boolean,
        showDesmumeWarning: Boolean,
        storageLocationSet: Boolean,
    ): UIState {
        val noGames = recentGames.isEmpty() && favoritesGames.isEmpty() && discoveryGames.isEmpty()
        return UIState(
            favoritesGames                 = favoritesGames,
            recentGames                    = recentGames,
            discoveryGames                 = discoveryGames,
            filteredGames                  = filteredGames,
            availableSystems               = availableSystems,
            indexInProgress                = indexInProgress,
            showNoNotificationPermissionCard = !notificationsPermissionEnabled,
            showNoMicrophonePermissionCard = showMicrophoneCard,
            showNoGamesCard                = noGames,
            showDesmumeDeprecatedCard      = showDesmumeWarning,
            showStorageLocationCard        = !storageLocationSet,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    init {
        viewModelScope.launch {
            // Discover: ALL games shuffled by seed (independent of chip selection)
            val shuffledDiscoverFlow = shuffleSeed.flatMapLatest { seed ->
                retrogradeDb.gameDao().selectAllGames()
                    .map { games -> games.shuffled(java.util.Random(seed)) }
            }

            // Filtered list: all games for selected chip (or all if null)
            val filteredGamesFlow = selectedSystemId.flatMapLatest { sysId ->
                if (sysId == null) {
                    retrogradeDb.gameDao().selectAllGames()
                } else {
                    retrogradeDb.gameDao().selectAllBySystem(sysId)
                }
            }

            val uiStatesFlow = combine(
                favoritesGames(retrogradeDb),
                recentGames(retrogradeDb),
                shuffledDiscoverFlow,
                filteredGamesFlow,
                availableSystems(retrogradeDb),
                indexingInProgress(appContext),
                notificationsPermissionEnabledState,
                microphoneNotification(retrogradeDb),
                desmumeWarningNotification(),
                storageLocationSetState,
            ) { args ->
                @Suppress("UNCHECKED_CAST")
                buildViewState(
                    favoritesGames                 = args[0] as List<Game>,
                    recentGames                    = args[1] as List<Game>,
                    discoveryGames                 = args[2] as List<Game>,
                    filteredGames                  = args[3] as List<Game>,
                    availableSystems               = args[4] as List<SystemID>,
                    indexInProgress                = args[5] as Boolean,
                    notificationsPermissionEnabled = args[6] as Boolean,
                    showMicrophoneCard             = args[7] as Boolean,
                    showDesmumeWarning             = args[8] as Boolean,
                    storageLocationSet             = args[9] as Boolean,
                )
            }

            uiStatesFlow
                .debounce(DEBOUNCE_TIME)
                .flowOn(Dispatchers.IO)
                .collect { uiStates.value = it }
        }
    }

    private fun indexingInProgress(appContext: Context) =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()

    private fun recentGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstUnfavoriteRecents(CAROUSEL_MAX_ITEMS)

    private fun favoritesGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstFavorites(CAROUSEL_MAX_ITEMS)

    private fun availableSystems(retrogradeDb: RetrogradeDatabase): Flow<List<SystemID>> =
        retrogradeDb.gameDao().selectSystemsWithCount()
            .map { systemCounts ->
                systemCounts.mapNotNull { sc ->
                    SystemID.values().firstOrNull { it.dbname == sc.systemId }
                }.sortedBy { it.dbname }
            }
            .distinctUntilChanged()

    private fun dsGamesCount(retrogradeDb: RetrogradeDatabase): Flow<Int> =
        retrogradeDb.gameDao().selectSystemsWithCount()
            .map { systems -> systems.firstOrNull { it.systemId == SystemID.NDS.dbname }?.count ?: 0 }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun microphoneNotification(db: RetrogradeDatabase): Flow<Boolean> =
        microphonePermissionEnabledState.flatMapLatest { isMicrophoneEnabled ->
            if (isMicrophoneEnabled) flowOf(false)
            else combine(coresSelection.getSelectedCores(), dsGamesCount(db)) { cores, dsCount ->
                cores.any { it.coreConfig.supportsMicrophone } && dsCount > 0
            }.distinctUntilChanged()
        }

    private fun desmumeWarningNotification(): Flow<Boolean> =
        coresSelection.getSelectedCores()
            .map { cores -> cores.any { it.coreConfig.coreID == CoreID.DESMUME } }
            .distinctUntilChanged()
}
