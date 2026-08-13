package com.swordfish.chimeroid.lib.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.File

class DirectoriesManager(private val appContext: Context) {

    fun getCoresDirectory(): File =
        File(appContext.filesDir, "cores").apply { mkdirs() }

    @Volatile private var cachedBase: File? = null

    private fun resolveBase(): File {
        cachedBase?.let { return it }
        return computeBase().also { cachedBase = it }
    }

    private fun computeBase(): File {
        val uri = getBaseDirUri() ?: return defaultBase()

        val custom = resolveUsableCustomDir(uri)
        if (custom == null) {
            Timber.w("DirectoriesManager: custom base dir '$uri' is not usable, falling back to default")
        }
        return custom ?: defaultBase()
    }

    private fun defaultBase(): File = appContext.getExternalFilesDir(null) ?: appContext.filesDir

    private fun resolveUsableCustomDir(uri: Uri): File? {
        val custom = SafUriHelper.treeUriToPath(uri)?.let(::File) ?: return null
        if (!SafUriHelper.isTreeUriWritable(appContext, uri)) return null
        return custom.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
    }

    @Deprecated("Use the external states directory")
    fun getInternalStatesDirectory(): File =
        File(appContext.filesDir, "states").apply { mkdirs() }

    fun getSystemDirectory(): File =
        File(resolveBase(), "system").apply { mkdirs() }

    fun getStatesDirectory(): File =
        File(resolveBase(), "states").apply { mkdirs() }

    fun getStatesPreviewDirectory(): File =
        File(resolveBase(), "state-previews").apply { mkdirs() }

    fun getSavesDirectory(): File =
        File(resolveBase(), "saves").apply { mkdirs() }

    fun getInternalRomsDirectory(): File =
        File(resolveBase(), "roms").apply { mkdirs() }

    fun getBaseDirUri(): Uri? {
        val raw = PreferenceManager.getDefaultSharedPreferences(appContext)
            .getString(PREF_KEY_CUSTOM_BASE_DIR, null)
        if (raw.isNullOrBlank()) return null
        return raw.toUri().takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
    }

    fun isBaseDirConfigured(): Boolean = getBaseDirUri()?.let(::resolveUsableCustomDir) != null

    fun isBaseDirSet(): Boolean = getBaseDirUri() != null

    fun getBaseDirDisplay(): String {
        val uri = getBaseDirUri() ?: return defaultBase().absolutePath
        val usable = resolveUsableCustomDir(uri)
        val displayPath = usable?.absolutePath ?: SafUriHelper.treeUriToPath(uri) ?: uri.toString()
        return if (usable != null) displayPath else "$displayPath ⚠ (not accessible)"
    }

    fun saveBaseDir(uri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putString(PREF_KEY_CUSTOM_BASE_DIR, uri?.toString())
            .apply()

        cachedBase = null
    }

    companion object {
        const val PREF_KEY_CUSTOM_BASE_DIR = "custom_base_dir"
    }
}
