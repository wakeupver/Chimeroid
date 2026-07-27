package com.swordfish.chimeroid.app.mobile.feature.settings.inputdevices

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.input.GamePadBindingActivity
import com.swordfish.chimeroid.app.mobile.feature.input.GamePadShortcutBindingActivity
import com.swordfish.chimeroid.app.shared.input.InputBindingUpdater
import com.swordfish.chimeroid.app.shared.input.InputKey
import com.swordfish.chimeroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.chimeroid.app.shared.input.chimeroiddevice.getChimeroidInputDevice
import com.swordfish.chimeroid.app.shared.settings.GameShortcut
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidCardSettingsGroup
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsMenuLink
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsPage
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsSwitch
import com.swordfish.chimeroid.app.utils.android.settings.booleanPreferenceState

@Composable
fun InputDevicesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: InputDevicesSettingsViewModel,
) {
    val state =
        viewModel.uiState
            .collectAsState(InputDevicesSettingsViewModel.State())
            .value

    ChimeroidSettingsPage(modifier = modifier.fillMaxSize()) {
        EnabledDeviceCategory(state)
        state.bindings.forEach { (device, bindings) ->
            DeviceBindingCategory(device, bindings)
        }
        GeneralOptionsCategory(viewModel)
    }
}

@Composable
private fun DeviceBindingCategory(
    device: InputDevice,
    bindings: InputDevicesSettingsViewModel.BindingsView,
) {
    val context = LocalContext.current
    val customizableKeys = device.getChimeroidInputDevice().getCustomizableKeys()

    ChimeroidCardSettingsGroup(title = { Text(text = device.name) }) {
        customizableKeys.forEach { retroKey ->
            val inputKey = bindings.keys[retroKey] ?: InputKey(KeyEvent.KEYCODE_UNKNOWN)

            ChimeroidSettingsMenuLink(
                title = { Text(text = retroKey.displayName(LocalContext.current)) },
                subtitle = { Text(text = inputKey.displayName()) },
                onClick = {
                    val intent =
                        Intent(context, GamePadBindingActivity::class.java).apply {
                            putExtra(InputBindingUpdater.REQUEST_DEVICE, device)
                            putExtra(InputBindingUpdater.REQUEST_RETRO_KEY, retroKey.keyCode)
                        }
                    context.startActivity(intent)
                },
            )
        }

        bindings.shortcuts.forEach {
            DeviceShortcutBinding(context, device, it)
        }
    }
}

@Composable
private fun DeviceShortcutBinding(
    context: Context,
    device: InputDevice,
    shortcut: GameShortcut,
) {
    ChimeroidSettingsMenuLink(
        title = { Text(text = shortcut.type.displayName()) },
        subtitle = { Text(text = shortcut.name) },
        onClick = {
            val intent =
                Intent(context, GamePadShortcutBindingActivity::class.java).apply {
                    putExtra(ShortcutBindingUpdater.REQUEST_DEVICE, device)
                    putExtra(ShortcutBindingUpdater.REQUEST_SHORTCUT_TYPE, shortcut.type.name)
                }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun EnabledDeviceCategory(state: InputDevicesSettingsViewModel.State) {
    ChimeroidCardSettingsGroup(title = { Text(text = stringResource(R.string.settings_gamepad_category_enabled)) }) {
        state.devices.forEach { device ->
            ChimeroidSettingsSwitch(
                state = booleanPreferenceState(key = device.key, default = device.enabledByDefault),
                title = { Text(text = device.name) },
            )
        }
    }
}

@Composable
private fun GeneralOptionsCategory(viewModel: InputDevicesSettingsViewModel) {
    ChimeroidCardSettingsGroup(title = { Text(text = stringResource(R.string.settings_gamepad_category_general)) }) {
        ChimeroidSettingsMenuLink(
            title = { Text(text = stringResource(R.string.settings_gamepad_title_reset_bindings)) },
            onClick = { viewModel.resetAllBindings() },
        )
    }
}
