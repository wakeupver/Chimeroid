package com.swordfish.chimeroid.app.mobile.feature.settings.bios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.lib.bios.BiosManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class BiosSettingsViewModel(private val biosManager: BiosManager) : ViewModel() {
    class Factory
        @Inject
        constructor(biosManager: BiosManager) :
            ViewModelProvider.Factory by viewModelFactory({ BiosSettingsViewModel(biosManager) })

    val uiState =
        flow { emit(biosManager.getBiosInfoAsync()) }
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                BiosManager.BiosInfo(emptyList(), emptyList()),
            )
}
