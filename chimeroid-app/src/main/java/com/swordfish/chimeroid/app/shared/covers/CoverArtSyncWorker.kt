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
import timber.log.Timber
import javax.inject.Inject

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

        repository.invalidateSquishedCoversIfNeeded()

        val games = try {
            retrogradeDb.gameDao().selectAll()
        } catch (e: Exception) {
            Timber.e(e, "CoverArtSyncWorker: failed to query games")
            return Result.failure()
        }

        val validIds = games.map { it.id }.toSet()
        repository.pruneCovers(validIds)

        var downloaded = 0
        var failed = 0
        games.forEach { game ->
            if (repository.hasCover(game.id)) return@forEach
            val url = game.coverFrontUrl ?: return@forEach

            val ok = try {
                repository.persistCover(game.id, url, httpClient)
            } catch (e: Exception) {
                Timber.w(e, "CoverArtSyncWorker: cover fetch failed for '${game.title}'")
                false
            }

            if (ok) downloaded++ else failed++
        }

        Timber.i("CoverArtSyncWorker: downloaded=$downloaded failed=$failed total=${games.size}")

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
