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
import okio.buffer
import okio.source
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class CoverArtFetcher(
    private val data: CoverRequest,
    private val repository: CoverArtRepository,
    private val httpClient: OkHttpClient,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val game = data.game
        val localFile = repository.getCoverFile(game.id)

        if (localFile.exists()) {
            return toSourceResult(localFile, DataSource.DISK)
        }

        val coverUrl = game.coverFrontUrl ?: return null

        return withContext(Dispatchers.IO) {
            val saved = try {
                repository.persistCover(game.id, coverUrl, httpClient)
            } catch (e: Exception) {
                Timber.w(e, "Cover fetch error: ${game.title}")
                false
            }

            if (!saved || !localFile.exists()) return@withContext null

            toSourceResult(localFile, DataSource.NETWORK)
        }
    }

    private fun toSourceResult(
        file: File,
        dataSource: DataSource,
    ): SourceResult {
        return SourceResult(
            source = ImageSource(source = file.source().buffer(), context = options.context),
            mimeType = "image/jpeg",
            dataSource = dataSource,
        )
    }

    class Factory(context: Context) : Fetcher.Factory<CoverRequest> {

        val repository = CoverArtRepository(context.applicationContext)

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

