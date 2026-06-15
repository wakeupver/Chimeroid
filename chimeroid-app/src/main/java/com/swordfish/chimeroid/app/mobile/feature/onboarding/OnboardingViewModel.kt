package com.swordfish.chimeroid.app.mobile.feature.onboarding

import android.app.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val microphoneGranted: Boolean = false,
    val canContinue: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = ONBOARDING_TOTAL_PAGES,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            allFilesAccessGranted = hasAllFilesAccess(),
            notificationGranted = hasNotificationPermission(application),
            microphoneGranted = hasMicrophonePermission(application),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            // Restore ROMs folder
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(app)
            val romsKey = app.getString(com.swordfish.chimeroid.lib.R.string.pref_key_extenral_folder)
            val savedRomsUri = prefs.getString(romsKey, null)

            // Restore base directory
            val dm = DirectoriesManager(app)
            val baseDirConfigured = dm.isBaseDirConfigured()

            updateState(
                romsDirectoryUri = savedRomsUri,
                romsDirectoryValid = savedRomsUri != null,
                baseDirectoryPath = if (baseDirConfigured) dm.getBaseDirDisplay() else null,
                baseDirectoryValid = baseDirConfigured,
            )
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
            val romsKey = app.getString(com.swordfish.chimeroid.lib.R.string.pref_key_extenral_folder)
            prefs.edit().putString(romsKey, uri.toString()).apply()
            LibraryIndexScheduler.scheduleLibrarySync(app)
            updateState(romsDirectoryUri = uri.toString(), romsDirectoryValid = true)
        }
    }

    // -------------------------------------------------------------------------
    // Base directory (resolved real path via StorageBaseDirPicker)
    // -------------------------------------------------------------------------

    fun refreshBaseDirectory() {
        val app = getApplication<Application>()
        val dm = DirectoriesManager(app)
        updateState(
            baseDirectoryPath = if (dm.isBaseDirConfigured()) dm.getBaseDirDisplay() else null,
            baseDirectoryValid = dm.isBaseDirConfigured(),
        )
    }

    // -------------------------------------------------------------------------
    // All-files access
    // -------------------------------------------------------------------------

    fun refreshAllFilesAccess() {
        updateState(allFilesAccessGranted = hasAllFilesAccess())
    }

    // -------------------------------------------------------------------------
    // Notification permission
    // -------------------------------------------------------------------------

    fun refreshNotificationPermission() {
        updateState(notificationGranted = hasNotificationPermission(getApplication()))
    }

    // -------------------------------------------------------------------------
    // Microphone permission
    // -------------------------------------------------------------------------

    fun refreshMicrophonePermission() {
        updateState(microphoneGranted = hasMicrophonePermission(getApplication()))
    }

    // -------------------------------------------------------------------------
    // Pager navigation
    // -------------------------------------------------------------------------

    fun setCurrentPage(page: Int) {
        updateState(currentPage = page.coerceIn(0, _uiState.value.totalPages - 1))
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

    private fun updateState(
        romsDirectoryUri: String? = _uiState.value.romsDirectoryUri,
        romsDirectoryValid: Boolean = _uiState.value.romsDirectoryValid,
        baseDirectoryPath: String? = _uiState.value.baseDirectoryPath,
        baseDirectoryValid: Boolean = _uiState.value.baseDirectoryValid,
        allFilesAccessGranted: Boolean = _uiState.value.allFilesAccessGranted,
        notificationGranted: Boolean = _uiState.value.notificationGranted,
        microphoneGranted: Boolean = _uiState.value.microphoneGranted,
        currentPage: Int = _uiState.value.currentPage,
    ) {
        _uiState.value = OnboardingUiState(
            romsDirectoryUri = romsDirectoryUri,
            romsDirectoryValid = romsDirectoryValid,
            baseDirectoryPath = baseDirectoryPath,
            baseDirectoryValid = baseDirectoryValid,
            allFilesAccessGranted = allFilesAccessGranted,
            notificationGranted = notificationGranted,
            microphoneGranted = microphoneGranted,
            canContinue = romsDirectoryValid && allFilesAccessGranted,
            currentPage = currentPage,
            totalPages = ONBOARDING_TOTAL_PAGES,
        )
    }

    companion object {
        fun hasAllFilesAccess(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

        fun hasNotificationPermission(context: android.content.Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS,
                    )
            } else {
                true // Granted implicitly on API < 33
            }

        fun hasMicrophonePermission(context: android.content.Context): Boolean =
            PackageManager.PERMISSION_GRANTED ==
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.RECORD_AUDIO,
                )
    }
}
