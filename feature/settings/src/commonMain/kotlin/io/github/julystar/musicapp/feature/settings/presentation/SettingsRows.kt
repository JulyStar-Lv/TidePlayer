package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_adjust
import musicapp.core.presentation.generated.resources.icon_album
import musicapp.core.presentation.generated.resources.icon_chevron_left
import musicapp.core.presentation.generated.resources.icon_cloud
import musicapp.core.presentation.generated.resources.icon_dashboard
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_file
import musicapp.core.presentation.generated.resources.icon_folder
import musicapp.core.presentation.generated.resources.icon_image
import musicapp.core.presentation.generated.resources.icon_lyrics
import musicapp.core.presentation.generated.resources.icon_mode_repeat
import musicapp.core.presentation.generated.resources.icon_music_note
import musicapp.core.presentation.generated.resources.icon_onedrive
import musicapp.core.presentation.generated.resources.icon_ok
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_setting
import musicapp.core.presentation.generated.resources.icon_timelapse
import musicapp.core.presentation.generated.resources.icon_wifitethering
import musicapp.feature.settings.generated.resources.Res as SettingsRes
import musicapp.feature.settings.generated.resources.settings_action_cancel
import musicapp.feature.settings.generated.resources.settings_action_clear
import musicapp.feature.settings.generated.resources.settings_action_confirm
import musicapp.feature.settings.generated.resources.settings_action_done
import musicapp.feature.settings.generated.resources.settings_action_failed_retry
import musicapp.feature.settings.generated.resources.settings_action_working
import musicapp.feature.settings.generated.resources.settings_cancel
import musicapp.feature.settings.generated.resources.settings_save
import kotlin.math.roundToInt

@Composable
internal fun SettingsPageLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    compactHorizontalPadding: Dp? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    val pageScrollState = rememberScrollState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = if (onBack == null) {
                    {}
                } else {
                    {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(CoreRes.drawable.icon_chevron_left),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val pageWidth = minOf(maxWidth, 800.dp)
            val pagePadding = if (maxWidth <= DesignTokens.adaptive.compactMaxWidth) {
                compactHorizontalPadding ?: spacing.pageExpanded
            } else {
                spacing.pageExpanded
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(pageWidth)
                    .fillMaxHeight()
                    .let { modifier ->
                        if (scrollable) modifier.verticalScroll(pageScrollState) else modifier
                    }
                    .padding(
                        start = pagePadding,
                        top = spacing.xs,
                        end = pagePadding,
                        bottom = maxOf(DesignTokens.player.miniBarHeight, bottomContentInset) + spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(text = title)
        Card(content = content)
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    marker: String? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        startAction = marker?.let { iconMarker ->
            { SettingsLeadingIcon(marker = iconMarker, accentColor = accentColor) }
        },
    )
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    summary: String? = null,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    var previewValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderPreference(
        title = title,
        summary = summary,
        value = previewValue,
        valueText = valueText,
        onValueChange = { previewValue = it },
        onValueChangeFinished = { onValueChange(previewValue.roundToInt()) },
        enabled = enabled,
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    )
}

@Composable
internal fun SettingsInfoRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    marker: String? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    val leading = marker?.let { iconMarker ->
        @Composable { SettingsLeadingIcon(marker = iconMarker, accentColor = accentColor) }
    }
    if (onClick != null) {
        ArrowPreference(
            title = title,
            summary = value,
            enabled = enabled,
            onClick = onClick,
            startAction = leading,
        )
    } else {
        BasicComponent(
            title = title,
            summary = value,
            enabled = enabled,
            startAction = leading,
        )
    }
}

@Composable
internal fun SettingsDangerRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
    )
}

@Composable
private fun SettingsLeadingIcon(
    marker: String,
    accentColor: Color,
) {
    val drawable = markerDrawable(marker)
    if (drawable != null) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    } else {
        Text(
            text = marker,
            color = accentColor,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun markerDrawable(marker: String): DrawableResource? = when (marker) {
    "≋" -> CoreRes.drawable.icon_wifitethering
    "◠", "≡", "◈" -> CoreRes.drawable.icon_adjust
    "↓" -> CoreRes.drawable.icon_download
    "▷" -> CoreRes.drawable.icon_play
    "↻", "↺" -> CoreRes.drawable.icon_mode_repeat
    "⌁" -> CoreRes.drawable.icon_timelapse
    "◎", "W", "G", "E", "P", "J", "N", "D", "C" -> CoreRes.drawable.icon_cloud
    "O" -> CoreRes.drawable.icon_onedrive
    "S" -> CoreRes.drawable.icon_folder
    "▦", "▣" -> CoreRes.drawable.icon_album
    "DS" -> CoreRes.drawable.icon_dashboard
    "◇", "文", "§" -> CoreRes.drawable.icon_file
    "♫" -> CoreRes.drawable.icon_lyrics
    "◐", "◌" -> CoreRes.drawable.icon_image
    "▢" -> CoreRes.drawable.icon_dashboard
    "☾" -> CoreRes.drawable.icon_setting
    "⌕" -> CoreRes.drawable.icon_search
    "♪" -> CoreRes.drawable.icon_music_note
    else -> null
}

@Composable
internal fun SettingsConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(SettingsRes.string.settings_cancel),
                onClick = onDismiss,
            )
            TextButton(
                text = confirmText,
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error,
                ),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
internal fun SettingsInputDialog(
    show: Boolean,
    title: String,
    message: String,
    value: String,
    label: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(SettingsRes.string.settings_cancel),
                onClick = onDismiss,
            )
            TextButton(
                text = stringResource(SettingsRes.string.settings_save),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onConfirm,
            )
        }
    }
}

internal fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${formatOneDecimal(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${formatOneDecimal(mb)} MB"
    return "${formatOneDecimal(mb / 1024.0)} GB"
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10).toLong()
    val whole = scaled / 10
    val decimal = scaled % 10
    return if (decimal == 0L) whole.toString() else "$whole.$decimal"
}

// ── Select Row ──

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SettingsSelectRow(
    label: String,
    subtitle: String? = null,
    selectedValue: String,
    selectedLabel: String,
    options: List<SettingsSelectOption>,
    enabled: Boolean = true,
    menuMinWidth: Dp = 200.dp,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }
    val entry = DropdownEntry(
        items = buildList {
            if (selectedIndex !in options.indices) {
                add(DropdownItem(text = selectedLabel, enabled = false, selected = true))
            }
            options.forEachIndexed { index, option ->
                add(
                    DropdownItem(
                        text = option.label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(option.value) },
                    ),
                )
            }
        },
    )
    OverlayDropdownPreference(
        title = label,
        summary = subtitle,
        entry = entry,
        enabled = enabled,
    )
}

@Immutable
internal data class SettingsSelectOption(
    val value: String,
    val label: String,
)

@Composable
internal fun <T> SettingsSelectRow(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    subtitle: String? = null,
    enabled: Boolean = true,
    menuMinWidth: Dp = 200.dp,
    onSelect: (T) -> Unit,
) {
    val selectedIndex = options.indexOf(selected)
    SettingsSelectRow(
        label = label,
        subtitle = subtitle,
        selectedValue = selectedIndex.toString(),
        selectedLabel = optionLabel(selected),
        options = options.mapIndexed { index, option ->
            SettingsSelectOption(value = index.toString(), label = optionLabel(option))
        },
        enabled = enabled,
        menuMinWidth = menuMinWidth,
        onSelect = { value ->
            value.toIntOrNull()?.let(options::getOrNull)?.let(onSelect)
        },
    )
}
