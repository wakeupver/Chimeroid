package com.swordfish.chimeroid.lib.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.frybits.harmony.getHarmonySharedPreferences
import com.swordfish.chimeroid.common.preferences.SharedPreferencesDataStore
import com.swordfish.chimeroid.lib.R

object SharedPreferencesHelper {
    fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getHarmonySharedPreferences(context.getString(R.string.pref_file_harmony_options))
    }

    fun getSharedPreferencesDataStore(context: Context): SharedPreferencesDataStore {
        return SharedPreferencesDataStore(getSharedPreferences(context))
    }

    /**
     * Default shared preferences does not work with multi-process. It's intentionally used only
     * for the stored directory preference, which is only ever read in the main process. Do not
     * use this for anything that needs multi-process consistency - use [getSharedPreferences].
     *
     * Not marked [Deprecated]: its underlying file ("<package>_preferences") differs from
     * Harmony's ("harmony_options" - see R.string.pref_file_harmony_options), so switching
     * existing call sites over would silently orphan users' already-stored preference values.
     */
    fun getLegacySharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}
