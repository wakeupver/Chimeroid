package com.swordfish.chimeroid.app.mobile.feature.settings.coreselection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.chimeroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.chimeroid.app.utils.android.viewmodel.viewModelFactory
import com.swordfish.chimeroid.lib.core.CoresSelection
import com.swordfish.chimeroid.lib.library.GameSystem
import com.swordfish.chimeroid.lib.library.SystemCoreConfig
import kotlinx.coroutines.launch

class CoresSelectionViewModel(
    context: Context,
    private val coresSelection: CoresSelection,
) : ViewModel() {
    class Factory(
        context: Context,
        coresSelection: CoresSelection,
    ) : ViewModelProvider.Factory by viewModelFactory({ CoresSelectionViewModel(context, coresSelection) })

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    fun getSelectedCores() = coresSelection.getSelectedCores()

    fun changeCore(
        system: GameSystem,
        coreConfig: SystemCoreConfig,
        context: Context,
    ) {
        viewModelScope.launch {
            coresSelection.updateCoreConfigForSystem(system, coreConfig.coreID)
            LibraryIndexScheduler.scheduleCoreUpdate(context)
        }
    }
}
