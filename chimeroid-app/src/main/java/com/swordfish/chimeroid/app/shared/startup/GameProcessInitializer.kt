package com.swordfish.chimeroid.app.shared.startup

import android.content.Context
import androidx.startup.Initializer
import com.swordfish.chimeroid.app.shared.game.GameProcessLock
import timber.log.Timber

class GameProcessInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Timber.i("Requested initialization of game process tasks")
        GameProcessLock.acquire(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(DebugInitializer::class.java)
    }
}
