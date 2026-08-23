package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.MAX_CUSTOM_THEME_SEEDS
import io.github.julystar.musicapp.core.domain.model.normalizeCustomThemeSeedArgbValues
import io.github.julystar.musicapp.core.domain.model.normalizeThemeSeedArgb
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.ThemeSeedPreviewTheme
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.pow

private data class ThemePreset(
    val argb: Long,
    val name: StringResource,
)

private val ThemePresets = listOf(
    ThemePreset(0xFFFF5B8AL, Res.string.settings_theme_color_preset_pink),
    ThemePreset(0xFF7A6CFFL, Res.string.settings_theme_color_preset_purple),
    ThemePreset(0xFF3D9AFFL, Res.string.settings_theme_color_preset_blue),
    ThemePreset(0xFFFF8A3DL, Res.string.settings_theme_color_preset_orange),
    ThemePreset(0xFF3DCA8AL, Res.string.settings_theme_color_preset_green),
    ThemePreset(0xFFFFD93DL, Res.string.settings_theme_color_preset_yellow),
)

@Composable
internal fun ThemeColorPickerDialog(
    show: Boolean,
    savedArgb: Long,
    customArgbValues: List<Long>,
    onApply: (Long) -> Unit,
    onCustomColorsChange: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftArgb by remember { mutableStateOf(normalizeThemeSeedArgb(savedArgb)) }
    var hexInput by remember { mutableStateOf(formatThemeSeedHex(savedArgb).removePrefix("#")) }
    LaunchedEffect(show, savedArgb) {
        if (show) {
            draftArgb = normalizeThemeSeedArgb(savedArgb)
            hexInput = formatThemeSeedHex(savedArgb).removePrefix("#")
        }
    }

    val normalizedCustom = remember(customArgbValues) {
        normalizeCustomThemeSeedArgbValues(customArgbValues)
    }
    val parsedHex = parseThemeSeedHex(hexInput)
    val isDuplicate = ThemePresets.any { it.argb == draftArgb } || normalizedCustom.contains(draftArgb)
    val isAtLimit = normalizedCustom.size >= MAX_CUSTOM_THEME_SEEDS

    fun updateDraft(argb: Long) {
        draftArgb = normalizeThemeSeedArgb(argb)
        hexInput = formatThemeSeedHex(argb).removePrefix("#")
    }

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_theme_color_picker_title),
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.settings_theme_color_picker_note),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
            val twoColumns = maxWidth >= 620.dp
            val scrollState = rememberScrollState()
            if (twoColumns) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PickerEditor(
                        draftArgb = draftArgb,
                        savedArgb = savedArgb,
                        customArgbValues = normalizedCustom,
                        hexInput = hexInput,
                        parsedHex = parsedHex,
                        isDuplicate = isDuplicate,
                        isAtLimit = isAtLimit,
                        onDraftChange = ::updateDraft,
                        onHexChange = { value ->
                            hexInput = value.filterNot { it == '#' }.take(6).uppercase()
                            parseThemeSeedHex(hexInput)?.let { draftArgb = it }
                        },
                        onCustomColorsChange = onCustomColorsChange,
                        modifier = Modifier.weight(1.15f),
                    )
                    ThemePreviews(
                        draftArgb = draftArgb,
                        modifier = Modifier.weight(0.85f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PickerEditor(
                        draftArgb = draftArgb,
                        savedArgb = savedArgb,
                        customArgbValues = normalizedCustom,
                        hexInput = hexInput,
                        parsedHex = parsedHex,
                        isDuplicate = isDuplicate,
                        isAtLimit = isAtLimit,
                        onDraftChange = ::updateDraft,
                        onHexChange = { value ->
                            hexInput = value.filterNot { it == '#' }.take(6).uppercase()
                            parseThemeSeedHex(hexInput)?.let { draftArgb = it }
                        },
                        onCustomColorsChange = onCustomColorsChange,
                    )
                    ThemePreviews(draftArgb = draftArgb)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            val horizontal = maxWidth >= 480.dp
            if (horizontal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                    ) { Text(stringResource(Res.string.settings_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = parsedHex != null,
                        onClick = { onApply(draftArgb) },
                    ) { Text(stringResource(Res.string.settings_theme_color_apply)) }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = parsedHex != null,
                        onClick = { onApply(draftArgb) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.settings_theme_color_apply)) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.settings_cancel)) }
                }
            }
        }
        }
    }
}

@Composable
private fun PickerEditor(
    draftArgb: Long,
    savedArgb: Long,
    customArgbValues: List<Long>,
    hexInput: String,
    parsedHex: Long?,
    isDuplicate: Boolean,
    isAtLimit: Boolean,
    onDraftChange: (Long) -> Unit,
    onHexChange: (String) -> Unit,
    onCustomColorsChange: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorPicker = DesignTokens.colorPicker
    val hexDescription = stringResource(Res.string.settings_theme_color_value)
    val hexErrorDescription = stringResource(Res.string.settings_theme_color_hex_error)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(colorPicker.sectionGap),
    ) {
        PickerSection(title = stringResource(Res.string.settings_theme_color_current_section)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(draftArgb.toInt()))
                        .border(1.dp, MiuixTheme.colorScheme.outline, CircleShape),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = formatThemeSeedHex(draftArgb),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(
                            if (normalizeThemeSeedArgb(savedArgb) == draftArgb) {
                                Res.string.settings_theme_color_saved
                            } else {
                                Res.string.settings_theme_color_unsaved
                            },
                        ),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        PickerSection(title = stringResource(Res.string.settings_theme_color_presets)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(colorPicker.gridGap),
                verticalArrangement = Arrangement.spacedBy(colorPicker.gridGap),
            ) {
                ThemePresets.forEach { preset ->
                    ThemeColorSwatch(
                        argb = preset.argb,
                        name = stringResource(preset.name),
                        selected = draftArgb == preset.argb,
                        onClick = { onDraftChange(preset.argb) },
                    )
                }
            }
        }

        PickerSection(
            title = stringResource(Res.string.settings_theme_color_saved_colors),
            trailing = "${customArgbValues.size}/$MAX_CUSTOM_THEME_SEEDS",
        ) {
            if (customArgbValues.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_theme_color_empty_palette),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MiuixTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(colorPicker.gridGap),
                    verticalArrangement = Arrangement.spacedBy(colorPicker.gridGap),
                ) {
                    customArgbValues.forEachIndexed { index, argb ->
                        SavedThemeColor(
                            argb = argb,
                            name = stringResource(
                                Res.string.settings_theme_color_custom_name,
                                index + 1,
                            ),
                            selected = draftArgb == argb,
                            onClick = { onDraftChange(argb) },
                            onRemove = {
                                onCustomColorsChange(
                                    customArgbValues.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    },
                                )
                            },
                        )
                    }
                }
            }
            val addLabel = when {
                isDuplicate -> Res.string.settings_theme_color_already_saved
                isAtLimit -> Res.string.settings_theme_color_palette_limit
                else -> Res.string.settings_theme_color_add_palette
            }
            Button(
                enabled = !isDuplicate && !isAtLimit && parsedHex != null,
                onClick = {
                    onCustomColorsChange(
                        normalizeCustomThemeSeedArgbValues(customArgbValues + draftArgb),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(addLabel)) }
        }

        PickerSection(title = stringResource(Res.string.settings_theme_color_custom_hsv)) {
            ColorPicker(
                color = Color(draftArgb.toInt()),
                onColorChanged = { onDraftChange(it.toArgb().toLong() and 0xFFFFFFFFL) },
            )
        }

        PickerSection(title = stringResource(Res.string.settings_theme_color_value)) {
            val error = parsedHex == null
            TextField(
                value = hexInput,
                onValueChange = onHexChange,
                label = "#$hexDescription",
                useLabelAsPlaceholder = true,
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = hexDescription
                        if (error) stateDescription = hexErrorDescription
                    },
            )
            if (error) {
                Text(
                    text = "⚠ ${stringResource(Res.string.settings_theme_color_hex_error)}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PickerSection(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title.uppercase(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold,
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        content()
    }
}

@Composable
private fun ThemeColorSwatch(
    argb: Long,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val color = Color(argb.toInt())
    val outlineColor = when {
        focused -> MiuixTheme.colorScheme.onSurface
        selected -> color
        hovered -> MiuixTheme.colorScheme.outline
        else -> Color.Transparent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(DesignTokens.colorPicker.swatchSize)
                .scale(if (pressed) 0.95f else 1f)
                .border(if (selected || focused || hovered) 3.dp else 0.dp, outlineColor, CircleShape)
                .padding(if (selected || focused || hovered) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(color)
                .hoverable(interactionSource)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = "$name, ${formatThemeSeedHex(argb)}"
                    this.selected = selected
                    role = Role.RadioButton
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = contrastColor(color),
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = name,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SavedThemeColor(
    argb: Long,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val removeDescription = stringResource(Res.string.settings_theme_color_remove, name)
    Row(verticalAlignment = Alignment.Top) {
        ThemeColorSwatch(argb = argb, name = name, selected = selected, onClick = onClick)
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = removeDescription
                    role = Role.Button
                }
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.title3,
            )
        }
    }
}

@Composable
private fun ThemePreviews(
    draftArgb: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeSeedPreviewTheme(seedColor = Color(draftArgb.toInt()), darkTheme = false) {
            ThemePreviewCard(dark = false)
        }
        ThemeSeedPreviewTheme(seedColor = Color(draftArgb.toInt()), darkTheme = true) {
            ThemePreviewCard(dark = true)
        }
        Text(
            text = stringResource(Res.string.settings_theme_color_preview_note),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )
    }
}

@Composable
private fun ThemePreviewCard(dark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.background)
            .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_theme_color_preview),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(
                if (dark) {
                    Res.string.settings_theme_color_preview_dark
                } else {
                    Res.string.settings_theme_color_preview_light
                },
            ),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote2,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_theme_color_preview_selected),
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.settings_theme_color_preview_secondary),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                style = MiuixTheme.textStyles.footnote2,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.secondaryVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.66f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {},
                ) { Text(stringResource(Res.string.settings_theme_color_preview_primary)) }
                Button(
                    onClick = {},
                ) {
                    Text(
                        stringResource(Res.string.settings_theme_color_preview_secondary_action),
                    )
                }
            }
        }
    }
}

internal fun formatThemeSeedHex(argb: Long): String {
    return "#${(normalizeThemeSeedArgb(argb) and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')}"
}

internal fun parseThemeSeedHex(value: String): Long? {
    val body = value.trim().removePrefix("#")
    if (body.length != 6 || body.any { !it.isDigit() && it.uppercaseChar() !in 'A'..'F' }) {
        return null
    }
    return body.toLongOrNull(16)?.let { 0xFF000000L or it }
}

private fun contrastColor(color: Color): Color {
    fun channel(value: Float): Double {
        return if (value <= 0.03928f) {
            (value / 12.92f).toDouble()
        } else {
            (((value + 0.055f) / 1.055f).toDouble()).pow(2.4)
        }
    }
    val luminance =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    return if (luminance > 0.42) Color(0xFF0D0B18) else Color.White
}
