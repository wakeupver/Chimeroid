package com.swordfish.chimeroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.swordfish.chimeroid.lib.core.CoreUpdater
import com.swordfish.chimeroid.lib.library.CoreID
import com.swordfish.chimeroid.lib.storage.DirectoriesManager

class NoAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {}

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
    }
}
