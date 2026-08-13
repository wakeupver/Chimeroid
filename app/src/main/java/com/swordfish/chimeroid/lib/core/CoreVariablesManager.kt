package com.swordfish.chimeroid.lib.core

import android.content.SharedPreferences
import com.swordfish.chimeroid.lib.library.SystemCoreConfig
import com.swordfish.chimeroid.lib.library.SystemID
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoreVariablesManager(private val sharedPreferences: Lazy<SharedPreferences>) {

    suspend fun getOptionsForCore(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
    ): List<CoreVariable> = withContext(Dispatchers.IO) {
        val defaults  = systemCoreConfig.defaultSettings.associate { it.key to it.value }
        val overrides = retrieveCustomCoreVariables(systemID).associate { it.key to it.value }
        (defaults + overrides).map { (k, v) -> CoreVariable(k, v) }
    }

    private fun retrieveCustomCoreVariables(systemID: SystemID): List<CoreVariable> {
        val prefix = computeSharedPreferencesPrefix(systemID.dbname)
        return sharedPreferences.get().all
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { (key, value) ->
                val strValue = when (value) {
                    is Boolean -> if (value) "enabled" else "disabled"
                    is String  -> value
                    is Int, is Long, is Float -> value.toString()
                    else -> return@mapNotNull null
                }
                CoreVariable(computeOriginalKey(key, systemID.dbname), strValue)
            }
    }

    companion object {
        private const val RETRO_OPTION_PREFIX = "cv"

        fun computeSharedPreferenceKey(retroVariableName: String, systemID: String): String =
            "${computeSharedPreferencesPrefix(systemID)}$retroVariableName"

        fun computeOriginalKey(sharedPreferencesKey: String, systemID: String): String =
            sharedPreferencesKey.removePrefix(computeSharedPreferencesPrefix(systemID))

        fun computeSharedPreferencesPrefix(systemID: String): String =
            "${RETRO_OPTION_PREFIX}_${systemID}_"
    }
}
