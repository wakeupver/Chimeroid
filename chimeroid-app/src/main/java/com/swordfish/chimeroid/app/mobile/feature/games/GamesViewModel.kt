package com.swordfish.chimeroid.app.mobile.feature.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.common.paging.buildFlowPaging
import com.swordfish.chimeroid.lib.library.MetaSystemID
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import com.swordfish.chimeroid.lib.library.db.entity.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class GamesViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    initialMetaSystem: MetaSystemID,
) : ViewModel() {
    class Factory(
        retrogradeDb: RetrogradeDatabase,
        initialMetaSystem: MetaSystemID,
    ) : ViewModelProvider.Factory by viewModelFactory({ GamesViewModel(retrogradeDb, initialMetaSystem) })

    private val metaSystemId = MutableStateFlow(initialMetaSystem)

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        metaSystemId
            .map { metaSystem -> metaSystem.systemIDs }
            .map { systemIds -> systemIds.map { it.dbname } }
            .flatMapLatest {
                when (it.size) {
                    0 -> emptyFlow()
                    1 -> buildFlowPaging(20, viewModelScope) { retrogradeDb.gameDao().selectBySystem(it.first()) }
                    else -> buildFlowPaging(20, viewModelScope) { retrogradeDb.gameDao().selectBySystems(it) }
                }
            }
}
