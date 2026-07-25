package com.swordfish.chimeroid.app.mobile.feature.gamemenu.patchcodes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.lib.library.db.entity.PatchCode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun GameMenuPatchCodesScreen(viewModel: GameMenuPatchCodesViewModel) {
    val context = LocalContext.current
    val codes by viewModel.patchCodes.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker for .cht files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importChtFile(context, uri)
        }
    }

    // Observe import results and show snackbar
    LaunchedEffect(Unit) {
        viewModel.importResult.collect { result ->
            val message = when (result) {
                is GameMenuPatchCodesViewModel.ImportResult.Success ->
                    context.getString(R.string.patch_codes_import_success, result.imported, result.skipped)
                is GameMenuPatchCodesViewModel.ImportResult.Error ->
                    context.getString(R.string.patch_codes_import_error, result.message)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Import .cht file FAB (small, above main FAB)
                FloatingActionButton(
                    onClick = {
                        filePicker.launch(
                            // Accept .cht and any text-based file since some file managers
                            // don't recognise the .cht MIME type
                            arrayOf("*/*"),
                        )
                    },
                    containerColor = MiuixTheme.colorScheme.secondaryContainer,
                    minWidth = 40.dp,
                    minHeight = 40.dp,
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = stringResource(R.string.patch_codes_import_cht),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Add manually FAB
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.patch_codes_add),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (codes.isEmpty()) {
                EmptyCodesPlaceholder()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(codes, key = { it.id }) { patchCode ->
                        PatchCodeItem(
                            patchCode = patchCode,
                            onToggle = { viewModel.toggleCode(patchCode) },
                            onDelete = { viewModel.deleteCode(patchCode) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPatchCodeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { desc, code ->
                val added = viewModel.addCode(desc, code)
                if (added) showAddDialog = false
            },
        )
    }
}

@Composable
private fun PatchCodeItem(
    patchCode: PatchCode,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (patchCode.enabled)
                MiuixTheme.colorScheme.primaryContainer
            else
                MiuixTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                state = if (patchCode.enabled) ToggleableState.On else ToggleableState.Off,
                onClick = onToggle,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patchCode.description,
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 2,
                )
                Text(
                    text = patchCode.code,
                    style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.patch_codes_delete),
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyCodesPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.patch_codes_empty_title),
            style = MiuixTheme.textStyles.title3,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.patch_codes_empty_subtitle),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.patch_codes_import_hint),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun AddPatchCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (description: String, code: String) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    WindowDialog(
        show = true,
        title = stringResource(R.string.patch_codes_add_dialog_title),
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = description,
                onValueChange = { description = it; showError = false },
                label = stringResource(R.string.patch_codes_field_description),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = code,
                onValueChange = { code = it.uppercase(); showError = false },
                label = stringResource(R.string.patch_codes_field_code),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.patch_codes_field_code_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (showError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.patch_codes_error_empty_fields),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = stringResource(android.R.string.cancel), onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.patch_codes_add_confirm),
                    onClick = {
                        if (description.isBlank() || code.isBlank()) showError = true
                        else onConfirm(description, code)
                    },
                )
            }
        }
    }
}
