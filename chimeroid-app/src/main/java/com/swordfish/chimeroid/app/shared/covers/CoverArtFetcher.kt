package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Coil [Fetcher] for [CoverRequest].
 *
 * Priority:
 *  1. Serve from a previously-saved JPEG in [CoverArtRepository.getCoverFile].
 *  2. Download from [Game.coverFrontUrl], scale + compress, save as JPEG, serve.
 *  3. Return null → Coil falls back to the drawable set via `fallback`/`error`.
 */
class CoverArtFetcher(
    private val data: CoverRequest,
    private val repository: CoverArtRepository,
    private val httpClient: OkHttpClient,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val game = data.game
        val localFile = repository.getCoverFile(game.id)

        // ── 1. Local cache hit ──────────────────────────────────────────────
        if (localFile.exists()) {
            return SourceResult(
                source = ImageSource(
                    source = localFile.source().buffer(),
                    context = options.context,
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }

        // ── 2. Download & persist ───────────────────────────────────────────
        val url = game.coverFrontUrl ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient
                    .newCall(Request.Builder().url(url).build())
                    .execute()

                if (!response.isSuccessful) {
                    Timber.w("Cover download [${response.code}] ${game.title}")
                    response.close()
                    return@withContext null
                }

                val body = response.body ?: run {
                    response.close()
                    return@withContext null
                }

                val saved = body.byteStream().use { stream ->
                    repository.saveCover(game.id, stream)
                }
                response.close()

                if (!saved || !localFile.exists()) return@withContext null

                SourceResult(
                    source = ImageSource(
                        source = localFile.source().buffer(),
                        context = options.context,
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.NETWORK,
                )
            } catch (e: Exception) {
                Timber.w(e, "Cover fetch error: ${game.title}")
                null
            }
        }
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    class Factory(context: Context) : Fetcher.Factory<CoverRequest> {

        val repository = CoverArtRepository(context.applicationContext)

        /** Shared client — reused by [CoverArtSyncWorker] via [sharedHttpClient]. */
        internal val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        override fun create(
            data: CoverRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = CoverArtFetcher(data, repository, httpClient, options)
    }
}
