package com.swordfish.chimeroid.app.mobile.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.common.paging.buildFlowPaging
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import com.swordfish.chimeroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

class FavoritesViewModel(retrogradeDb: RetrogradeDatabase) : ViewModel() {
    class Factory(
        retrogradeDb: RetrogradeDatabase,
    ) : ViewModelProvider.Factory by viewModelFactory({ FavoritesViewModel(retrogradeDb) })

    val favorites: Flow<PagingData<Game>> =
        buildFlowPaging(20, viewModelScope) { retrogradeDb.gameDao().selectFavorites() }
}
