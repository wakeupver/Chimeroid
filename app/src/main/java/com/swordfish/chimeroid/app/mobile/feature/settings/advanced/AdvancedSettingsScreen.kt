package com.swordfish.chimeroid.app.mobile.feature.settings.advanced

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.main.MainRoute
import com.swordfish.chimeroid.app.shared.settings.StorageBaseDirPicker
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidCardSettingsGroup
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsList
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsMenuLink
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsPage
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsSlider
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsSwitch
import com.swordfish.chimeroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.chimeroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.chimeroid.app.utils.android.settings.intPreferenceState
import com.swordfish.chimeroid.lib.storage.DirectoriesManager

@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel,
    navController: NavHostController,
    directoriesManager: DirectoriesManager,
) {
    val uiState = viewModel.uiState.collectAsState().value

    ChimeroidSettingsPage(modifier = modifier.fillMaxSize()) {
        if (uiState?.cache == null) return@ChimeroidSettingsPage

        InputSettings()
        GeneralSettings(uiState, viewModel, navController)
        StorageSettings(directoriesManager)
    }
}

@Composable
private fun InputSettings() {
    ChimeroidCardSettingsGroup(
        title = { Text(text = stringResource(R.string.settings_category_input)) },
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        ChimeroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_enable_rumble)) },
        )
        ChimeroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_enable_device_rumble)) },
        )
        ChimeroidSettingsSlider(
            state = intPreferenceState(
                key = stringResource(R.string.pref_key_tilt_sensitivity_index),
                default = 6,
            ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun GeneralSettings(
    uiState: AdvancedSettingsViewModel.State,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    ChimeroidCardSettingsGroup(
        title = { Text(text = stringResource(R.string.settings_category_general)) },
    ) {
        ChimeroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_low_latency_audio)) },
        )
        ChimeroidSettingsList(
            title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
            items = uiState.cache.displayNames,
            state = indexPreferenceState(
                R.string.pref_key_max_cache_size,
                uiState.cache.default,
                uiState.cache.values,
            ),
        )
        ChimeroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_direct_game_load)) },
        )
        ChimeroidSettingsMenuLink(
            title = { Text(text = stringResource(R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel, navController)
    }
}

@Composable
private fun StorageSettings(directoriesManager: DirectoriesManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val displayPath = remember { mutableStateOf(directoriesManager.getBaseDirDisplay()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                displayPath.value = directoriesManager.getBaseDirDisplay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ChimeroidCardSettingsGroup(
        title = { Text(text = stringResource(R.string.settings_category_storage_paths)) },
    ) {
        ChimeroidSettingsMenuLink(
            title = { Text(text = stringResource(R.string.settings_title_storage_location)) },
            subtitle = { Text(text = displayPath.value) },
            onClick = { StorageBaseDirPicker.launch(context) },
        )
    }
}

@Composable
private fun FactoryResetDialog(
    state: MutableState<Boolean>,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val onDismiss = { state.value = false }
    AlertDialog(
        title = { Text(stringResource(R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                viewModel.resetAllSettings()
                navController.popBackStack(MainRoute.SETTINGS.route, false)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
