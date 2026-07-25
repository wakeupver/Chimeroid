package com.swordfish.chimeroid.app.mobile.feature.gamemenu.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.swordfish.chimeroid.app.shared.game.macro.MacroButton
import com.swordfish.touchinput.controller.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ────────────────────────────────────────────────────────────────────────────
// Screen — list / add / delete. Dragging a button to a new position happens
// live on the game screen (see MacroButtonOverlay); [onPositionOnScreen]
// hands off to that flow instead of duplicating it here.
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun GameMenuMacrosScreen(
    viewModel: GameMenuMacrosViewModel,
    onPositionOnScreen: () -> Unit,
) {
    val macroButtons by viewModel.macroButtons.collectAsState()
    var showAddMacroDialog by remember { mutableStateOf(false) }
    val atLimit = macroButtons.size >= MacroButton.MAX_BUTTONS

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (macroButtons.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = onPositionOnScreen,
                        minWidth = 40.dp,
                        minHeight = 40.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = stringResource(R.string.macro_position_button),
                        )
                    }
                }
                if (!atLimit) {
                    FloatingActionButton(onClick = { showAddMacroDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.macro_add_button),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (macroButtons.isEmpty()) {
                EmptyMacrosPlaceholder()
            } else {
                macroButtons.forEach { btn ->
                    key(btn.id) {
                        MacroButtonListItem(btn = btn, onDelete = { viewModel.deleteMacro(btn.id) })
                    }
                }
                if (atLimit) {
                    Text(
                        text = stringResource(R.string.macro_at_limit, MacroButton.MAX_BUTTONS),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(140.dp)) // clears the stacked FAB group when scrolled to the end
            }
        }
    }

    if (showAddMacroDialog) {
        AddMacroDialog(
            existingButtons = macroButtons,
            onConfirm = { newMacro ->
                viewModel.addOrUpdateMacro(newMacro)
                showAddMacroDialog = false
            },
            onDismiss = { showAddMacroDialog = false },
        )
    }
}

@Composable
private fun EmptyMacrosPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.macro_empty_hint),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Macro list item
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroButtonListItem(
    btn: MacroButton,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MiuixTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = btn.label,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = btn.keyCodes.joinToString(" + ") { MacroButton.keyName(it) },
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.macro_delete),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.error,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Add Macro dialog
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddMacroDialog(
    existingButtons: List<MacroButton>,
    onConfirm: (MacroButton) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf(emptySet<Int>()) }

    // Auto-generate label from selected keys when label is blank
    val autoLabel = remember(selectedKeys) {
        if (selectedKeys.isEmpty()) "" else MacroButton.autoLabel(selectedKeys.toList())
    }
    val displayLabel = label.ifBlank { autoLabel }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.macro_dialog_title),
                    style = MiuixTheme.textStyles.title3,
                )

                // Label input
                TextField(
                    value = label,
                    onValueChange = { if (it.length <= 6) label = it },
                    label = stringResource(R.string.macro_label_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Key selection
                Text(
                    text = stringResource(R.string.macro_keys_label),
                    style = MiuixTheme.textStyles.body2,
                )
                MacroKeyGrid(
                    selectedKeys = selectedKeys,
                    onToggle = { keyCode ->
                        selectedKeys = if (keyCode in selectedKeys) {
                            selectedKeys - keyCode
                        } else {
                            selectedKeys + keyCode
                        }
                    },
                )

                // Cancel / OK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(text = stringResource(R.string.macro_cancel), onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedKeys.isNotEmpty()) {
                                val (spawnX, spawnY) = MacroButton.nextSpawnPosition(existingButtons)
                                onConfirm(
                                    MacroButton(
                                        label = displayLabel.take(6).ifBlank { "M" },
                                        keyCodes = selectedKeys.toList(),
                                        xFraction = spawnX,
                                        yFraction = spawnY,
                                    ),
                                )
                            }
                        },
                        enabled = selectedKeys.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.macro_confirm_add))
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Key selection grid
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroKeyGrid(
    selectedKeys: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    // ALL_KEYS is a fixed 10-entry companion list — chunking it is invariant,
    // so it's cached once per composition instead of re-sliced on every
    // key-toggle recomposition.
    val rows = remember { MacroButton.ALL_KEYS.chunked(5) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { (keyCode, keyName) ->
                    MacroKeyChip(
                        selected = keyCode in selectedKeys,
                        label = keyName,
                        onClick = { onToggle(keyCode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Miuix has no FilterChip primitive — a small selectable Surface recreates the
// same "toggle pill" behavior using components already confirmed available.
@Composable
private fun MacroKeyChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}
