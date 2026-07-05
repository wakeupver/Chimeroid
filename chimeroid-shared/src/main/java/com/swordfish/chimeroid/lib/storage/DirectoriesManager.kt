package com.swordfish.chimeroid.lib.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.File

class DirectoriesManager(private val appContext: Context) {

    // -------------------------------------------------------------------------
    // Cores — ALWAYS in internal app-private storage, never customizable.
    // Path: /data/data/<pkg>/files/cores
    // -------------------------------------------------------------------------

    fun getCoresDirectory(): File =
        File(appContext.filesDir, "cores").apply { mkdirs() }

    // -------------------------------------------------------------------------
    // All other dirs — use the custom base if configured AND usable,
    // otherwise fall back silently to getExternalFilesDir(null).
    // -------------------------------------------------------------------------

    /**
     * One-shot cache for the resolved base directory.
     *
     * [resolveBase] validates the persisted SAF grant and may hit the document
     * provider / filesystem. Since multiple directory accessors ([getSavesDirectory],
     * [getStatesDirectory], [getSystemDirectory] …) are called in rapid succession
     * during game loading, caching eliminates N redundant preference reads, Binder
     * calls, and filesystem calls per load.
     *
     * The cache is invalidated by [saveBaseDir] — the only code path that mutates
     * the preference.
     */
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

    /** Guaranteed non-null: internal app-specific storage is always available. */
    private fun defaultBase(): File = appContext.getExternalFilesDir(null) ?: appContext.filesDir

    /**
     * A tree URI is only usable as a base dir if BOTH hold:
     *  - the SAF grant is still valid, checked the Android-standard way (via
     *    [SafUriHelper.isTreeUriWritable]), independent of MANAGE_EXTERNAL_STORAGE
     *  - the reconstructed real path is actually writable via [java.io.File], since
     *    every consumer of this class (saves, states, BIOS…) does raw File I/O for
     *    performance, which additionally needs MANAGE_EXTERNAL_STORAGE on API 30+
     *    or legacy storage on API ≤29.
     *
     * The cheap local check (path reconstruction) runs before the Binder call to the
     * document provider, so unsupported providers short-circuit without an IPC round trip.
     */
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * The persisted custom base dir as a standard Android [Uri] granted by the SAF
     * tree picker — the single source of truth for this feature. Returns null for
     * anything that isn't a valid `content://` tree URI, which also gracefully
     * discards raw filesystem paths stored by older app versions (those never
     * carried a real SAF grant, so they can't be recovered — the user simply
     * re-picks via the storage base dir picker).
     */
    fun getBaseDirUri(): Uri? {
        val raw = PreferenceManager.getDefaultSharedPreferences(appContext)
            .getString(PREF_KEY_CUSTOM_BASE_DIR, null)
        if (raw.isNullOrBlank()) return null
        return raw.toUri().takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
    }

    /** True if the user has explicitly configured a base dir AND it's currently usable. */
    fun isBaseDirConfigured(): Boolean = getBaseDirUri()?.let(::resolveUsableCustomDir) != null

    /** True if a custom URI is stored (regardless of whether it's accessible right now). */
    fun isBaseDirSet(): Boolean = getBaseDirUri() != null

    /** Human-readable display of the current active base directory. */
    fun getBaseDirDisplay(): String {
        val uri = getBaseDirUri() ?: return defaultBase().absolutePath
        val usable = resolveUsableCustomDir(uri)
        val displayPath = usable?.absolutePath ?: SafUriHelper.treeUriToPath(uri) ?: uri.toString()
        return if (usable != null) displayPath else "$displayPath ⚠ (not accessible)"
    }

    /** Persist a new base dir URI. Pass null to revert to default. */
    fun saveBaseDir(uri: Uri?) {
        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .putString(PREF_KEY_CUSTOM_BASE_DIR, uri?.toString())
            .apply()
        // Invalidate cached value so the next directory access recomputes from preferences.
        cachedBase = null
    }

    companion object {
        const val PREF_KEY_CUSTOM_BASE_DIR = "custom_base_dir"
    }
}
