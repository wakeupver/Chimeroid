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
import com.swordfish.chimeroid.lib.R
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

    /**
     * Traverses directories in parallel using channelFlow + recursive coroutine launches.
     */
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

        // allowVirtualFiles mirrors the user's "Allow Direct Game Load" setting. When it is
        // off we must never fall back to fd-detach + /proc/self/fd — that path exists
        // specifically as an opt-in for providers that don't expose a real filesystem path,
        // and some devices/cores handle it worse than a plain cached copy. Honoring the flag
        // here (instead of always taking the fd path) is what makes the setting meaningful.
        return when {
            isZipped -> getGameRomFilesZipped(game, originalDocument)
            allowVirtualFiles -> getGameRomFilesDirect(game, dataFiles)
            else -> getGameRomFilesCached(game, dataFiles)
        }
    }

    // ── ZIP path ────────────────────────────────────────────────────────────────

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

    // ── Direct (no-copy) path ────────────────────────────────────────────────

    /**
     * Loads all ROM files without copying a single byte to the cache.
     *
     * Mirrors the technique used by PPSSPP in ContentUri.java → openContentUri():
     *
     *   pfd = contentResolver.openFileDescriptor(uri, "r")
     *   fd  = pfd.detachFd()   ← "Take ownership of the fd" (PPSSPP comment)
     *
     * After [android.os.ParcelFileDescriptor.detachFd] the Java PFD wrapper is invalidated,
     * but the underlying kernel file-descriptor remains open and owned by our process.
     * There is no GC risk — the int fd is just a number; nothing will close it until we
     * explicitly adopt + close it via [closeDetachedFds] in onCleared().
     *
     * The path "/proc/self/fd/{fd}" is a stable symlink the kernel maintains for every
     * open fd in the process. libretrodroid opens this path exactly like a real file.
     *
     * For URIs that resolve to a real filesystem path (primary storage, SD card) we skip
     * the fd entirely — zero overhead, purest possible load.
     *
     * If resolving any file in the set fails partway through, every fd already detached in
     * this call is closed before the exception propagates — otherwise a failed multi-disc
     * load would leak one kernel fd per file that succeeded before the failure, and repeated
     * failed attempts would eventually exhaust the process's fd limit.
     */
    private fun getGameRomFilesDirect(game: Game, dataFiles: List<DataFile>): RomFiles {
        val detachedFds = mutableListOf<Int>()

        fun resolveUri(uri: Uri): File {
            // Fast path: real filesystem path — no fd needed at all.
            resolveRealFilePath(uri)?.let { return it }

            // PPSSPP path: detachFd() → /proc/self/fd/N.
            // pfd is invalidated immediately after detachFd(); the kernel fd lives on.
            val pfd =
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("$TAG: cannot open file descriptor for $uri")
            val rawFd = pfd.detachFd() // ← "Take ownership of the fd" — identical to PPSSPP
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

    // ── Cached fallback (direct-load disabled) ────────────────────────────────

    /**
     * Safe fallback for when "Allow Direct Game Load" is off: real filesystem paths are
     * still used as-is (zero overhead), but any URI that would otherwise require the fd
     * trick is instead copied once into the cache. No fd is ever opened here, so there is
     * nothing to detach, track, or leak — trading a one-time copy for maximum compatibility.
     */
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Resolves a SAF content URI to a real [File] path without opening the file.
     * Handles the primary volume and removable SD cards.
     * Returns null when the real path cannot be determined (caller should fall back).
     */
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
