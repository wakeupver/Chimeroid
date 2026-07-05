package com.swordfish.chimeroid.app.shared.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.chimeroid.lib.android.RetrogradeActivity
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import com.swordfish.chimeroid.lib.storage.SafUriHelper
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Transparent Activity for selecting the app-wide base storage directory.
 *
 * Cores always remain in internal storage (/data/data/<pkg>/files/cores).
 *
 * Flow (SAF-first, per Android storage standards):
 *  1. Launch the SAF tree picker directly — this step needs no dangerous permission.
 *  2. Persist a read/write grant for the chosen tree, scoped to this feature only.
 *  3. Validate the grant the Android-standard way (via DocumentFile) and reject
 *     providers that can't be mapped to a real path.
 *  4. Save the tree URI itself (not a derived path) as the base dir.
 *  5. Optionally, non-blockingly, offer MANAGE_EXTERNAL_STORAGE so the raw
 *     File-based I/O every consumer uses can reach the folder on API 30+.
 */
class StorageBaseDirPicker : RetrogradeActivity() {

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    private var mandatory = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mandatory = intent.getBooleanExtra(EXTRA_MANDATORY, false)
        if (savedInstanceState == null) launchPicker()
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_DIR)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "StorageBaseDirPicker: SAF not available")
            finishWithCancel()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_CODE_PICK_DIR) {
            finish()
            return
        }
        if (resultCode != Activity.RESULT_OK) {
            handleCancel()
            return
        }

        val uri = data?.data
        if (uri == null) {
            handleCancel()
            return
        }
        commitUri(uri)
    }

    // -------------------------------------------------------------------------
    // Commit
    // -------------------------------------------------------------------------

    private fun commitUri(uri: Uri) {
        val realPath = SafUriHelper.treeUriToPath(uri)
        if (realPath == null) {
            Timber.w("StorageBaseDirPicker: unsupported SAF authority for '$uri'")
            toast(R.string.storage_picker_unsupported_location)
            launchPicker()
            return
        }

        SafUriHelper.persistTreePermission(this, uri, directoriesManager.getBaseDirUri())

        if (!SafUriHelper.isTreeUriWritable(this, uri)) {
            Timber.w("StorageBaseDirPicker: '$uri' is not writable")
            toast(R.string.storage_picker_not_writable)
            launchPicker()
            return
        }

        Timber.i("StorageBaseDirPicker: saving base dir '$uri'")
        directoriesManager.saveBaseDir(uri)
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        offerElevatedAccessIfNeeded(realPath)

        setResult(Activity.RESULT_OK)
        finish()
    }

    /**
     * The SAF grant above already suffices for DocumentFile-based access. Raw
     * java.io.File I/O — used by every downstream consumer (saves, states,
     * BIOS…) for performance — additionally needs MANAGE_EXTERNAL_STORAGE on
     * Android 11+. Offered as a non-blocking follow-up rather than a gate, since
     * the picker itself never needs a permission SAF doesn't require.
     */
    private fun offerElevatedAccessIfNeeded(realPath: String) {
        if (SafUriHelper.hasExternalStorageAccess() || File(realPath).canWrite()) return

        toast(R.string.storage_picker_need_manage_permission)
        val opened = runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.isSuccess

        if (!opened) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                .onFailure { Timber.w(it, "StorageBaseDirPicker: cannot open MANAGE_EXTERNAL_STORAGE settings") }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun handleCancel() {
        if (mandatory) launchPicker() else finishWithCancel()
    }

    private fun finishWithCancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun toast(@StringRes resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val REQUEST_CODE_PICK_DIR = 2001
        const val EXTRA_MANDATORY = "extra_mandatory"

        fun launch(context: Context) {
            context.startActivity(Intent(context, StorageBaseDirPicker::class.java))
        }

        fun launchForResult(activity: Activity, mandatory: Boolean = false) {
            val intent = Intent(activity, StorageBaseDirPicker::class.java).apply {
                putExtra(EXTRA_MANDATORY, mandatory)
            }
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_DIR)
        }
    }
}
