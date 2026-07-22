package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.swordfish.chimeroid.common.bitmap.downscaledToFit
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manages per-game cover JPEG files in [coversDir] and a single-file
 * ZIP pack ([packFile]) that aggregates all of them.
 *
 * Storage layout inside [Context.getFilesDir]:
 *
 *   files/
 *     covers/
 *       1.jpg          ← game id 1
 *       2.jpg          ← game id 2
 *       …
 *       covers.zip     ← packed archive (regenerated after bulk sync)
 *
 * Reads (Coil Fetcher) go directly to the individual .jpg files so they
 * are always fast and never contend with a pack rebuild.
 */
class CoverArtRepository(private val context: Context) {

    companion object {
        const val COVER_MAX_PX = 512
        const val JPEG_QUALITY = 85
        const val PACK_FILE_NAME = "covers.zip"

        private const val PREFS_NAME = "cover_art_repository"
        private const val KEY_ASPECT_FIX_VERSION = "aspect_fix_version"

        /** Bump when a change to [CoverArtRepository.saveCover] invalidates previously cached JPEGs. */
        private const val CURRENT_ASPECT_FIX_VERSION = 1

        /** Schemes served straight from disk via [android.content.ContentResolver] — no network I/O. */
        private val LOCAL_URI_SCHEMES = setOf("content", "file")

        /**
         * Matches the last "(Tag)" immediately before ".png" in a thumbnails.libretro.com URL —
         * on a Named_Boxarts filename this is almost always the release region.
         */
        private val TRAILING_TAG = Regex("""\(([^()]*)\)\.png$""")

        /**
         * Regions retried, in order, when the ROM's own region tag has no thumbnail — e.g. a
         * USA ROM whose only libretro-thumbnails entry is tagged (Europe). Bounded to the 4
         * most common single-word tags; compound tags ("Japan, Asia") aren't attempted.
         */
        private val REGION_FALLBACKS = listOf("World", "USA", "Europe", "Japan")

        /** True when [coverUrl] is an on-device `content://`/`file://` image rather than a remote HTTP(S) thumbnail. */
        fun isLocalCoverUri(coverUrl: String): Boolean = Uri.parse(coverUrl).scheme in LOCAL_URI_SCHEMES
    }

    val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    /** The single-file ZIP archive that bundles every cover JPEG. */
    val packFile: File
        get() = File(coversDir, PACK_FILE_NAME)

    /** Every persisted cover JPEG currently in [coversDir]. */
    private val coverJpegFiles: List<File>
        get() = coversDir.listFiles { f -> f.extension == "jpg" }?.toList() ?: emptyList()

    fun getCoverFile(gameId: Int): File = File(coversDir, "$gameId.jpg")

    fun hasCover(gameId: Int): Boolean = getCoverFile(gameId).exists()

    /**
     * Cover JPEGs saved before [CURRENT_ASPECT_FIX_VERSION] were forced into an exact
     * [COVER_MAX_PX]×[COVER_MAX_PX] square, squishing any non-square box art. Wipes the
     * cache once so the next sync re-downloads every cover through the corrected
     * [downscaledToFit] path. Idempotent — a no-op on every call after the first.
     *
     * Not thread-safe against concurrent [saveCover] writers; call only from
     * [CoverArtSyncWorker]'s serialized background sync, before any covers are (re)written.
     */
    internal fun invalidateSquishedCoversIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_ASPECT_FIX_VERSION, 0) >= CURRENT_ASPECT_FIX_VERSION) return

        coverJpegFiles.forEach { it.delete() }
        if (packFile.exists()) packFile.delete()
        prefs.edit().putInt(KEY_ASPECT_FIX_VERSION, CURRENT_ASPECT_FIX_VERSION).apply()
        Timber.i("invalidateSquishedCoversIfNeeded: cleared cover cache for pre-fix squished covers")
    }

    /**
     * Decodes [inputStream] as a bitmap, downscales it — preserving aspect ratio — so its
     * longer side is at most [COVER_MAX_PX]px, and writes a JPEG to disk. Returns true on
     * success.
     */
    fun saveCover(gameId: Int, inputStream: InputStream): Boolean {
        return try {
            val raw = BitmapFactory.decodeStream(inputStream) ?: return false
            if (raw.width <= 0 || raw.height <= 0) {
                raw.recycle()
                return false
            }

            val scaled = raw.downscaledToFit(COVER_MAX_PX)
                .also { if (it !== raw) raw.recycle() }

            val outFile = getCoverFile(gameId)
            outFile.outputStream().buffered().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            scaled.recycle()
            Timber.v("Saved cover for game $gameId → ${outFile.length()} bytes")
            true
        } catch (e: Exception) {
            Timber.e(e, "saveCover failed for game $gameId")
            false
        }
    }

    /**
     * Persists whichever cover source [coverUrl] refers to as this game's cached JPEG — a
     * remote HTTP(S) thumbnail, or an on-device `content://`/`file://` image such as a PICO-8
     * `.p8.png` cart (a real PNG in its own right). Single entry point shared by
     * [CoverArtFetcher] (on-demand) and [CoverArtSyncWorker] (background sync).
     *
     * @throws Exception on I/O failure — callers decide how to log/report it.
     */
    fun persistCover(
        gameId: Int,
        coverUrl: String,
        httpClient: OkHttpClient,
    ): Boolean {
        return if (isLocalCoverUri(coverUrl)) {
            context.contentResolver.openInputStream(Uri.parse(coverUrl))?.use { saveCover(gameId, it) } ?: false
        } else {
            downloadCover(gameId, coverUrl, httpClient)
        }
    }

    /**
     * Downloads [url] and, on failure, retries the same title under each of [REGION_FALLBACKS]
     * before giving up — libretro-thumbnails catalogues only one region per game, which is
     * often not the one the local ROM's own filename happens to be tagged with.
     */
    private fun downloadCover(
        gameId: Int,
        url: String,
        httpClient: OkHttpClient,
    ): Boolean {
        if (tryDownload(gameId, url, httpClient)) return true

        val currentRegion = TRAILING_TAG.find(url)?.groups?.get(1) ?: return false

        return REGION_FALLBACKS
            .asSequence()
            .filter { it != currentRegion.value }
            .map { url.replaceRange(currentRegion.range, it) }
            .any { tryDownload(gameId, it, httpClient) }
    }

    private fun tryDownload(
        gameId: Int,
        url: String,
        httpClient: OkHttpClient,
    ): Boolean {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Cover download [${response.code}] game=$gameId url=$url")
                return false
            }
            return response.body?.byteStream()?.use { saveCover(gameId, it) } ?: false
        }
    }

    /**
     * Packs every *.jpg in [coversDir] into [packFile] using DEFLATE compression.
     * Uses an atomic temp-file swap so readers never see a partial archive.
     *
     * @return the final pack file.
     */
    fun packCovers(): File {
        val covers = coverJpegFiles

        if (covers.isEmpty()) {
            Timber.i("packCovers: nothing to pack")
            return packFile
        }

        val tempPack = File(coversDir, "covers.zip.tmp")
        tempPack.delete()

        ZipOutputStream(tempPack.outputStream().buffered()).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)
            covers.forEach { cover ->
                val entry = ZipEntry(cover.name)
                zos.putNextEntry(entry)
                cover.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        if (packFile.exists()) packFile.delete()
        tempPack.renameTo(packFile)

        Timber.i("packCovers: ${covers.size} covers → ${packFile.length() / 1024} KB")
        return packFile
    }

    /**
     * Deletes cover files for game IDs that are no longer in [validGameIds].
     */
    fun pruneCovers(validGameIds: Set<Int>) {
        coverJpegFiles.forEach { file ->
            val id = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
            if (id !in validGameIds) {
                file.delete()
                Timber.d("pruneCovers: removed $id.jpg")
            }
        }
    }
}
