package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.julystar.musicapp.core.presentation.components.AppSwitch
import io.github.julystar.musicapp.core.presentation.components.DesignChevron
import io.github.julystar.musicapp.core.presentation.components.DesignChevronDirection
import io.github.julystar.musicapp.core.presentation.components.DesignPreferenceRow
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    SettingsInfoRow(
        title = stringResource(Res.string.settings_peq_add_band),
        value = stringResource(
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
    DesignPreferenceRow(
        title = stringResource(Res.string.settings_peq_band, index + 1),
        summary = summary,
        enabled = enabled,
        onClick = { if (band.enabled) expanded = !expanded },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSwitch(
                    checked = band.enabled,
                    enabled = enabled,
                    onCheckedChange = { checked ->
                        if (!checked) expanded = false
                        onUpdate(band.copy(enabled = checked))
                    },
                )
                DesignChevron(
                    direction = DesignChevronDirection.Right,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                )
            }
        },
    )
    if (expanded && band.enabled) {
        SettingsSelectRow(
            label = stringResource(Res.string.settings_peq_filter_type),
            selected = band.type,
            options = ParametricEqFilterType.entries.toList(),
            optionLabel = { type -> stringResource(type.titleResource()) },
            enabled = enabled,
            onSelect = { type -> onUpdate(band.copy(type = type)) },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_frequency),
            value = band.frequencyHz,
            valueRange = 10..20_000,
            valueText = formatHz(band.frequencyHz),
            enabled = enabled,
            onValueChange = { value -> onUpdate(band.copy(frequencyHz = value)) },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_gain),
            value = band.gainTenthsDb,
            valueRange = -240..240,
            valueText = formatTenthsDb(band.gainTenthsDb),
            enabled = enabled,
            onValueChange = { value -> onUpdate(band.copy(gainTenthsDb = value)) },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_q),
            value = band.qHundredths,
            valueRange = 5..2_400,
            valueText = formatHundredths(band.qHundredths),
            enabled = enabled,
            onValueChange = { value -> onUpdate(band.copy(qHundredths = value)) },
        )
        SettingsDangerRow(
            title = stringResource(Res.string.settings_peq_remove_band),
            summary = stringResource(Res.string.settings_peq_remove_band_summary, index + 1),
            enabled = enabled,
            onClick = onRemove,
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
