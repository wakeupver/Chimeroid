package com.swordfish.chimeroid.app.mobile.feature.onboarding

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.chimeroid.lib.storage.DirectoriesManager
import com.swordfish.chimeroid.lib.R as LibR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Top-level constants — must be outside the class so OnboardingUiState defaults can reference them
const val ONBOARDING_TOTAL_PAGES = 4

data class OnboardingUiState(
    val romsDirectoryUri: String? = null,
    val romsDirectoryValid: Boolean = false,
    val baseDirectoryPath: String? = null,
    val baseDirectoryValid: Boolean = false,
    val allFilesAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val canContinue: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = ONBOARDING_TOTAL_PAGES,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            allFilesAccessGranted = hasAllFilesAccess(),
            notificationGranted = hasNotificationPermission(application),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            // Restore ROMs folder
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(app)
            val romsKey = app.getString(LibR.string.pref_key_extenral_folder)
            val savedRomsUri = prefs.getString(romsKey, null)

            // Restore base directory
            val dm = DirectoriesManager(app)
            val baseDirConfigured = dm.isBaseDirConfigured()

            updateState {
                copy(
                    romsDirectoryUri = savedRomsUri,
                    romsDirectoryValid = savedRomsUri != null,
                    baseDirectoryPath = if (baseDirConfigured) dm.getBaseDirDisplay() else null,
                    baseDirectoryValid = baseDirConfigured,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // ROMs directory (SAF URI)
    // -------------------------------------------------------------------------

    fun setRomsDirectory(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                app.contentResolver.persistedUriPermissions
                    .filter { it.isReadPermission && it.uri != uri }
                    .forEach { app.contentResolver.releasePersistableUriPermission(it.uri, flags) }
                app.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(app)
            val romsKey = app.getString(LibR.string.pref_key_extenral_folder)
            prefs.edit().putString(romsKey, uri.toString()).apply()
            LibraryIndexScheduler.scheduleLibrarySync(app)
            updateState { copy(romsDirectoryUri = uri.toString(), romsDirectoryValid = true) }
        }
    }

    // -------------------------------------------------------------------------
    // Base directory (resolved real path via StorageBaseDirPicker)
    // -------------------------------------------------------------------------

    fun refreshBaseDirectory() {
        val app = getApplication<Application>()
        val dm = DirectoriesManager(app)
        val baseDirConfigured = dm.isBaseDirConfigured()
        updateState {
            copy(
                baseDirectoryPath = if (baseDirConfigured) dm.getBaseDirDisplay() else null,
                baseDirectoryValid = baseDirConfigured,
            )
        }
    }

    // -------------------------------------------------------------------------
    // All-files access
    // -------------------------------------------------------------------------

    fun refreshAllFilesAccess() {
        updateState { copy(allFilesAccessGranted = hasAllFilesAccess()) }
    }

    // -------------------------------------------------------------------------
    // Notification permission
    // -------------------------------------------------------------------------

    fun refreshNotificationPermission() {
        updateState { copy(notificationGranted = hasNotificationPermission(getApplication())) }
    }

    // -------------------------------------------------------------------------
    // Pager navigation
    // -------------------------------------------------------------------------

    fun setCurrentPage(page: Int) {
        updateState { copy(currentPage = page.coerceIn(0, ONBOARDING_TOTAL_PAGES - 1)) }
    }

    // -------------------------------------------------------------------------
    // Complete
    // -------------------------------------------------------------------------

    fun completeOnboarding(onFinished: () -> Unit) {
        if (!_uiState.value.canContinue) return
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val onboardingKey = app.getString(R.string.pref_key_onboarding_completed)
            SharedPreferencesHelper.getLegacySharedPreferences(app)
                .edit().putBoolean(onboardingKey, true).apply()
            withContext(Dispatchers.Main) { onFinished() }
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    // Callers arrive from both the Main thread (DisposableEffect ON_RESUME refreshes) and
    // IO-dispatched coroutines (init, setRomsDirectory). MutableStateFlow.update applies
    // `transform` via an atomic compare-and-set retry loop, so concurrent callers can never
    // clobber each other's changes the way a plain read-then-`_uiState.value = ...` would.
    private inline fun updateState(transform: OnboardingUiState.() -> OnboardingUiState) {
        _uiState.update { current ->
            val next = current.transform()
            next.copy(canContinue = next.romsDirectoryValid && next.allFilesAccessGranted)
        }
    }

    companion object {
        fun hasAllFilesAccess(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

        fun hasNotificationPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    )
            } else {
                true // Granted implicitly on API < 33
            }
    }
}
