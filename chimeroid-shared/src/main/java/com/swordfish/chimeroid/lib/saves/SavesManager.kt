package com.swordfish.chimeroid.lib.saves

import com.swordfish.chimeroid.common.kotlin.runCatchingWithRetry
import com.swordfish.chimeroid.common.kotlin.writeBytesAtomic
import com.swordfish.chimeroid.lib.library.db.entity.Game
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSaveRAM(game: Game): ByteArray? {
        return withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    if (saveFile.exists() && saveFile.length() > 0) {
                        saveFile.readBytes()
                    } else {
                        null
                    }
                }
            result.getOrNull()
        }
    }

    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    if (data.isEmpty()) {
                        return@runCatchingWithRetry
                    }

                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    saveFile.writeBytesAtomic(data)
                }
            result.getOrNull()
        }
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo {
        return withContext(Dispatchers.IO) {
            val saveFile = getSaveFile(getSaveRAMFileName(game))
            val fileExists = saveFile.exists() && saveFile.length() > 0
            SaveInfo(fileExists, saveFile.lastModified())
        }
    }

    private fun getSaveFile(fileName: String): File {

        val savesDirectory = directoriesManager.getSavesDirectory()
        return File(savesDirectory, fileName)
    }

    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
