/*
 * RetrogradeApplicationModule.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.chimeroid.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.swordfish.chimeroid.app.mobile.feature.game.GameActivity
import com.swordfish.chimeroid.app.mobile.feature.game.GameService
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.chimeroid.app.mobile.feature.input.GamePadBindingActivity
import com.swordfish.chimeroid.app.mobile.feature.input.GamePadShortcutBindingActivity
import com.swordfish.chimeroid.app.mobile.feature.main.MainActivity
import com.swordfish.chimeroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.chimeroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.chimeroid.app.shared.game.ExternalGameLauncherActivity
import com.swordfish.chimeroid.app.shared.game.GameLauncher
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.chimeroid.app.shared.rumble.RumbleManager
import com.swordfish.chimeroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.chimeroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.chimeroid.ext.feature.core.CoreUpdaterImpl
import com.swordfish.chimeroid.ext.feature.review.ReviewManager
import com.swordfish.chimeroid.ext.feature.savesync.SaveSyncManagerImpl
import com.swordfish.chimeroid.lib.bios.BiosManager
import com.swordfish.chimeroid.lib.core.CoreUpdater
import com.swordfish.chimeroid.lib.core.CoreVariablesManager
import com.swordfish.chimeroid.lib.core.CoresSelection
import com.swordfish.chimeroid.lib.game.GameLoader
import com.swordfish.chimeroid.lib.injection.PerActivity
import com.swordfish.chimeroid.lib.injection.PerApp
import com.swordfish.chimeroid.lib.library.ChimeroidLibrary
import com.swordfish.chimeroid.lib.library.db.RetrogradeDatabase
import com.swordfish.chimeroid.lib.library.db.dao.GameSearchDao
import com.swordfish.chimeroid.lib.library.db.dao.Migrations
import com.swordfish.chimeroid.lib.library.db.dao.PatchCodeDao
import com.swordfish.chimeroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.chimeroid.lib.migration.DesmumeMigrationHandler
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.chimeroid.lib.saves.SavesCoherencyEngine
import com.swordfish.chimeroid.lib.saves.SavesManager
import com.swordfish.chimeroid.lib.saves.StatesManager
import com.swordfish.chimeroid.lib.saves.StatesPreviewManager
import com.swordfish.chimeroid.lib.savesync.SaveSyncManager
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import com.swordfish.chimeroid.lib.storage.StorageProvider
import com.swordfish.chimeroid.lib.storage.StorageProviderRegistry
import com.swordfish.chimeroid.lib.storage.local.LocalStorageProvider
import com.swordfish.chimeroid.lib.storage.local.StorageAccessFrameworkProvider
import com.swordfish.chimeroid.metadata.libretrodb.LibretroDBMetadataProvider
import com.swordfish.chimeroid.metadata.libretrodb.db.LibretroDBManager
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.InputStream
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

@Module
abstract class ChimeroidApplicationModule {
    @Binds
    abstract fun context(app: ChimeroidApplication): Context

    @Binds
    abstract fun saveSyncManager(saveSyncManagerImpl: SaveSyncManagerImpl): SaveSyncManager

    @PerActivity
    @ContributesAndroidInjector(modules = [MainActivity.Module::class])
    abstract fun mainActivity(): MainActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun externalGameLauncherActivity(): ExternalGameLauncherActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun gameActivity(): GameActivity

    @ContributesAndroidInjector
    abstract fun gameService(): GameService

    @PerActivity
    @ContributesAndroidInjector(modules = [GameMenuActivity.Module::class])
    abstract fun gameMenuActivity(): GameMenuActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun storageFrameworkPickerLauncher(): StorageFrameworkPickerLauncher

    @ContributesAndroidInjector
    abstract fun storageBaseDirPicker(): com.swordfish.chimeroid.app.shared.settings.StorageBaseDirPicker

    @PerActivity
    @ContributesAndroidInjector(modules = [GamePadBindingActivity.Module::class])
    abstract fun gamepadBindingActivity(): GamePadBindingActivity

    @PerActivity
    @ContributesAndroidInjector(modules = [GamePadShortcutBindingActivity.Module::class])
    abstract fun gamepadShortcutBindingActivity(): GamePadShortcutBindingActivity

    @Module
    companion object {
        @Provides
        @PerApp
        @JvmStatic
        fun libretroDBManager(app: ChimeroidApplication) = LibretroDBManager(app)

        @Provides
        @PerApp
        @JvmStatic
        fun retrogradeDb(app: ChimeroidApplication) =
            Room.databaseBuilder(app, RetrogradeDatabase::class.java, RetrogradeDatabase.DB_NAME)
                .addCallback(GameSearchDao.CALLBACK)
                .addMigrations(
                    GameSearchDao.MIGRATION,
                    Migrations.VERSION_8_9,
                    Migrations.VERSION_9_10,
                    Migrations.VERSION_10_11,
                )
                .fallbackToDestructiveMigration()
                // WAL mode allows concurrent reads without blocking writers,
                // which is essential now that scan batches run in parallel.
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        @Provides
        @PerApp
        @JvmStatic
        fun gameMetadataProvider(libretroDBManager: LibretroDBManager): GameMetadataProvider =
            LibretroDBMetadataProvider(libretroDBManager)

        @Provides
        @PerApp
        @IntoSet
        @JvmStatic
        fun localSAFStorageProvider(context: Context): StorageProvider = StorageAccessFrameworkProvider(context)

        @Provides
        @PerApp
        @IntoSet
        @JvmStatic
        fun localGameStorageProvider(
            context: Context,
            directoriesManager: DirectoriesManager,
        ): StorageProvider = LocalStorageProvider(context, directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun gameStorageProviderRegistry(
            context: Context,
            providers: Set<@JvmSuppressWildcards StorageProvider>,
        ) = StorageProviderRegistry(context, providers)

        @Provides
        @PerApp
        @JvmStatic
        fun chimeroidLibrary(
            db: RetrogradeDatabase,
            storageProviderRegistry: Lazy<StorageProviderRegistry>,
            gameMetadataProvider: Lazy<GameMetadataProvider>,
            biosManager: BiosManager,
        ) = ChimeroidLibrary(db, storageProviderRegistry, gameMetadataProvider, biosManager)

        @Provides
        @PerApp
        @JvmStatic
        fun patchCodeDao(db: RetrogradeDatabase) = db.patchCodeDao()

        @Provides
        @PerApp
        @JvmStatic
        fun okHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .build()

        @Provides
        @PerApp
        @JvmStatic
        fun retrofit(): Retrofit =
            Retrofit.Builder()
                .baseUrl("https://example.com")
                .addConverterFactory(
                    object : Converter.Factory() {
                        override fun responseBodyConverter(
                            type: Type,
                            annotations: Array<out Annotation>,
                            retrofit: Retrofit,
                        ): Converter<ResponseBody, *>? {
                            if (type == ZipInputStream::class.java) {
                                return Converter<ResponseBody, ZipInputStream> { responseBody ->
                                    ZipInputStream(responseBody.byteStream())
                                }
                            }
                            if (type == InputStream::class.java) {
                                return Converter<ResponseBody, InputStream> { responseBody ->
                                    responseBody.byteStream()
                                }
                            }
                            return null
                        }
                    },
                )
                .build()

        @Provides
        @PerApp
        @JvmStatic
        fun directoriesManager(context: Context) = DirectoriesManager(context)

        @Provides
        @PerApp
        @JvmStatic
        fun statesManager(directoriesManager: DirectoriesManager) = StatesManager(directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun savesManager(directoriesManager: DirectoriesManager) = SavesManager(directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun statesPreviewManager(directoriesManager: DirectoriesManager) = StatesPreviewManager(directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun coreManager(
            directoriesManager: DirectoriesManager,
            retrofit: Retrofit,
        ): CoreUpdater = CoreUpdaterImpl(directoriesManager, retrofit)

        @Provides
        @PerApp
        @JvmStatic
        fun coreVariablesManager(sharedPreferences: Lazy<SharedPreferences>) = CoreVariablesManager(sharedPreferences)

        @Provides
        @PerApp
        @JvmStatic
        fun gameLoader(
            chimeroidLibrary: ChimeroidLibrary,
            statesManager: StatesManager,
            savesManager: SavesManager,
            coreVariablesManager: CoreVariablesManager,
            retrogradeDatabase: RetrogradeDatabase,
            savesCoherencyEngine: SavesCoherencyEngine,
            directoriesManager: DirectoriesManager,
            biosManager: BiosManager,
            desmumeMigrationHandler: DesmumeMigrationHandler,
        ) = GameLoader(
            chimeroidLibrary,
            statesManager,
            savesManager,
            coreVariablesManager,
            retrogradeDatabase,
            savesCoherencyEngine,
            directoriesManager,
            biosManager,
            desmumeMigrationHandler,
        )

        @Provides
        @PerApp
        @JvmStatic
        fun inputDeviceManager(
            context: Context,
            sharedPreferences: Lazy<SharedPreferences>,
        ) = InputDeviceManager(context, sharedPreferences)

        @Provides
        @PerApp
        @JvmStatic
        fun biosManager(directoriesManager: DirectoriesManager) = BiosManager(directoriesManager)

@Provides
        @PerApp
        @JvmStatic
        fun coresSelection(
            sharedPreferences: Lazy<SharedPreferences>,
            desmumeMigrationHandler: DesmumeMigrationHandler,
        ) = CoresSelection(sharedPreferences, desmumeMigrationHandler)

@Provides
        @PerApp
        @JvmStatic
        fun savesCoherencyEngine(
            savesManager: SavesManager,
            statesManager: StatesManager,
        ) = SavesCoherencyEngine(savesManager, statesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun saveSyncManagerImpl(
            context: Context,
            directoriesManager: DirectoriesManager,
        ) = SaveSyncManagerImpl(context, directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun desmumeMigrationHandler(directoriesManager: DirectoriesManager) =
            DesmumeMigrationHandler(directoriesManager)

        @Provides
        @PerApp
        @JvmStatic
        fun postGameHandler(retrogradeDatabase: RetrogradeDatabase) =
            GameLaunchTaskHandler(ReviewManager(), retrogradeDatabase)

        @Provides
        @PerApp
        @JvmStatic
        fun shortcutsGenerator(
            context: Context,
            retrofit: Retrofit,
        ) = ShortcutsGenerator(context, retrofit)

@Provides
        @PerApp
        @JvmStatic
        fun retroControllerManager(sharedPreferences: Lazy<SharedPreferences>) =
            ControllerConfigsManager(sharedPreferences)

        @Provides
        @PerApp
        @JvmStatic
        fun settingsManager(
            context: Context,
            sharedPreferences: Lazy<SharedPreferences>,
        ) = SettingsManager(context, sharedPreferences)

        @Provides
        @PerApp
        @JvmStatic
        fun sharedPreferences(context: Context) = SharedPreferencesHelper.getSharedPreferences(context)

        @Provides
        @PerApp
        @JvmStatic
        fun gameLauncher(
            coresSelection: CoresSelection,
            gameLaunchTaskHandler: GameLaunchTaskHandler,
        ) = GameLauncher(coresSelection, gameLaunchTaskHandler)

        @Provides
        @PerApp
        @JvmStatic
        fun rumbleManager(
            context: Context,
            settingsManager: SettingsManager,
            inputDeviceManager: InputDeviceManager,
        ) = RumbleManager(context, settingsManager, inputDeviceManager)
    }
}
