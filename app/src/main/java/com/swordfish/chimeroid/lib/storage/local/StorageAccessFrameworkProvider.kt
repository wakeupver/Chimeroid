package com.swordfish.chimeroid.lib.storage.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.swordfish.chimeroid.common.kotlin.closeDetachedFds
import com.swordfish.chimeroid.common.kotlin.extractEntryToFile
import com.swordfish.chimeroid.common.kotlin.isZipped
import com.swordfish.chimeroid.common.kotlin.writeToFile
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.lib.library.db.entity.DataFile
import com.swordfish.chimeroid.lib.library.db.entity.Game
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.chimeroid.lib.storage.BaseStorageFile
import com.swordfish.chimeroid.lib.storage.RomFiles
import com.swordfish.chimeroid.lib.storage.StorageFile
import com.swordfish.chimeroid.lib.storage.StorageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class StorageAccessFrameworkProvider(private val context: Context) : StorageProvider {
    override val id: String = "access_framework"

    override val name: String = context.getString(R.string.local_storage)

    override val uriSchemes = listOf("content")

    override val prefsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    override val enabledByDefault = true

    override fun listBaseStorageFiles(): Flow<List<BaseStorageFile>> {
        return getExternalFolder()?.let { folder ->
            traverseDirectoryEntries(Uri.parse(folder))
        } ?: emptyFlow()
    }

    override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? {
        return DocumentFileParser.parseDocumentFile(context, baseStorageFile)
    }

    private fun getExternalFolder(): String? {
        val prefString = context.getString(R.string.pref_key_extenral_folder)
        val preferenceManager = SharedPreferencesHelper.getLegacySharedPreferences(context)
        return preferenceManager.getString(prefString, null)
    }

    private fun traverseDirectoryEntries(rootUri: Uri): Flow<List<BaseStorageFile>> =
        channelFlow {
            val rootDocumentId = DocumentsContract.getTreeDocumentId(rootUri) ?: return@channelFlow

            fun launchTraversal(documentId: String) {
                launch {
                    val result = runCatching { listBaseStorageFiles(rootUri, documentId) }
                    if (result.isFailure) {
                        Timber.e(result.exceptionOrNull(), "Error listing files in $documentId")
                    }
                    val (files, subDirs) = result.getOrDefault(emptyList<BaseStorageFile>() to emptyList())
                    if (files.isNotEmpty()) send(files)
                    subDirs.forEach { launchTraversal(it) }
                }
            }

            launchTraversal(rootDocumentId)
        }

    private fun listBaseStorageFiles(
        treeUri: Uri,
        rootDocumentId: String,
    ): Pair<List<BaseStorageFile>, List<String>> {
        val resultFiles = mutableListOf<BaseStorageFile>()
        val resultDirectories = mutableListOf<String>()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)

        Timber.d("Querying files in directory: $childrenUri")

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use {
            while (it.moveToNext()) {
                val documentId = it.getString(0)
                val documentName = it.getString(1)
                val documentSize = it.getLong(2)
                val mimeType = it.getString(3)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    resultDirectories.add(documentId)
                } else {
                    val documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    resultFiles.add(
                        BaseStorageFile(
                            name = documentName,
                            size = documentSize,
                            uri = documentUri,
                            path = documentUri.path,
                        ),
                    )
                }
            }
        }

        return resultFiles to resultDirectories
    }

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val originalDocumentUri = Uri.parse(game.fileUri)
        val originalDocument =
            DocumentFile.fromSingleUri(context, originalDocumentUri)
                ?: error("$TAG: cannot resolve document for ${game.fileUri}")

        val isZipped = originalDocument.isZipped() && originalDocument.name != game.fileName

        return when {
            isZipped -> getGameRomFilesZipped(game, originalDocument)
            allowVirtualFiles -> getGameRomFilesDirect(game, dataFiles)
            else -> getGameRomFilesCached(game, dataFiles)
        }
    }

    private fun getGameRomFilesZipped(
        game: Game,
        originalDocument: DocumentFile,
    ): RomFiles {
        val cacheFile = GameCacheUtils.getCacheFileForGame(SAF_CACHE_SUBFOLDER, context, game)
        if (!cacheFile.exists()) {
            ZipInputStream(openInputStreamOrThrow(originalDocument.uri))
                .extractEntryToFile(game.fileName, cacheFile)
        }
        return RomFiles(files = listOf(cacheFile))
    }

    private fun getGameRomFilesDirect(game: Game, dataFiles: List<DataFile>): RomFiles {
        val detachedFds = mutableListOf<Int>()

        fun resolveUri(uri: Uri): File {

            resolveRealFilePath(uri)?.let { return it }

            val pfd =
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("$TAG: cannot open file descriptor for $uri")
            val rawFd = pfd.detachFd()
            detachedFds += rawFd
            Timber.d("$TAG: detached fd=$rawFd for $uri -> /proc/self/fd/$rawFd")
            return File("/proc/self/fd/$rawFd")
        }

        return try {
            val gameFile = resolveUri(Uri.parse(game.fileUri))
            val dataFileItems = dataFiles.map { resolveUri(Uri.parse(it.fileUri)) }
            RomFiles(files = listOf(gameFile) + dataFileItems, detachedFds = detachedFds)
        } catch (t: Throwable) {
            detachedFds.closeDetachedFds(TAG)
            throw t
        }
    }

    private fun getGameRomFilesCached(game: Game, dataFiles: List<DataFile>): RomFiles {
        fun resolveOrCache(uri: Uri, cacheFile: File): File {
            resolveRealFilePath(uri)?.let { return it }
            if (!cacheFile.exists()) {
                openInputStreamOrThrow(uri).writeToFile(cacheFile)
            }
            return cacheFile
        }

        val gameFile =
            resolveOrCache(
                Uri.parse(game.fileUri),
                GameCacheUtils.getCacheFileForGame(SAF_CACHE_SUBFOLDER, context, game),
            )
        val dataFileItems =
            dataFiles.map {
                resolveOrCache(
                    Uri.parse(it.fileUri),
                    GameCacheUtils.getDataFileForGame(SAF_CACHE_SUBFOLDER, context, game, it),
                )
            }

        return RomFiles(files = listOf(gameFile) + dataFileItems)
    }

    private fun resolveRealFilePath(uri: Uri): File? {
        return try {
            if (uri.scheme == "file") return File(uri.path ?: return null)
            if (!DocumentsContract.isDocumentUri(context, uri)) return null

            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":").takeIf { it.size == 2 } ?: return null
            val (volumeId, relativePath) = parts

            val root = when {
                volumeId.equals("primary", ignoreCase = true) ->
                    Environment.getExternalStorageDirectory()
                else ->
                    File("/storage/$volumeId")
            }

            File(root, relativePath).takeIf { it.exists() && it.canRead() }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: could not resolve real path for $uri, will use fallback")
            null
        }
    }

    private fun openInputStreamOrThrow(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: error("$TAG: contentResolver returned no stream for $uri")

    override fun getInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    companion object {
        const val SAF_CACHE_SUBFOLDER = "storage-framework-games"
        private const val TAG = "SAFProvider"
    }
}
