package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.ParametricEqBand
import io.github.julystar.musicapp.core.domain.model.ParametricEqFilterType
import io.github.julystar.musicapp.core.domain.model.ParametricEqualizerSettings
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun ParametricEqualizerEditor(
    settings: ParametricEqualizerSettings,
    enabled: Boolean,
    maxBands: Int,
    onUpdate: (ParametricEqualizerSettings) -> Unit,
) {
    if (settings.bands.isEmpty()) {
        Text(
            text = stringResource(Res.string.settings_peq_empty),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(16.dp),
        )
    }
    settings.bands.forEachIndexed { index, band ->
        ParametricBandCard(
            index = index,
            band = band,
            enabled = enabled,
            onUpdate = { updated ->
                onUpdate(
                    settings.copy(
                        bands = settings.bands.toMutableList().apply { this[index] = updated },
                    ),
                )
            },
            onRemove = {
                onUpdate(
                    settings.copy(
                        bands = settings.bands.filterIndexed { itemIndex, _ -> itemIndex != index },
                    ),
                )
            },
        )
    }
    ArrowPreference(
        title = stringResource(Res.string.settings_peq_add_band),
        summary = stringResource(
            Res.string.settings_peq_band_count,
            settings.bands.size,
            maxBands,
        ),
        enabled = enabled && settings.bands.size < maxBands,
        onClick = {
            onUpdate(settings.copy(bands = settings.bands + ParametricEqBand()))
        },
    )
}

@Composable
private fun ParametricBandCard(
    index: Int,
    band: ParametricEqBand,
    enabled: Boolean,
    onUpdate: (ParametricEqBand) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(index) { mutableStateOf(false) }
    val qLabel = stringResource(Res.string.settings_peq_q)
    val summary = listOf(
        formatHz(band.frequencyHz),
        formatTenthsDb(band.gainTenthsDb),
        "$qLabel ${formatHundredths(band.qHundredths)}",
    ).joinToString(" · ")
    BasicComponent(
        title = stringResource(Res.string.settings_peq_band, index + 1),
        summary = summary,
        enabled = enabled,
        onClick = { if (band.enabled) expanded = !expanded },
        endActions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = band.enabled,
                    enabled = enabled,
                    onCheckedChange = { checked ->
                        if (!checked) expanded = false
                        onUpdate(band.copy(enabled = checked))
                    },
                )
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_chevron_right),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                )
            }
        },
    )
    HorizontalDivider()
    if (expanded && band.enabled) {
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_peq_filter_type),
            enabled = enabled,
            entries = listOf(DropdownEntry(items = ParametricEqFilterType.entries.map { type ->
                DropdownItem(
                    text = stringResource(type.titleResource()),
                    selected = type == band.type,
                    onClick = { onUpdate(band.copy(type = type)) },
                )
            })),
        )
        var frequencyPreview by remember(band.frequencyHz) { mutableFloatStateOf(band.frequencyHz.toFloat()) }
        SliderPreference(
            title = stringResource(Res.string.settings_peq_frequency),
            value = frequencyPreview,
            valueRange = 10f..20_000f,
            steps = 19_989,
            valueText = formatHz(frequencyPreview.roundToInt()),
            enabled = enabled,
            onValueChange = { frequencyPreview = it },
            onValueChangeFinished = {
                onUpdate(band.copy(frequencyHz = frequencyPreview.roundToInt()))
            },
        )
        var gainPreview by remember(band.gainTenthsDb) { mutableFloatStateOf(band.gainTenthsDb.toFloat()) }
        SliderPreference(
            title = stringResource(Res.string.settings_peq_gain),
            value = gainPreview,
            valueRange = -240f..240f,
            steps = 479,
            valueText = formatTenthsDb(gainPreview.roundToInt()),
            enabled = enabled,
            onValueChange = { gainPreview = it },
            onValueChangeFinished = {
                onUpdate(band.copy(gainTenthsDb = gainPreview.roundToInt()))
            },
        )
        var qPreview by remember(band.qHundredths) { mutableFloatStateOf(band.qHundredths.toFloat()) }
        SliderPreference(
            title = stringResource(Res.string.settings_peq_q),
            value = qPreview,
            valueRange = 5f..2_400f,
            steps = 2_394,
            valueText = formatHundredths(qPreview.roundToInt()),
            enabled = enabled,
            onValueChange = { qPreview = it },
            onValueChangeFinished = {
                onUpdate(band.copy(qHundredths = qPreview.roundToInt()))
            },
        )
        BasicComponent(
            title = stringResource(Res.string.settings_peq_remove_band),
            summary = stringResource(Res.string.settings_peq_remove_band_summary, index + 1),
            enabled = enabled,
            onClick = onRemove,
            titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
        )
    }
}

private fun ParametricEqFilterType.titleResource() = when (this) {
    ParametricEqFilterType.Peak -> Res.string.settings_peq_type_peak
    ParametricEqFilterType.LowShelf -> Res.string.settings_peq_type_low_shelf
    ParametricEqFilterType.HighShelf -> Res.string.settings_peq_type_high_shelf
    ParametricEqFilterType.LowPass -> Res.string.settings_peq_type_low_pass
    ParametricEqFilterType.HighPass -> Res.string.settings_peq_type_high_pass
    ParametricEqFilterType.BandPass -> Res.string.settings_peq_type_band_pass
    ParametricEqFilterType.Notch -> Res.string.settings_peq_type_notch
}
