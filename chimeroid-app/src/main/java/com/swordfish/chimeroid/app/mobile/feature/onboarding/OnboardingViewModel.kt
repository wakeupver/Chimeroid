package com.swordfish.chimeroid.app.mobile.feature.onboarding

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.chimeroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val performanceProfile: Int = PROFILE_SAFE,
    val romsDirectoryUri: String? = null,
    val romsDirectoryValid: Boolean = false,
    val allFilesAccessGranted: Boolean = false,
    val canContinue: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = TOTAL_PAGES,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(allFilesAccessGranted = hasAllFilesAccess()),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Restore already-selected ROMs folder from preferences (e.g. user re-opens app mid-onboarding)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(app)
            val prefKey = app.getString(com.swordfish.chimeroid.lib.R.string.pref_key_extenral_folder)
            val savedUri = prefs.getString(prefKey, null)
            if (savedUri != null) {
                updateState(romsDirectoryUri = savedUri, romsDirectoryValid = true)
            }
        }
    }

    // -------------------------------------------------------------------------
    // ROMs directory
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
            val prefKey = app.getString(com.swordfish.chimeroid.lib.R.string.pref_key_extenral_folder)
            prefs.edit().putString(prefKey, uri.toString()).apply()

            LibraryIndexScheduler.scheduleLibrarySync(app)

            updateState(romsDirectoryUri = uri.toString(), romsDirectoryValid = true)
        }
    }

    // -------------------------------------------------------------------------
    // All-files access
    // -------------------------------------------------------------------------

    fun refreshAllFilesAccess() {
        updateState(allFilesAccessGranted = hasAllFilesAccess())
    }

    // -------------------------------------------------------------------------
    // Performance profile
    // -------------------------------------------------------------------------

    fun setPerformanceProfile(profile: Int) {
        updateState(performanceProfile = profile)
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
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(app)

            // Apply performance profile → direct game load setting
            val directLoadKey = app.getString(R.string.pref_key_allow_direct_game_load)
            prefs.edit()
                .putBoolean(directLoadKey, _uiState.value.performanceProfile == PROFILE_FAST)
                .apply()

            // Mark onboarding completed
            val onboardingKey = app.getString(R.string.pref_key_onboarding_completed)
            prefs.edit().putBoolean(onboardingKey, true).apply()

            withContext(Dispatchers.Main) { onFinished() }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun updateState(
        performanceProfile: Int = _uiState.value.performanceProfile,
        romsDirectoryUri: String? = _uiState.value.romsDirectoryUri,
        romsDirectoryValid: Boolean = _uiState.value.romsDirectoryValid,
        allFilesAccessGranted: Boolean = _uiState.value.allFilesAccessGranted,
        currentPage: Int = _uiState.value.currentPage,
    ) {
        _uiState.value = OnboardingUiState(
            performanceProfile = performanceProfile,
            romsDirectoryUri = romsDirectoryUri,
            romsDirectoryValid = romsDirectoryValid,
            allFilesAccessGranted = allFilesAccessGranted,
            canContinue = romsDirectoryValid && allFilesAccessGranted,
            currentPage = currentPage,
            totalPages = TOTAL_PAGES,
        )
    }

    companion object {
        const val TOTAL_PAGES = 5
        const val PROFILE_SAFE = 0
        const val PROFILE_FAST = 1

        fun hasAllFilesAccess(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // pre-Android 11: legacy permission is enough
            }
    }
}
