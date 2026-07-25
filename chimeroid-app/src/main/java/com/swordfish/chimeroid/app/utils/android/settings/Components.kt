package com.swordfish.chimeroid.app.utils.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun ChimeroidSettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
fun ChimeroidSettingsSwitch(
    enabled: Boolean = true,
    state: SettingValueState<Boolean>,
    icon: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    SwitchPreference(
        checked = state.value,
        onCheckedChange = {
            state.value = it
            onCheckedChange(it)
        },
        title = title,
        summary = subtitle,
        startAction = icon,
        endActions = action ?: {},
        enabled = enabled,
    )
}

@Composable
fun ChimeroidSettingsMenuLink(
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: String,
    titleColor: Color? = null,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val content = @Composable {
        ArrowPreference(
            title = title,
            summary = subtitle,
            startAction = icon,
            endActions = action ?: {},
            onClick = onClick,
            enabled = enabled,
        )
    }
    if (titleColor != null) {
        CompositionLocalProvider(LocalContentColor provides titleColor, content = content)
    } else {
        content()
    }
}

@Composable
fun ChimeroidSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Column {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun ChimeroidCardSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
    ) {
        Card {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun ChimeroidSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    title: String,
    subtitle: String? = null,
) {
    SliderPreference(
        modifier = modifier,
        value = state.value.toFloat(),
        onValueChange = { state.value = it.roundToInt() },
        steps = steps,
        valueRange = valueRange,
        title = title,
        summary = subtitle,
        enabled = enabled,
    )
}

@Composable
private fun SettingsGroupTitleSmall(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    )
}
