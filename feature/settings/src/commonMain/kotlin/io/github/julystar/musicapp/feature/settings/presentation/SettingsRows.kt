package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.presentation.components.DesignLoadingIndicator
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.julystar.musicapp.core.presentation.components.AppSwitch
import io.github.julystar.musicapp.core.presentation.components.AppTextField
import io.github.julystar.musicapp.core.presentation.components.DesignChevron
import io.github.julystar.musicapp.core.presentation.components.DesignChevronDirection
import io.github.julystar.musicapp.core.presentation.components.DesignDialog
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.DesignPreferenceRow
import io.github.julystar.musicapp.core.presentation.components.DesignSettingsGroup
import io.github.julystar.musicapp.core.presentation.components.DesignSlider
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.theme.DesignGradients
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_adjust
import musicapp.core.presentation.generated.resources.icon_album
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageWidth = minOf(maxWidth, 800.dp)
        val pagePadding = if (maxWidth <= DesignTokens.adaptive.compactMaxWidth) {
            compactHorizontalPadding ?: spacing.pageExpanded
        } else {
            spacing.pageExpanded
        }
        val showTopBar = maxWidth < 1024.dp || onBack != null
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(pageWidth)
                .fillMaxHeight()
                .background(MiuixTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = pagePadding,
                        top = if (showTopBar) {
                            DesignTokens.adaptive.compactHeaderHeight + spacing.xs
                        } else {
                            spacing.xs
                        },
                        end = pagePadding,
                        bottom = maxOf(DesignTokens.player.miniBarHeight, bottomContentInset) + spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = content,
            )
            if (showTopBar) {
                DesignStickyGlassActionBar(
                    title = title,
                    collapseFraction = 1f,
                    onNavigateBack = onBack,
                    compactTitle = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
internal fun SettingsEntryCard(
    title: String,
    summary: String?,
    icon: DrawableResource,
    onClick: (() -> Unit)? = null,
) {
    DesignPreferenceRow(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            SettingsLeadingIcon(drawable = icon)
        },
        trailing = if (onClick != null) {
            { DesignChevron(direction = DesignChevronDirection.Right) }
        } else {
            null
        },
    )
}

@Composable
private fun SettingsLeadingIcon(drawable: DrawableResource) {
    SettingsIconBadge(drawable = drawable)
}

@Composable
internal fun SettingsIconBadge(
    drawable: DrawableResource,
    colors: List<Color> = DesignGradients.Brand.colors,
    modifier: Modifier = Modifier,
    preserveDrawableColors: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .then(
                if (preserveDrawableColors) {
                    Modifier
                } else {
                    Modifier.background(Brush.linearGradient(colors))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (preserveDrawableColors) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(drawable),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    DesignSettingsGroup(title = title, content = content)
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
    DesignPreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        leading = marker?.let { iconMarker ->
            { SettingsLeadingIcon(marker = iconMarker, accentColor = accentColor) }
        },
        trailing = {
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valueText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            DesignSlider(
                value = previewValue,
                onValueChange = { previewValue = it },
                onValueChangeFinished = { onValueChange(previewValue.roundToInt()) },
                enabled = enabled,
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
            )
        }
        if (showDivider) {
            DesignListDivider()
        }
    }
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
    DesignPreferenceRow(
        title = title,
        summary = value,
        enabled = enabled,
        onClick = onClick,
        leading = marker?.let { iconMarker ->
            { SettingsLeadingIcon(marker = iconMarker, accentColor = accentColor) }
        },
        trailing = if (onClick != null) {
            {
                DesignChevron(direction = DesignChevronDirection.Right)
            }
        } else {
            null
        },
    )
}

@Composable
internal fun SettingsDangerRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DesignPreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        titleColor = MiuixTheme.colorScheme.error,
    )
}

@Composable
private fun SettingsLeadingIcon(
    marker: String,
    accentColor: Color,
) {
    val drawable = markerDrawable(marker)
    if (drawable != null) {
        SettingsIconBadge(
            drawable = drawable,
            colors = markerGradient(marker, accentColor),
        )
    } else {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(4.dp, shape, clip = false)
                .clip(shape)
                .background(Brush.linearGradient(markerGradient(marker, accentColor))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = marker,
                color = Color.White,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun markerGradient(marker: String, accentColor: Color): List<Color> = when (marker) {
    "≋", "◎", "W", "G", "E", "P", "J", "N", "D", "C", "O" ->
        DesignGradients.BluePurple.colors
    "◠", "≡", "◈", "DS", "▢", "☾" -> DesignGradients.PurplePink.colors
    "↓", "⌁", "S", "▦", "▣" -> DesignGradients.OrangeYellow.colors
    "▷", "↻", "↺", "♫", "♪" -> DesignGradients.PinkOrange.colors
    "◇", "文", "§", "◐", "◌", "⌕" -> DesignGradients.GreenBlue.colors
    "●" -> listOf(accentColor, DesignPalette.BrandPink)
    else -> DesignGradients.Brand.colors
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
    DesignDialog(
        show = show,
        onDismiss = onDismiss,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.xs))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DesignTextButton(
                text = stringResource(SettingsRes.string.settings_cancel),
                variant = DesignTextButtonVariant.Default,
                size = DesignTextButtonSize.Medium,
                onClick = onDismiss,
            )
            DesignTextButton(
                text = confirmText,
                variant = DesignTextButtonVariant.Error,
                size = DesignTextButtonSize.Medium,
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
    DesignDialog(
        show = show,
        onDismiss = onDismiss,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.xs))
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.sm))
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DesignTextButton(
                text = stringResource(SettingsRes.string.settings_cancel),
                variant = DesignTextButtonVariant.Default,
                size = DesignTextButtonSize.Medium,
                onClick = onDismiss,
            )
            DesignTextButton(
                text = stringResource(SettingsRes.string.settings_save),
                variant = DesignTextButtonVariant.Primary,
                size = DesignTextButtonSize.Medium,
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

// ── Select Row (popup choice menu) ──

@Composable
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
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Box {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(enabled = enabled) { menuOpen = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = selectedLabel,
                color = if (menuOpen) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            // Up/down chevron
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DesignChevron(
                    direction = DesignChevronDirection.Right,
                    size = 10.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = -90f),
                )
                DesignChevron(
                    direction = DesignChevronDirection.Right,
                    size = 10.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = 90f),
                )
            }
        }

            if (menuOpen) {
                Popup(
                    alignment = Alignment.TopEnd,
                    properties = PopupProperties(focusable = true),
                    onDismissRequest = { menuOpen = false },
                ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = menuMinWidth, max = 300.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selectedValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    onSelect(option.value)
                                    menuOpen = false
                                }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                color = if (isSelected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    painter = painterResource(CoreRes.drawable.icon_ok),
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        DesignListDivider()
    }
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

// ── Action Row (destructive actions with confirm states) ──

@Composable
internal fun SettingsActionRow(
    label: String,
    subtitle: String,
    state: SettingsActionState,
    actionLabel: String? = null,
    onStateChange: (SettingsActionState) -> Unit,
    onConfirm: () -> Unit,
) {
    val effectiveActionLabel = actionLabel ?: stringResource(SettingsRes.string.settings_action_clear)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
            )
            val statusText = when (state) {
                SettingsActionState.Busy -> stringResource(SettingsRes.string.settings_action_working)
                SettingsActionState.Success -> stringResource(SettingsRes.string.settings_action_done)
                SettingsActionState.Error -> stringResource(SettingsRes.string.settings_action_failed_retry)
                else -> subtitle
            }
            Text(
                text = statusText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )

            // Confirm buttons
            if (state == SettingsActionState.Confirm) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(SettingsRes.string.settings_action_confirm),
                        color = Color.White,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.error)
                            .clickable { onConfirm() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Text(
                        text = stringResource(SettingsRes.string.settings_action_cancel),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onStateChange(SettingsActionState.Idle) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Right side state indicator
        when (state) {
            SettingsActionState.Busy -> DesignLoadingIndicator(size = 18.dp)
            SettingsActionState.Success -> Icon(
                painter = painterResource(CoreRes.drawable.icon_ok),
                contentDescription = null,
                tint = DesignPalette.SupportGreen,
                modifier = Modifier.size(18.dp),
            )
            SettingsActionState.Idle -> Text(
                text = effectiveActionLabel,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onStateChange(SettingsActionState.Confirm) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
            else -> {}
        }
    }
}

internal enum class SettingsActionState {
    Idle,
    Confirm,
    Busy,
    Success,
    Error,
}
