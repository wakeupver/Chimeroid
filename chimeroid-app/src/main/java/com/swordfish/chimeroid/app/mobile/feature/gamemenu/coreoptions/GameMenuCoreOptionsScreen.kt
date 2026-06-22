package com.swordfish.chimeroid.app.mobile.feature.gamemenu.coreoptions

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.chimeroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.chimeroid.app.shared.coreoptions.ChimeroidCoreOption
import com.swordfish.chimeroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsGroup
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsList
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsSwitch
import com.swordfish.chimeroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.chimeroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.chimeroid.lib.core.CoreVariablesManager

@Composable
fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
) {
    val context = LocalContext.current
    val connectedGamePads by viewModel.connectedGamePads.collectAsState(0)
    val allOptions = gameMenuRequest.coreOptions + gameMenuRequest.advancedCoreOptions

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        CoreOptions(gameMenuRequest.game.systemId, allOptions, context)
        AutoDetectedCoreOptions(gameMenuRequest.game.systemId, gameMenuRequest.autoDetectedCoreOptions, context)
        ControllersOptions(gameMenuRequest, maxOf(1, connectedGamePads), context)
    }
}

// ── Shared item composable ────────────────────────────────────────────────────

/**
 * Renders a single [ChimeroidCoreOption] as either a switch (boolean) or a
 * drop-down list, based on its allowed values.
 * Extracted to eliminate the identical block that used to appear in both
 * [CoreOptions] and [AutoDetectedCoreOptions].
 */
@Composable
private fun CoreOptionItem(
    systemID: String,
    coreOption: ChimeroidCoreOption,
    context: Context,
) {
    val prefKey = CoreVariablesManager.computeSharedPreferenceKey(coreOption.getKey(), systemID)
    val entryValues = coreOption.getEntriesValues()
    if (entryValues.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
        ChimeroidSettingsSwitch(
            state = booleanPreferenceState(prefKey, coreOption.getCurrentValue() == "enabled"),
            title = { Text(text = coreOption.getDisplayName(context)) },
        )
    } else {
        ChimeroidSettingsList(
            title = { Text(text = coreOption.getDisplayName(context)) },
            items = coreOption.getEntries(context),
            state = indexPreferenceState(
                prefKey,
                entryValues.firstOrNull() ?: coreOption.getCurrentValue(),
                entryValues,
            ),
        )
    }
}

// ── Section composables ───────────────────────────────────────────────────────

@Composable
private fun CoreOptions(
    systemID: String,
    coreOptions: List<ChimeroidCoreOption>,
    context: Context,
) {
    if (coreOptions.isEmpty()) return
    for (option in coreOptions) {
        CoreOptionItem(systemID = systemID, coreOption = option, context = context)
    }
}

/**
 * Renders auto-detected core variables (not declared in [GameSystem]) inside a
 * collapsible "All Core Options" section to avoid cluttering the main list.
 */
@Composable
private fun AutoDetectedCoreOptions(
    systemID: String,
    autoDetectedOptions: List<ChimeroidCoreOption>,
    context: Context,
) {
    if (autoDetectedOptions.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.core_settings_auto_detected),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                for (option in autoDetectedOptions) {
                    CoreOptionItem(systemID = systemID, coreOption = option, context = context)
                }
            }
        }
    }
}

@Composable
private fun ControllersOptions(
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    connectedGamePads: Int,
    context: Context,
) {
    val controllers = gameMenuRequest.coreConfig.controllerConfigs

    val visibleControllers = (0 until connectedGamePads)
        .map { it to controllers[it] }
        .filter { (_, cfgs) -> cfgs != null && cfgs.size >= 2 }

    if (visibleControllers.isEmpty()) return

    ChimeroidSettingsGroup(
        title = { Text(text = stringResource(R.string.core_settings_category_controllers)) },
    ) {
        visibleControllers.forEach { (port, controllerConfigs) ->
            ChimeroidSettingsList(
                title = {
                    Text(text = context.getString(R.string.core_settings_controller, (port + 1).toString()))
                },
                items = controllerConfigs!!.map { stringResource(id = it.displayName) },
                state = indexPreferenceState(
                    ControllerConfigsManager.getSharedPreferencesId(
                        gameMenuRequest.game.systemId,
                        gameMenuRequest.coreConfig.coreID,
                        port,
                    ),
                    controllerConfigs.map { it.name }.first(),
                    controllerConfigs.map { it.name },
                ),
            )
        }
    }
}
