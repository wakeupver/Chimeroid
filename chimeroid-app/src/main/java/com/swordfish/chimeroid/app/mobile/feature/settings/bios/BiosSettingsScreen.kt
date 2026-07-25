package com.swordfish.chimeroid.app.mobile.feature.settings.bios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidCardSettingsGroup
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsMenuLink
import com.swordfish.chimeroid.app.utils.android.settings.ChimeroidSettingsPage
import com.swordfish.chimeroid.lib.bios.Bios

@Composable
fun BiosScreen(
    modifier: Modifier = Modifier,
    viewModel: BiosSettingsViewModel,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    ChimeroidSettingsPage(modifier = modifier.fillMaxSize()) {
        if (uiState.detected.isNotEmpty()) {
            DetectedEntries(uiState.detected)
        }
        if (uiState.notDetected.isNotEmpty()) {
            SupportedEntries(uiState.notDetected)
        }
    }
}

@Composable
private fun DetectedEntries(detected: List<Bios>) {
    ChimeroidCardSettingsGroup(
        title = stringResource(id = R.string.settings_bios_category_detected),
    ) {
        detected.forEach {
            BiosEntry(it, true)
        }
    }
}

@Composable
private fun SupportedEntries(supported: List<Bios>) {
    ChimeroidCardSettingsGroup(
        title = stringResource(id = R.string.settings_bios_category_not_detected),
    ) {
        supported.forEach {
            BiosEntry(it, false)
        }
    }
}

@Composable
fun BiosEntry(
    bios: Bios,
    detected: Boolean,
) {
    ChimeroidSettingsMenuLink(
        title = bios.description,
        subtitle = bios.displayName(),
        enabled = detected,
        onClick = { },
    )
}
