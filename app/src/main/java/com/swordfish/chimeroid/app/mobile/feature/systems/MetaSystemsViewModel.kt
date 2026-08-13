package com.swordfish.chimeroid.app.mobile.feature.systems

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.chimeroid.app.shared.systems.MetaSystemInfo
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.lib.library.GameSystem
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import com.swordfish.chimeroid.lib.library.metaSystemID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MetaSystemsViewModel(retrogradeDb: RetrogradeDatabase, appContext: Context) : ViewModel() {
    class Factory(
        retrogradeDb: RetrogradeDatabase,
        appContext: Context,
    ) : ViewModelProvider.Factory by viewModelFactory({ MetaSystemsViewModel(retrogradeDb, appContext) })

    val availableMetaSystems: Flow<List<MetaSystemInfo>> =
        retrogradeDb.gameDao()
            .selectSystemsWithCount()
            .map { systemCounts ->
                systemCounts.asSequence()
                    .filter { (_, count) -> count > 0 }
                    .map { (systemId, count) -> GameSystem.findById(systemId).metaSystemID() to count }
                    .groupBy { (metaSystemId, _) -> metaSystemId }
                    .map { (metaSystemId, counts) -> MetaSystemInfo(metaSystemId, counts.sumOf { it.second }) }
                    .sortedBy { it.getName(appContext) }
                    .toList()
            }
}
