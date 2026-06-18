package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.swordfish.chimeroid.lib.injection.AndroidWorkerInjection
import com.swordfish.chimeroid.lib.injection.WorkerKey
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject

/**
 * Downloads cover art for every game that is missing a local JPEG, then
 * re-packs all covers into a single [CoverArtRepository.packFile] ZIP.
 *
 * Scheduled by [com.swordfish.chimeroid.app.shared.library.LibraryIndexScheduler]
 * after each library scan completes.
 */
class CoverArtSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        return withContext(Dispatchers.IO) { runSync() }
    }

    private suspend fun runSync(): Result {
        val repository = CoverArtRepository(applicationContext)
        val httpClient = CoverArtFetcher.Factory(applicationContext).httpClient

        val games = try {
            retrogradeDb.gameDao().selectAll()
        } catch (e: Exception) {
            Timber.e(e, "CoverArtSyncWorker: failed to query games")
            return Result.failure()
        }

        // Prune orphaned covers (games deleted from library)
        val validIds = games.map { it.id }.toSet()
        repository.pruneCovers(validIds)

        // Download missing covers
        var downloaded = 0
        var failed = 0
        games.forEach { game ->
            if (repository.hasCover(game.id)) return@forEach
            val url = game.coverFrontUrl ?: return@forEach

            val ok = try {
                val response = httpClient
                    .newCall(Request.Builder().url(url).build())
                    .execute()

                if (!response.isSuccessful) {
                    response.close()
                    false
                } else {
                    val body = response.body
                    val saved = body?.byteStream()?.use { stream ->
                        repository.saveCover(game.id, stream)
                    } ?: false
                    response.close()
                    saved
                }
            } catch (e: Exception) {
                Timber.w(e, "CoverArtSyncWorker: download failed for '${game.title}'")
                false
            }

            if (ok) downloaded++ else failed++
        }

        Timber.i("CoverArtSyncWorker: downloaded=$downloaded failed=$failed total=${games.size}")

        // Repack only when something changed
        if (downloaded > 0 || repository.packFile.exists().not()) {
            try {
                repository.packCovers()
            } catch (e: Exception) {
                Timber.e(e, "CoverArtSyncWorker: packCovers failed")
            }
        }

        return Result.success()
    }

    companion object {
        val WORK_ID: String = CoverArtSyncWorker::class.java.simpleName

        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_ID,
                    androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<CoverArtSyncWorker>().build(),
                )
        }
    }

    // ── Dagger wiring (mirrors LibraryIndexWork pattern) ────────────────────

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(CoverArtSyncWorker::class)
        abstract fun bindFactory(
            builder: Subcomponent.Builder,
        ): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<CoverArtSyncWorker> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<CoverArtSyncWorker>()
    }
}
