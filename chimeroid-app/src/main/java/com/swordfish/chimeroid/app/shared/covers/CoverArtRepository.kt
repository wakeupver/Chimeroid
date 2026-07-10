package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

        /** Schemes served straight from disk via [android.content.ContentResolver] — no network I/O. */
        private val LOCAL_URI_SCHEMES = setOf("content", "file")

        /** True when [coverUrl] is an on-device `content://`/`file://` image rather than a remote HTTP(S) thumbnail. */
        fun isLocalCoverUri(coverUrl: String): Boolean = Uri.parse(coverUrl).scheme in LOCAL_URI_SCHEMES
    }

    val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    /** The single-file ZIP archive that bundles every cover JPEG. */
    val packFile: File
        get() = File(coversDir, PACK_FILE_NAME)

    fun getCoverFile(gameId: Int): File = File(coversDir, "$gameId.jpg")

    fun hasCover(gameId: Int): Boolean = getCoverFile(gameId).exists()

    /**
     * Decodes [inputStream] as a bitmap, scales it down to [COVER_MAX_PX]×[COVER_MAX_PX],
     * and writes a JPEG to disk. Returns true on success.
     */
    fun saveCover(gameId: Int, inputStream: InputStream): Boolean {
        return try {
            val raw = BitmapFactory.decodeStream(inputStream) ?: return false
            val scaled = if (raw.width > COVER_MAX_PX || raw.height > COVER_MAX_PX) {
                Bitmap.createScaledBitmap(raw, COVER_MAX_PX, COVER_MAX_PX, true)
                    .also { if (it !== raw) raw.recycle() }
            } else {
                raw
            }

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

    private fun downloadCover(
        gameId: Int,
        url: String,
        httpClient: OkHttpClient,
    ): Boolean {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.w("Cover download [${response.code}] game=$gameId")
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
        val covers = coversDir.listFiles { f ->
            f.extension == "jpg"
        } ?: emptyArray<File>()

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
        coversDir.listFiles { f -> f.extension == "jpg" }
            ?.forEach { file ->
                val id = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
                if (id !in validGameIds) {
                    file.delete()
                    Timber.d("pruneCovers: removed $id.jpg")
                }
            }
    }
}
