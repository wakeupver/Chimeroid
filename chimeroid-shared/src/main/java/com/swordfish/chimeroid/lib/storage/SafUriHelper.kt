package com.swordfish.chimeroid.lib.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import timber.log.Timber
import java.io.File

object SafUriHelper {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PERSISTABLE_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Convert a SAF tree URI to a real filesystem path. This is only a best-effort
     * optimization (fast raw [File] I/O, friendly display) — never the authority on
     * whether the tree is actually usable; use [isTreeUriWritable] for that.
     *
     * Handles:
     *  - Primary: content://…/tree/primary:Chimeroid  → /storage/emulated/0/Chimeroid
     *  - SD card: content://…/tree/ABCD-1234:Games     → /storage/ABCD-1234/Games
     *  - Root:    content://…/tree/primary:            → /storage/emulated/0
     *
     * Returns null if the URI authority is not the standard external storage provider
     * (e.g. Downloads provider, cloud/OEM providers) — callers must reject these, since
     * raw File access to them is never possible regardless of permissions held.
     */
    fun treeUriToPath(treeUri: Uri): String? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null

        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return@runCatching null
            val colonIdx = docId.indexOf(':')
            if (colonIdx < 0) return@runCatching null

            val storageId = docId.substring(0, colonIdx)
            val relativePath = docId.substring(colonIdx + 1)

            val root = if (storageId.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory()
            } else {
                File("/storage/$storageId")
            }

            (if (relativePath.isEmpty()) root else File(root, relativePath)).absolutePath
        }.onFailure {
            Timber.e(it, "SafUriHelper: failed to resolve tree URI $treeUri")
        }.getOrNull()
    }

    /**
     * The Android-standard way to check whether a persisted tree URI still grants
     * usable access: goes through the document provider itself (SAF) rather than
     * assuming from a reconstructed [File] path, so it is correct independent of
     * MANAGE_EXTERNAL_STORAGE / legacy storage state, and correctly reflects grants
     * that were revoked externally (SD card removed, provider uninstalled, etc.).
     */
    fun isTreeUriWritable(context: Context, treeUri: Uri): Boolean =
        runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.let { it.exists() && it.canWrite() } == true
        }.getOrDefault(false)

    /**
     * Takes a persistable read/write grant for [newUri] and releases the grant
     * previously held for the same feature ([previousUri]), if any and if different.
     * Scoped to the caller's own previous value — rather than sweeping every persisted
     * permission the app holds — so unrelated SAF-backed features (e.g. the ROMs
     * library folder) are never clobbered. Also keeps the app under the OS-enforced
     * cap of 128 persisted URI grants as this feature's directory is re-picked over time.
     */
    fun persistTreePermission(context: Context, newUri: Uri, previousUri: Uri?) {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(newUri, PERSISTABLE_FLAGS) }
            .onFailure { Timber.e(it, "SafUriHelper: failed to take permission for $newUri") }

        if (previousUri != null && previousUri != newUri) {
            runCatching { resolver.releasePersistableUriPermission(previousUri, PERSISTABLE_FLAGS) }
        }
    }

    /**
     * Returns true if the app has broad external storage access on this Android version.
     *  - Android ≤10 (API 29): legacy storage (declared in manifest) covers it
     *  - Android 11+ (API 30): needs MANAGE_EXTERNAL_STORAGE
     */
    fun hasExternalStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}
