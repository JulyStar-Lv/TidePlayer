package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PreferenceInsideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
fun AppPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    PreferenceContainer(modifier = modifier, showDivider = showDivider) {
        BasicComponent(
            title = title,
            summary = summary,
            titleColor = BasicComponentDefaults.titleColor(color = titleColor),
            startAction = leading.asPreferenceStartAction(),
            endActions = trailing,
            insideMargin = PreferenceInsideMargin,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

@Composable
fun AppArrowPreference(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    showDivider: Boolean = true,
) {
    PreferenceContainer(modifier = modifier, showDivider = showDivider) {
        ArrowPreference(
            title = title,
            summary = summary,
            startAction = leading.asPreferenceStartAction(),
            endActions = endActions,
            insideMargin = PreferenceInsideMargin,
            onClick = onClick,
            enabled = enabled,
        )
    }
}

@Composable
fun AppSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    PreferenceContainer(modifier = modifier, showDivider = showDivider) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            onCheckedChange = onCheckedChange,
            startAction = leading.asPreferenceStartAction(),
            insideMargin = PreferenceInsideMargin,
            enabled = enabled,
        )
    }
}

@Composable
fun AppSliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    PreferenceContainer(modifier = modifier, showDivider = showDivider) {
        SliderPreference(
            title = title,
            summary = summary,
            value = value,
            valueText = valueText,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            insideMargin = PreferenceInsideMargin,
        )
    }
}

@Composable
fun AppDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    selectedLabel: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val entry = DropdownEntry(
        items = buildList {
            if (selectedIndex !in items.indices && selectedLabel != null) {
                add(
                    DropdownItem(
                        text = selectedLabel,
                        enabled = false,
                        selected = true,
                    ),
                )
            }
            items.forEachIndexed { index, item ->
                add(
                    DropdownItem(
                        text = item,
                        selected = index == selectedIndex,
                        onClick = { onSelectedIndexChange(index) },
                    ),
                )
            }
        },
    )
    PreferenceContainer(modifier = modifier, showDivider = showDivider) {
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            entry = entry,
            insideMargin = PreferenceInsideMargin,
            enabled = enabled,
        )
    }
}

@Composable
private fun PreferenceContainer(
    modifier: Modifier,
    showDivider: Boolean,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        content()
        if (showDivider) {
            DesignListDivider()
        }
    }
}

private fun (@Composable () -> Unit)?.asPreferenceStartAction(): (@Composable () -> Unit)? =
    this?.let { content ->
        {
            Box(modifier = Modifier.padding(end = 6.dp)) {
                content()
            }
        }
    }
