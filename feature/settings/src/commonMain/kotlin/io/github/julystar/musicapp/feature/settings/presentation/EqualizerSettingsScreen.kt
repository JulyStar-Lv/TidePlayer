package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.BUILT_IN_EQUALIZER_PRESETS
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.ParametricEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.applyPreset
import io.github.julystar.musicapp.core.domain.model.matchingPreset
import io.github.julystar.musicapp.core.domain.model.resetEqualizer
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import io.github.julystar.musicapp.core.presentation.components.DesignTabItem
import io.github.julystar.musicapp.core.presentation.components.DesignTabs
import io.github.julystar.musicapp.core.presentation.components.DesignTabsVariant
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EqualizerSettingsScreen(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val effects = state.settings.audioEffects
    val profile = effects.profile
    val capabilities = state.capabilities.audioDsp
    val equalizerEnabled = when (profile.equalizerMode) {
        EqualizerMode.Graphic -> profile.graphicEqualizer.enabled
        EqualizerMode.Parametric -> profile.parametricEqualizer.enabled
    }
    val selectedModeSupported = when (profile.equalizerMode) {
        EqualizerMode.Graphic -> capabilities.graphicEqualizer
        EqualizerMode.Parametric -> capabilities.parametricEqualizer
    }

    fun updateProfile(updated: AudioEffectProfile) {
        onAction(
            SettingsAction.SetAudioEffectSettings(
                effects.withAudioEffectProfile(updated),
            ),
        )
    }

    SettingsPageLayout(
        title = stringResource(Res.string.settings_equalizer_section),
        onBack = onBack,
        compactHorizontalPadding = 12.dp,
    ) {
        SettingsSection(title = stringResource(Res.string.settings_equalizer_section)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_equalizer_enabled),
                summary = stringResource(Res.string.settings_equalizer_enabled_summary),
                checked = equalizerEnabled,
                enabled = selectedModeSupported,
                onCheckedChange = { enabled ->
                    updateProfile(
                        when (profile.equalizerMode) {
                            EqualizerMode.Graphic -> profile.copy(
                                graphicEqualizer = profile.graphicEqualizer.copy(enabled = enabled),
                            )
                            EqualizerMode.Parametric -> profile.copy(
                                parametricEqualizer =
                                    profile.parametricEqualizer.copy(enabled = enabled),
                            )
                        },
                    )
                },
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val expanded = maxWidth >= 660.dp
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1.45f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        EqualizerResponseSection(state)
                        EqualizerEditorSection(
                            profile = profile,
                            enabled = equalizerEnabled && selectedModeSupported,
                            maxParametricBands = capabilities.maxParametricBands,
                            onUpdate = ::updateProfile,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        EqualizerModeSection(profile, capabilities, ::updateProfile)
                        EqualizerPresetSection(profile, capabilities.graphicEqualizer, ::updateProfile)
                        EqualizerAdvancedSection(state, profile, selectedModeSupported, ::updateProfile)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    EqualizerModeSection(profile, capabilities, ::updateProfile)
                    EqualizerPresetSection(profile, capabilities.graphicEqualizer, ::updateProfile)
                    EqualizerResponseSection(state)
                    EqualizerEditorSection(
                        profile = profile,
                        enabled = equalizerEnabled && selectedModeSupported,
                        maxParametricBands = capabilities.maxParametricBands,
                        onUpdate = ::updateProfile,
                    )
                    EqualizerAdvancedSection(state, profile, selectedModeSupported, ::updateProfile)
                }
            }
        }
    }
}

@Composable
private fun EqualizerModeSection(
    profile: AudioEffectProfile,
    capabilities: io.github.julystar.musicapp.core.domain.model.AudioDspCapabilities,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.settings_equalizer_mode)) {
        DesignTabs(
            items = listOf(
                DesignTabItem(
                    label = stringResource(Res.string.settings_equalizer_graphic),
                    enabled = capabilities.graphicEqualizer,
                ),
                DesignTabItem(
                    label = stringResource(Res.string.settings_equalizer_parametric),
                    enabled = capabilities.parametricEqualizer,
                ),
            ),
            selectedIndex = profile.equalizerMode.ordinal,
            onSelectedIndexChange = { index ->
                val mode = EqualizerMode.entries[index]
                onUpdate(
                    profile.copy(
                        equalizerMode = mode,
                        graphicEqualizer = profile.graphicEqualizer.copy(
                            enabled = mode == EqualizerMode.Graphic,
                        ),
                        parametricEqualizer = profile.parametricEqualizer.copy(
                            enabled = mode == EqualizerMode.Parametric,
                        ),
                    ),
                )
            },
            variant = DesignTabsVariant.Pill,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun EqualizerPresetSection(
    profile: AudioEffectProfile,
    supported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    if (profile.equalizerMode != EqualizerMode.Graphic) return
    val selectedPreset = profile.graphicEqualizer.matchingPreset()
    val choices = listOf(EqualizerPresetChoice(null, stringResource(Res.string.settings_dsp_preset_custom))) +
        BUILT_IN_EQUALIZER_PRESETS.map { preset ->
            EqualizerPresetChoice(preset.id, equalizerPresetName(preset.id).orEmpty())
        }
    val selected = choices.firstOrNull { it.id == selectedPreset?.id } ?: choices.first()
    SettingsSection(title = stringResource(Res.string.settings_eq_presets_section)) {
        SettingsSelectRow(
            label = stringResource(Res.string.settings_dsp_preset),
            selected = selected,
            options = choices,
            optionLabel = { choice -> choice.name },
            enabled = supported,
            onSelect = { choice ->
                val preset = BUILT_IN_EQUALIZER_PRESETS.firstOrNull { it.id == choice.id }
                if (preset != null) {
                    onUpdate(
                        profile.copy(
                            equalizerMode = EqualizerMode.Graphic,
                            graphicEqualizer = profile.graphicEqualizer.applyPreset(preset),
                            parametricEqualizer = profile.parametricEqualizer.copy(enabled = false),
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun EqualizerResponseSection(state: SettingsUiState) {
    SettingsSection(title = stringResource(Res.string.settings_eq_frequency_response)) {
        EqualizerFrequencyResponse(response = state.audioDspFrequencyResponse)
    }
}

@Composable
private fun EqualizerEditorSection(
    profile: AudioEffectProfile,
    enabled: Boolean,
    maxParametricBands: Int,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(
        title = stringResource(
            if (profile.equalizerMode == EqualizerMode.Graphic) {
                Res.string.settings_eq_editor_section
            } else {
                Res.string.settings_equalizer_parametric
            },
        ),
    ) {
        when (profile.equalizerMode) {
            EqualizerMode.Graphic -> GraphicEqualizerEditor(
                settings = profile.graphicEqualizer,
                enabled = enabled,
                onUpdate = { updated ->
                    onUpdate(profile.copy(graphicEqualizer = updated))
                },
            )
            EqualizerMode.Parametric -> ParametricEqualizerEditor(
                settings = profile.parametricEqualizer,
                enabled = enabled,
                maxBands = maxParametricBands,
                onUpdate = { updated ->
                    onUpdate(profile.copy(parametricEqualizer = updated))
                },
            )
        }
    }
}

@Composable
private fun EqualizerAdvancedSection(
    state: SettingsUiState,
    profile: AudioEffectProfile,
    supported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.settings_eq_advanced_section)) {
        when (profile.equalizerMode) {
            EqualizerMode.Graphic -> {
                val equalizer = profile.graphicEqualizer
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_eq_preamp),
                    value = equalizer.preampTenthsDb,
                    valueRange = -240..120,
                    valueText = formatTenthsDb(equalizer.preampTenthsDb),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { value ->
                        onUpdate(profile.copy(graphicEqualizer = equalizer.copy(preampTenthsDb = value)))
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_eq_q),
                    value = equalizer.qHundredths,
                    valueRange = 10..1_000,
                    valueText = formatHundredths(equalizer.qHundredths),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { value ->
                        onUpdate(profile.copy(graphicEqualizer = equalizer.copy(qHundredths = value)))
                    },
                )
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_eq_reset),
                    value = stringResource(Res.string.settings_eq_reset_summary),
                    enabled = supported,
                    onClick = {
                        onUpdate(profile.copy(graphicEqualizer = equalizer.resetEqualizer()))
                    },
                )
            }
            EqualizerMode.Parametric -> {
                val equalizer = profile.parametricEqualizer
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_eq_preamp),
                    value = equalizer.preampTenthsDb,
                    valueRange = -960..120,
                    valueText = formatTenthsDb(equalizer.preampTenthsDb),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { value ->
                        onUpdate(profile.copy(parametricEqualizer = equalizer.copy(preampTenthsDb = value)))
                    },
                )
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_peq_reset),
                    value = stringResource(Res.string.settings_peq_reset_summary),
                    enabled = supported,
                    onClick = {
                        onUpdate(
                            profile.copy(
                                parametricEqualizer = ParametricEqualizerSettings(
                                    enabled = equalizer.enabled,
                                ),
                            ),
                        )
                    },
                )
            }
        }
        if (state.settings.audioEffects.headroom.mode == HeadroomMode.Automatic) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_headroom_automatic_info),
                value = "${formatMeter(state.audioDspMeter.appliedHeadroomDb)} · " +
                    stringResource(Res.string.settings_eq_automatic_headroom_hint),
            )
        }
    }
}

private data class EqualizerPresetChoice(val id: String?, val name: String)
