package com.swordfish.chimeroid.lib.core

import android.content.SharedPreferences
import com.swordfish.chimeroid.lib.library.SystemCoreConfig
import com.swordfish.chimeroid.lib.library.SystemID
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoreVariablesManager(private val sharedPreferences: Lazy<SharedPreferences>) {

    /**
     * Build the full list of [CoreVariable]s to push to a core.
     *
     * Priority (highest wins): user-saved SharedPreferences > [SystemCoreConfig.defaultSettings].
     */
    suspend fun getOptionsForCore(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
    ): List<CoreVariable> = withContext(Dispatchers.IO) {
        val defaults  = systemCoreConfig.defaultSettings.associate { it.key to it.value }
        val overrides = retrieveCustomCoreVariables(systemID).associate { it.key to it.value }
        (defaults + overrides).map { (k, v) -> CoreVariable(k, v) }
    }

    /**
     * Read every SharedPreference stored under this system's prefix and convert
     * them back to [CoreVariable]s.  Covers both manually-declared settings and
     * auto-detected ones saved via the in-game menu.
     */
    private fun retrieveCustomCoreVariables(systemID: SystemID): List<CoreVariable> {
        val prefix = computeSharedPreferencesPrefix(systemID.dbname)
        return sharedPreferences.get().all
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { (key, value) ->
                val strValue = when (value) {
                    is Boolean -> if (value) "enabled" else "disabled"
                    is String  -> value
                    is Int, is Long, is Float -> value.toString()
                    else -> return@mapNotNull null   // skip unknown / null types
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
