package com.swordfish.chimeroid.app.utils.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun ChimeroidSettingsList(
    enabled: Boolean = true,
    state: SettingValueState<Int>,
    title: String,
    items: List<String>,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    subtitle: String? = null,
    closeDialogDelay: Long = 200,
    action: (@Composable RowScope.() -> Unit)? = null,
    onItemSelected: ((Int, String) -> Unit)? = null,
) {
    if (state.value >= items.size) {
        throw IndexOutOfBoundsException("Current value for $title list setting cannot be grater than items size")
    }

    var showDialog by remember { mutableStateOf(false) }

    val safeSubtitle =
        if (state.value >= 0 && useSelectedValueAsSubtitle) {
            items[state.value]
        } else {
            subtitle
        }

    ChimeroidSettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = safeSubtitle,
        action = action,
        onClick = { showDialog = true },
    )

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val onSelected: (Int, Boolean) -> Unit = { selectedIndex, updateState ->
        coroutineScope.launch {
            if (updateState) state.value = selectedIndex
            delay(closeDialogDelay)
            showDialog = false
        }
    }

    WindowDialog(
        show = showDialog,
        title = title,
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .selectableGroup(),
        ) {
            if (subtitle != null) {
                Text(text = subtitle)
                Spacer(modifier = Modifier.size(8.dp))
            }

            items.forEachIndexed { index, item ->
                val isSelected by rememberUpdatedState(newValue = state.value == index)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                role = Role.RadioButton,
                                selected = isSelected,
                                onClick = {
                                    onSelected(index, !isSelected)
                                    onItemSelected?.invoke(index, items[index])
                                },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                    )
                    Text(
                        text = item,
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}
