package com.swordfish.chimeroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.swordfish.chimeroid.lib.core.CoreUpdater
import com.swordfish.chimeroid.lib.library.CoreID
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Downloads a zip of core-specific system assets from the Cores repo, extracts it under a
 * folder of its own inside the shared system directory, and skips re-downloading while the
 * extracted copy already matches [assetsVersion]. PPSSPP and Dolphin both need exactly this
 * shape; keeping it here avoids duplicating the streaming-extraction/version-gate logic per core.
 */
abstract class ZipAssetsManager : CoreID.AssetsManager {
    /** Folder created directly under the system directory, e.g. "PPSSPP", "dolphin-emu". */
    protected abstract val assetsFolderName: String
    protected abstract val assetsUrl: Uri
    protected abstract val assetsVersion: String
    protected abstract val assetsVersionKey: String

    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        getAssetsDirectory(directoriesManager).deleteRecursively()
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        if (!updateRequested(directoriesManager, sharedPreferences)) {
            return
        }

        try {
            val response = coreUpdaterApi.downloadZip(assetsUrl.toString())
            handleSuccess(directoriesManager, response, sharedPreferences)
        } catch (e: Throwable) {
            getAssetsDirectory(directoriesManager).deleteRecursively()
        }
    }

    private suspend fun handleSuccess(
        directoriesManager: DirectoriesManager,
        response: Response<ZipInputStream>,
        sharedPreferences: SharedPreferences,
    ) {
        val coreAssetsDirectory = getAssetsDirectory(directoriesManager)
        coreAssetsDirectory.deleteRecursively()
        coreAssetsDirectory.mkdirs()

        response.body()?.use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                Timber.d("Writing file: ${entry.name}")
                val destFile = File(coreAssetsDirectory, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    // Zip entries are expected in parent-then-child order (true for the
                    // assets this app ships), but creating the parent defensively here costs
                    // one cheap mkdirs() check per file and avoids a FileNotFoundException
                    // crash if a repackaged zip ever omits explicit directory entries.
                    destFile.parentFile?.mkdirs()
                    // .use{} guarantees the output stream is closed even if the copy throws
                    // partway through; leaving it unclosed here would leak one file
                    // descriptor per zip entry (this Sys folder alone has dozens of files).
                    destFile.outputStream().use { output -> zipInputStream.copyTo(output) }
                }
            }
        }

        sharedPreferences.edit()
            .putString(assetsVersionKey, assetsVersion)
            .commit()
    }

    private suspend fun updateRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val directoryExists = getAssetsDirectory(directoriesManager).exists()
            val currentVersion = sharedPreferences.getString(assetsVersionKey, "none")
            !directoryExists || currentVersion != assetsVersion
        }

    private suspend fun getAssetsDirectory(directoriesManager: DirectoriesManager): File =
        withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), assetsFolderName)
        }
}
