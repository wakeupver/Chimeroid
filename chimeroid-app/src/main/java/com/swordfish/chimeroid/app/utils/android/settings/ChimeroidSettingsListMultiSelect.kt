package com.swordfish.chimeroid.app.utils.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ChimeroidSettingsListMultiSelect(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SettingValueState<Set<String>>,
    title: String,
    entryValues: List<String>,
    entries: List<String>,
    icon: (@Composable () -> Unit)? = null,
    confirmButton: String,
    subtitle: String? = null,
    onItemsSelected: ((List<String>) -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    if (entryValues.size != entries.size) {
        throw IllegalArgumentException("entries and entryValues need to have the same size")
    }

    var showDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    ChimeroidSettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = { showDialog = true },
    )

    val onAdd: (Int) -> Unit = { selectedIndex ->
        val mutable = state.value.toMutableSet()
        mutable.add(entryValues[selectedIndex])
        state.value = mutable
    }
    val onRemove: (Int) -> Unit = { selectedIndex ->
        val mutable = state.value.toMutableSet()
        mutable.remove(entryValues[selectedIndex])
        state.value = mutable
    }

    WindowDialog(
        show = showDialog,
        title = title,
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
            ) {
                if (subtitle != null) {
                    Text(text = subtitle)
                    Spacer(modifier = Modifier.size(8.dp))
                }

                entryValues.forEachIndexed { index, item ->
                    val isSelected by rememberUpdatedState(newValue = state.value.contains(item))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .toggleable(
                                    role = Role.Checkbox,
                                    value = isSelected,
                                    onValueChange = {
                                        if (isSelected) {
                                            onRemove(index)
                                        } else {
                                            onAdd(index)
                                        }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entries[index],
                            style = MiuixTheme.textStyles.body1,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                            onClick = null,
                        )
                    }
                }
            }
            TextButton(
                text = confirmButton,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showDialog = false
                    onItemsSelected?.invoke(entryValues.filter { state.value.contains(it) })
                },
            )
        }
    }
}
