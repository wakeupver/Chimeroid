package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.swordfish.chimeroid.common.bitmap.downscaledToFit
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoverArtRepository(private val context: Context) {

    companion object {
        const val COVER_MAX_PX = 512
        const val JPEG_QUALITY = 85
        const val PACK_FILE_NAME = "covers.zip"

        private const val PREFS_NAME = "cover_art_repository"
        private const val KEY_ASPECT_FIX_VERSION = "aspect_fix_version"

        private const val CURRENT_ASPECT_FIX_VERSION = 1

        private val TRAILING_TAG = Regex("""\(([^()]*)\)\.png$""")

        private val REGION_FALLBACKS = listOf("World", "USA", "Europe", "Japan")
    }

    val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    val packFile: File
        get() = File(coversDir, PACK_FILE_NAME)

    private val coverJpegFiles: List<File>
        get() = coversDir.listFiles { f -> f.extension == "jpg" }?.toList() ?: emptyList()

    fun getCoverFile(gameId: Int): File = File(coversDir, "$gameId.jpg")

    fun hasCover(gameId: Int): Boolean = getCoverFile(gameId).exists()

    internal fun invalidateSquishedCoversIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_ASPECT_FIX_VERSION, 0) >= CURRENT_ASPECT_FIX_VERSION) return

        coverJpegFiles.forEach { it.delete() }
        if (packFile.exists()) packFile.delete()
        prefs.edit().putInt(KEY_ASPECT_FIX_VERSION, CURRENT_ASPECT_FIX_VERSION).apply()
        Timber.i("invalidateSquishedCoversIfNeeded: cleared cover cache for pre-fix squished covers")
    }

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

    fun persistCover(
        gameId: Int,
        coverUrl: String,
        httpClient: OkHttpClient,
    ): Boolean = downloadCover(gameId, coverUrl, httpClient)

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
