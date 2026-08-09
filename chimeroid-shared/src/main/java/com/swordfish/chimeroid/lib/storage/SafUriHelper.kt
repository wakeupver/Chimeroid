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

    fun isTreeUriWritable(context: Context, treeUri: Uri): Boolean =
        runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.let { it.exists() && it.canWrite() } == true
        }.getOrDefault(false)

    fun persistTreePermission(context: Context, newUri: Uri, previousUri: Uri?) {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(newUri, PERSISTABLE_FLAGS) }
            .onFailure { Timber.e(it, "SafUriHelper: failed to take permission for $newUri") }

        if (previousUri != null && previousUri != newUri) {
            runCatching { resolver.releasePersistableUriPermission(previousUri, PERSISTABLE_FLAGS) }
        }
    }

    fun hasExternalStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
}
