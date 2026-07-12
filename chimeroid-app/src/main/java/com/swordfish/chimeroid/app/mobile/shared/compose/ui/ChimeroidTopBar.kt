package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R

// ─────────────────────────────────────────────────────────────────────────────
// Single source of truth for top-bar geometry. HomeScreen's collapsing header
// and the shared top bar (every other route) both read these values so their
// sizing/insets can never silently drift apart again.
// ─────────────────────────────────────────────────────────────────────────────
object ChimeroidTopBarDefaults {
    val Height: Dp = 56.dp
    val HomeExpandedHeight: Dp = 156.dp
    val TitleStartPadding: Dp = 24.dp
    val ActionsEndPadding: Dp = 4.dp
    val ShadowElevation: Dp = 4.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared top-bar row: optional leading nav icon, a flexible title slot, and a
// trailing actions slot — pinned to ChimeroidTopBarDefaults.Height. Used as-is
// on non-Home routes, and pinned at the top of HomeScreen's animated header so
// both stay pixel-identical once fully collapsed.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChimeroidTopBarChrome(
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ChimeroidTopBarDefaults.Height)
            .padding(
                start = ChimeroidTopBarDefaults.TitleStartPadding,
                end = ChimeroidTopBarDefaults.ActionsEndPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke()
        Box(modifier = Modifier.weight(1f)) { title() }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info | Save-sync (optional) | Settings (optional) — the app's standard
// top-bar actions, shared verbatim between HomeScreen and every other route.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChimeroidTopBarActions(
    onHelpPressed: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    saveSyncEnabled: Boolean,
    operationInProgress: Boolean,
    showSettingsAction: Boolean = true,
) {
    IconButton(onClick = onHelpPressed) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.mobile_settings_help),
        )
    }
    if (saveSyncEnabled) {
        IconButton(onClick = onSyncClick, enabled = !operationInProgress) {
            Icon(
                imageVector = Icons.Outlined.CloudSync,
                contentDescription = stringResource(R.string.save_sync),
            )
        }
    }
    if (showSettingsAction) {
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }
}
