package com.swordfish.chimeroid.metadata.libretrodb.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LibretroDBManager(private val context: Context) {
    companion object {
        private const val DB_NAME = "libretro-db"
        private const val PREF_KEY_DB_REVISION = "libretro_db_revision"

        private const val LIBRETRO_DB_REVISION = 1L
    }

    private val initMutex = Mutex()

    @Volatile
    private var cachedDatabase: LibretroDatabase? = null

    suspend fun getDatabase(): LibretroDatabase {
        cachedDatabase?.let { return it }
        return initMutex.withLock {
            cachedDatabase ?: withContext(Dispatchers.IO) { buildDatabase() }.also { cachedDatabase = it }
        }
    }

    private fun buildDatabase(): LibretroDatabase {
        recreateIfOutdated()
        return Room.databaseBuilder(context, LibretroDatabase::class.java, DB_NAME)
            .createFromAsset("libretro-db.sqlite")
            .fallbackToDestructiveMigration()

            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA cache_size = -8000")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                }
            })
            .build()
    }

    private fun recreateIfOutdated() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        if (prefs.getLong(PREF_KEY_DB_REVISION, 0L) == LIBRETRO_DB_REVISION) return

        val dbFile = context.getDatabasePath(DB_NAME)
        sequenceOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm"), File("${dbFile.path}-journal"))
            .filter(File::exists)
            .forEach(File::delete)

        prefs.edit().putLong(PREF_KEY_DB_REVISION, LIBRETRO_DB_REVISION).apply()
    }
}
