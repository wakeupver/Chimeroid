package com.swordfish.chimeroid.metadata.libretrodb

import android.net.Uri
import com.swordfish.chimeroid.common.kotlin.filterNullable
import com.swordfish.chimeroid.lib.library.GameSystem
import com.swordfish.chimeroid.lib.library.SystemID
import com.swordfish.chimeroid.lib.library.metadata.GameMetadata
import com.swordfish.chimeroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.chimeroid.lib.storage.StorageFile
import com.swordfish.chimeroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.chimeroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.chimeroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {
    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")

        private const val IMAGE_TYPE = "Named_Boxarts"

        private val FIRST_TAG_ONLY = Regex("""^.+? \([^()]*\)""")

        private const val FILENAME_CACHE_SIZE = 2_000
    }

    private val filenameCache: LinkedHashMap<String, LibretroRom?> =
        object : LinkedHashMap<String, LibretroRom?>(FILENAME_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, LibretroRom?>) =
                size > FILENAME_CACHE_SIZE
        }

    private val sortedSystemIds: List<String> by lazy {
        SystemID.entries
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        Timber.d("Looking metadata for file: $storageFile")

        val metadata =
            runCatching {
                val db = ovgdbManager.getDatabase()

                findByFilename(db, storageFile)
                    ?: findByPathAndFilename(db, storageFile)
                    ?: findBySerial(storageFile, db)
                    ?: findByCRC(storageFile, db)

                    ?: findByUniqueExtension(storageFile)
                    ?: findByKnownSystem(storageFile)
                    ?: findByPathAndSupportedExtension(storageFile)
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata? {
        val systemId = rom.system ?: return null
        val system = GameSystem.findByIdOrNull(systemId) ?: return null
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = systemId,
            developer = rom.developer,
        )
    }

    private suspend fun cachedFindByFileName(
        db: LibretroDatabase,
        name: String,
    ): LibretroRom? {

        synchronized(filenameCache) { if (filenameCache.containsKey(name)) return filenameCache[name] }

        val result = db.gameDao().findByFileName(name)
        synchronized(filenameCache) { filenameCache[name] = result }
        return result
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return cachedFindByFileName(db, file.name)
            .filterNullable { extractGameSystemOrNull(it)?.scanOptions?.scanByFilename == true }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {

        return cachedFindByFileName(db, file.name)
            .filterNullable { extractGameSystemOrNull(it)?.scanOptions?.scanByPathAndFilename == true }
            .filterNullable { rom ->
                extractGameSystemOrNull(rom)?.id?.dbname
                    ?.let { parentContainsSystem(file.path, it) } == true
            }
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        val system =
            sortedSystemIds
                .asSequence()
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let { buildFallbackMetadata(file, it) }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        return parent?.lowercase(Locale.getDefault())?.contains(dbname) == true
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {

        val crc = file.crc?.takeIf { it != "0" } ?: return null

        return db.gameDao().findByCRC(crc.uppercase(Locale.US))
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        val serial = file.serial ?: return null
        return db.gameDao().findBySerial(serial)
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        val systemID = file.systemID ?: return null

        val system = GameSystem.findByIdOrNull(systemID.dbname)

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = system?.let { computeCoverUrl(it, stripSecondaryTags(file.extensionlessName)) },
            system = systemID.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByFileName(file.name) ?: return null

        if (!system.scanOptions.scanByUniqueExtension) {
            return null
        }

        return buildFallbackMetadata(file, system)
    }

    private fun extractGameSystemOrNull(rom: LibretroRom): GameSystem? =
        rom.system?.let { GameSystem.findByIdOrNull(it) }

    private fun buildFallbackMetadata(
        file: StorageFile,
        system: GameSystem,
    ): GameMetadata =
        GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = computeCoverUrl(system, stripSecondaryTags(file.extensionlessName)),
            system = system.id.dbname,
            developer = null,
        )

    private fun stripSecondaryTags(name: String): String = FIRST_TAG_ONLY.find(name)?.value ?: name

    private fun computeCoverUrl(
        system: GameSystem,
        name: String?,
    ): String? {
        name ?: return null

        val systemName = if (system.id == SystemID.MAME2003PLUS) "MAME" else system.libretroFullName
        val thumbGameName = name.replace(THUMB_REPLACE, "_")

        val encodedSystem = Uri.encode(systemName)
        val encodedThumb = Uri.encode(thumbGameName)

        return "https://thumbnails.libretro.com/$encodedSystem/$IMAGE_TYPE/$encodedThumb.png"
    }
}