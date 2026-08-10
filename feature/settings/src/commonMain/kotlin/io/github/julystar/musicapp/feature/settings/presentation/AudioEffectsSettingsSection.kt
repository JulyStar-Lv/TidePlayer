package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioEffectPreset
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import io.github.julystar.musicapp.core.domain.model.CompressorSettings
import io.github.julystar.musicapp.core.domain.model.DynamicEqSettings
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.GraphicEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.LoudnessSettings
import io.github.julystar.musicapp.core.domain.model.MAX_EQ_BAND_GAIN_DB
import io.github.julystar.musicapp.core.domain.model.MIN_EQ_BAND_GAIN_DB
import io.github.julystar.musicapp.core.domain.model.MoogFilterMode
import io.github.julystar.musicapp.core.domain.model.ParametricEqBand
import io.github.julystar.musicapp.core.domain.model.ParametricEqFilterType
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.SpeakerOutputMode
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import io.github.julystar.musicapp.core.domain.repository.AudioDspFrequencyResponse
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AudioEffectsSettingsSection(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val effects = state.settings.audioEffects
    val profile = effects.profile
    val capabilities = state.capabilities.audioDsp
    val effectsEnabled = effects.enabled
    val monoLabel = stringResource(Res.string.settings_dsp_mono)
    val stereoLabel = stringResource(Res.string.settings_dsp_stereo)

    fun updateProfile(updated: AudioEffectProfile) {
        onAction(
            SettingsAction.SetAudioEffectSettings(
                effects.withAudioEffectProfile(updated)
            )
        )
    }

    SettingsSection(title = stringResource(Res.string.settings_audio_effects_section)) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_audio_effects_enabled),
            summary = stringResource(Res.string.settings_audio_effects_enabled_summary),
            checked = effectsEnabled,
            onCheckedChange = {
                onAction(SettingsAction.SetAudioEffectSettings(effects.copy(enabled = it)))
            },
        )
        if (capabilities.resourceDependent) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_resource_dependent),
                value = stringResource(Res.string.settings_dsp_resource_dependent_summary),
            )
        }
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_supported_formats),
            value = capabilities.supportedChannelCounts
                .sorted()
                .joinToString { count ->
                    if (count == 1) monoLabel else stereoLabel
                },
        )
        val outputFormats = state.capabilities.audioPipeline.dspOutputSampleFormats
            .sortedBy(AudioSampleFormat::ordinal)
            .map { it.localizedName() }
            .joinToString()
        if (outputFormats.isNotEmpty()) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_output_pipeline),
                value = if (state.capabilities.audioPipeline.highResolutionDspOutput) {
                    outputFormats
                } else {
                    stringResource(Res.string.settings_dsp_output_pipeline_fallback, outputFormats)
                },
            )
        }
    }

    DspRuntimeSection(state)

    SettingsSection(title = stringResource(Res.string.settings_headroom_section)) {
        val headroom = effects.headroom
        SettingsSelectRow(
            label = stringResource(Res.string.settings_headroom_mode),
            selected = headroom.mode,
            options = HeadroomMode.entries.toList(),
            optionLabel = { it.localizedName() },
            enabled = true,
            onSelect = { mode ->
                onAction(
                    SettingsAction.SetAudioEffectSettings(
                        effects.copy(headroom = headroom.copy(mode = mode))
                    )
                )
            },
        )
        SettingsInfoRow(
            title = stringResource(Res.string.settings_headroom_automatic_info),
            value = stringResource(Res.string.settings_headroom_automatic_summary),
            enabled = headroom.mode == HeadroomMode.Automatic,
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_headroom_manual),
            value = headroom.manualTenthsDb,
            valueRange = -240..0,
            valueText = tenthsDb(headroom.manualTenthsDb),
            enabled = headroom.mode == HeadroomMode.Manual,
            onValueChange = { value ->
                onAction(
                    SettingsAction.SetAudioEffectSettings(
                        effects.copy(headroom = headroom.copy(manualTenthsDb = value))
                    )
                )
            },
        )
    }

    AudioEffectPresetSection(
        effects = effects,
        enabled = effectsEnabled,
        onUpdate = { onAction(SettingsAction.SetAudioEffectSettings(it)) },
    )

    SettingsSection(title = stringResource(Res.string.settings_equalizer_section)) {
        SettingsSelectRow(
            label = stringResource(Res.string.settings_equalizer_mode),
            selected = profile.equalizerMode,
            options = EqualizerMode.entries.toList(),
            optionLabel = { mode ->
                when (mode) {
                    EqualizerMode.Graphic ->
                        stringResource(Res.string.settings_equalizer_graphic)
                    EqualizerMode.Parametric ->
                        stringResource(Res.string.settings_equalizer_parametric)
                }
            },
            enabled = effectsEnabled,
            onSelect = { mode ->
                updateProfile(
                    profile.copy(
                        equalizerMode = mode,
                        graphicEqualizer =
                            profile.graphicEqualizer.copy(enabled = mode == EqualizerMode.Graphic),
                        parametricEqualizer =
                            profile.parametricEqualizer.copy(
                                enabled = mode == EqualizerMode.Parametric
                            ),
                    )
                )
            },
        )
        DspFrequencyResponseChart(state.audioDspFrequencyResponse)
        when (profile.equalizerMode) {
            EqualizerMode.Graphic -> GraphicEqualizerControls(
                settings = profile.graphicEqualizer,
                enabled = effectsEnabled && capabilities.graphicEqualizer,
                onUpdate = { updateProfile(profile.copy(graphicEqualizer = it)) },
            )
            EqualizerMode.Parametric -> ParametricEqualizerControls(
                profile = profile,
                enabled = effectsEnabled && capabilities.parametricEqualizer,
                maxBands = capabilities.maxParametricBands,
                onUpdate = ::updateProfile,
            )
        }
    }

    if (capabilities.toneControl) {
        SettingsSection(title = stringResource(Res.string.settings_tone_section)) {
            val tone = profile.tone
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_tone_enabled),
                checked = tone.enabled,
                enabled = effectsEnabled,
                onCheckedChange = {
                    updateProfile(profile.copy(tone = tone.copy(enabled = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_bass),
                summary = stringResource(Res.string.settings_bass_shelf_summary),
                value = tone.bassGainDb,
                valueRange = -24..24,
                valueText = db(tone.bassGainDb),
                enabled = effectsEnabled && tone.enabled,
                onValueChange = {
                    updateProfile(profile.copy(tone = tone.copy(bassGainDb = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_bass_frequency),
                value = tone.bassFrequencyHz,
                valueRange = 50..500,
                valueText = hz(tone.bassFrequencyHz),
                enabled = effectsEnabled && tone.enabled,
                onValueChange = {
                    updateProfile(profile.copy(tone = tone.copy(bassFrequencyHz = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_treble),
                summary = stringResource(Res.string.settings_treble_shelf_summary),
                value = tone.trebleGainDb,
                valueRange = -24..24,
                valueText = db(tone.trebleGainDb),
                enabled = effectsEnabled && tone.enabled,
                onValueChange = {
                    updateProfile(profile.copy(tone = tone.copy(trebleGainDb = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_treble_frequency),
                value = tone.trebleFrequencyHz / 100,
                valueRange = 20..160,
                valueText = hz(tone.trebleFrequencyHz),
                enabled = effectsEnabled && tone.enabled,
                onValueChange = {
                    updateProfile(
                        profile.copy(tone = tone.copy(trebleFrequencyHz = it * 100))
                    )
                },
            )
        }
    }

    DynamicsControls(
        profile = profile,
        settingsEnabled = effectsEnabled,
        compressorSupported = capabilities.compressor,
        loudnessSupported = capabilities.loudness,
        dynamicEqSupported = capabilities.dynamicEq,
        limiterSupported = capabilities.peakLimiter,
        truePeakSupported = capabilities.truePeakLimiter,
        onUpdate = ::updateProfile,
    )

    SoundFieldControls(
        profile = profile,
        settingsEnabled = effectsEnabled,
        monoBassSupported = capabilities.monoBass,
        widthSupported = capabilities.stereoWidth,
        crossfeedSupported = capabilities.crossfeed,
        surroundSupported = capabilities.surround360,
        panoramicSupported = capabilities.panoramic360,
        onUpdate = ::updateProfile,
    )

    if (capabilities.moogFilter) {
        MoogControls(
            profile = profile,
            settingsEnabled = effectsEnabled,
            onUpdate = ::updateProfile,
        )
    }

    OutputControls(
        profile = profile,
        settingsEnabled = effectsEnabled,
        speakerSupported = capabilities.speakerOutput,
        reverbSupported = capabilities.reverb,
        onUpdate = ::updateProfile,
    )
}

@Composable
private fun DspRuntimeSection(state: SettingsUiState) {
    val status = state.audioDspRuntimeStatus
    val sampleRate = status.sampleRate
    val channelCount = status.channelCount
    val sampleFormat = status.sampleFormat
    SettingsSection(title = stringResource(Res.string.settings_dsp_runtime_section)) {
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_runtime_status),
            value = status.state.localizedName(),
        )
        if (sampleRate != null && channelCount != null && sampleFormat != null) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_runtime_format),
                value = stringResource(
                    Res.string.settings_dsp_runtime_format_value,
                    sampleRate,
                    channelCount,
                    sampleFormat.localizedName(),
                ),
            )
        }
        status.bypassReason?.let { reason ->
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_bypass_reason),
                value = reason.localizedName(),
            )
        }
        status.lastErrorCode?.let { errorCode ->
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_error_code),
                value = errorCode.toString(),
            )
        }
        if (status.latencyFrames > 0) {
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_latency),
                value = stringResource(
                    Res.string.settings_dsp_latency_frames,
                    status.latencyFrames,
                ),
            )
        }
        if (status.state == AudioDspRuntimeState.Active) {
            val meter = state.audioDspMeter
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_meter_peaks),
                value = stringResource(
                    Res.string.settings_dsp_meter_peaks_value,
                    meterDb(meter.inputPeakDb),
                    meterDb(meter.outputPeakDb),
                ),
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_meter_reduction),
                value = stringResource(
                    Res.string.settings_dsp_meter_reduction_value,
                    meterDb(meter.compressorGainReductionDb),
                    meterDb(meter.limiterGainReductionDb),
                    meterDb(meter.appliedHeadroomDb),
                ),
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_dsp_meter_recovery),
                value = stringResource(
                    Res.string.settings_dsp_meter_recovery_value,
                    meter.clippedSamples,
                    meter.nonFiniteRecoveryCount,
                ),
            )
            val performance = state.audioDspPerformance
            if (performance.processCount > 0) {
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_performance),
                    value = stringResource(
                        Res.string.settings_dsp_performance_value,
                        meterNumber(performance.averageProcessingTimeUs),
                        meterNumber(performance.maxProcessingTimeUs),
                        meterNumber(performance.deadlineUtilization * 100f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DspFrequencyResponseChart(response: AudioDspFrequencyResponse) {
    val curveColor = MiuixTheme.colorScheme.primary
    val guideColor = MiuixTheme.colorScheme.dividerLine
    Column {
        SettingsInfoRow(
            title = stringResource(Res.string.settings_eq_frequency_response),
            value = if (response.gainsDb.isEmpty()) {
                stringResource(Res.string.settings_eq_frequency_response_unavailable)
            } else {
                stringResource(Res.string.settings_eq_frequency_response_summary)
            },
        )
        if (response.gainsDb.size > 1) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val centerY = size.height / 2f
                drawLine(
                    color = guideColor,
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                )
                val path = Path()
                response.gainsDb.forEachIndexed { index, gain ->
                    val x = size.width * index / (response.gainsDb.lastIndex.toFloat())
                    val y = size.height * (24f - gain.coerceIn(-24f, 24f)) / 48f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = curveColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun AudioEffectPresetSection(
    effects: AudioEffectSettings,
    enabled: Boolean,
    onUpdate: (AudioEffectSettings) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val builtIns = builtInPresets()
    val choices = listOf(
        PresetChoice("custom", stringResource(Res.string.settings_dsp_preset_custom), null)
    ) + builtIns + effects.userPresets.map { preset ->
        PresetChoice(preset.id, preset.name, preset.profile)
    }
    val selected = choices.firstOrNull { it.profile == effects.profile } ?: choices.first()

    SettingsSection(title = stringResource(Res.string.settings_dsp_presets_section)) {
        SettingsSelectRow(
            label = stringResource(Res.string.settings_dsp_preset),
            selected = selected,
            options = choices,
            optionLabel = { it.name },
            enabled = enabled,
            onSelect = { choice ->
                choice.profile?.let { profile ->
                    onUpdate(effects.withAudioEffectProfile(profile))
                }
            },
        )
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_save_preset),
            value = stringResource(Res.string.settings_dsp_save_preset_summary),
            enabled = enabled,
            onClick = {
                presetName = ""
                showSaveDialog = true
            },
        )
        if (effects.userPresets.isNotEmpty()) {
            SettingsSelectRow(
                label = stringResource(Res.string.settings_dsp_delete_preset),
                selected = effects.userPresets.first(),
                options = effects.userPresets,
                optionLabel = { it.name },
                enabled = enabled,
                onSelect = { preset ->
                    onUpdate(
                        effects.copy(
                            userPresets = effects.userPresets.filterNot { it.id == preset.id }
                        )
                    )
                },
            )
        }
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_restore_defaults),
            value = stringResource(Res.string.settings_dsp_restore_defaults_summary),
            enabled = enabled,
            onClick = {
                onUpdate(
                    AudioEffectSettings.Default.copy(
                        enabled = effects.enabled,
                        userPresets = effects.userPresets,
                    )
                )
            },
        )
    }

    SettingsInputDialog(
        show = showSaveDialog,
        title = stringResource(Res.string.settings_dsp_save_preset),
        message = stringResource(Res.string.settings_dsp_save_preset_message),
        value = presetName,
        label = stringResource(Res.string.settings_dsp_preset_name),
        onValueChange = { presetName = it.take(128) },
        onConfirm = {
            val name = presetName.trim()
            if (name.isNotEmpty()) {
                val id = nextPresetId(effects.userPresets, name)
                onUpdate(
                    effects.copy(
                        userPresets = effects.userPresets +
                            AudioEffectPreset(id = id, name = name, profile = effects.profile)
                    )
                )
                showSaveDialog = false
            }
        },
        onDismiss = { showSaveDialog = false },
    )
}

@Composable
private fun GraphicEqualizerControls(
    settings: GraphicEqualizerSettings,
    enabled: Boolean,
    onUpdate: (GraphicEqualizerSettings) -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_equalizer_graphic),
        checked = settings.enabled,
        enabled = enabled,
        onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_eq_preamp),
        value = settings.preampTenthsDb,
        valueRange = -240..120,
        valueText = tenthsDb(settings.preampTenthsDb),
        enabled = enabled && settings.enabled,
        onValueChange = { onUpdate(settings.copy(preampTenthsDb = it)) },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_eq_q),
        value = settings.qHundredths,
        valueRange = 10..1_000,
        valueText = hundredths(settings.qHundredths),
        enabled = enabled && settings.enabled,
        onValueChange = { onUpdate(settings.copy(qHundredths = it)) },
    )
    settings.bandGainsDb.forEachIndexed { index, gain ->
        SettingsSliderRow(
            title = stringResource(Res.string.settings_eq_band, EQ_BAND_LABELS[index]),
            value = gain,
            valueRange = MIN_EQ_BAND_GAIN_DB..MAX_EQ_BAND_GAIN_DB,
            valueText = db(gain),
            enabled = enabled && settings.enabled,
            onValueChange = { updatedGain ->
                onUpdate(
                    settings.copy(
                        bandGainsDb = settings.bandGainsDb.toMutableList().apply {
                            this[index] = updatedGain
                        }
                    )
                )
            },
        )
    }
    SettingsInfoRow(
        title = stringResource(Res.string.settings_eq_reset),
        value = stringResource(Res.string.settings_eq_reset_summary),
        enabled = enabled,
        onClick = { onUpdate(GraphicEqualizerSettings()) },
    )
}

@Composable
private fun ParametricEqualizerControls(
    profile: AudioEffectProfile,
    enabled: Boolean,
    maxBands: Int,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val equalizer = profile.parametricEqualizer
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_equalizer_parametric),
        checked = equalizer.enabled,
        enabled = enabled,
        onCheckedChange = {
            onUpdate(profile.copy(parametricEqualizer = equalizer.copy(enabled = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_eq_preamp),
        value = equalizer.preampTenthsDb,
        valueRange = -960..120,
        valueText = tenthsDb(equalizer.preampTenthsDb),
        enabled = enabled && equalizer.enabled,
        onValueChange = {
            onUpdate(profile.copy(parametricEqualizer = equalizer.copy(preampTenthsDb = it)))
        },
    )
    equalizer.bands.forEachIndexed { index, band ->
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_peq_band, index + 1),
            checked = band.enabled,
            enabled = enabled && equalizer.enabled,
            onCheckedChange = { active ->
                updatePeqBand(profile, index, band.copy(enabled = active), onUpdate)
            },
        )
        SettingsSelectRow(
            label = stringResource(Res.string.settings_peq_filter_type),
            selected = band.type,
            options = ParametricEqFilterType.entries.toList(),
            optionLabel = { type -> stringResource(type.titleResource()) },
            enabled = enabled && equalizer.enabled && band.enabled,
            onSelect = { type -> updatePeqBand(profile, index, band.copy(type = type), onUpdate) },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_frequency),
            value = band.frequencyHz,
            valueRange = 10..20_000,
            valueText = hz(band.frequencyHz),
            enabled = enabled && equalizer.enabled && band.enabled,
            onValueChange = {
                updatePeqBand(profile, index, band.copy(frequencyHz = it), onUpdate)
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_gain),
            value = band.gainTenthsDb,
            valueRange = -240..240,
            valueText = tenthsDb(band.gainTenthsDb),
            enabled = enabled && equalizer.enabled && band.enabled,
            onValueChange = {
                updatePeqBand(profile, index, band.copy(gainTenthsDb = it), onUpdate)
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_peq_q),
            value = band.qHundredths,
            valueRange = 5..2_400,
            valueText = hundredths(band.qHundredths),
            enabled = enabled && equalizer.enabled && band.enabled,
            onValueChange = {
                updatePeqBand(profile, index, band.copy(qHundredths = it), onUpdate)
            },
        )
        SettingsInfoRow(
            title = stringResource(Res.string.settings_peq_remove_band),
            value = stringResource(Res.string.settings_peq_remove_band_summary, index + 1),
            enabled = enabled && equalizer.enabled,
            onClick = {
                onUpdate(
                    profile.copy(
                        parametricEqualizer = equalizer.copy(
                            bands = equalizer.bands.filterIndexed { itemIndex, _ ->
                                itemIndex != index
                            }
                        )
                    )
                )
            },
        )
    }
    SettingsInfoRow(
        title = stringResource(Res.string.settings_peq_add_band),
        value = stringResource(
            Res.string.settings_peq_band_count,
            equalizer.bands.size,
            maxBands,
        ),
        enabled = enabled && equalizer.enabled && equalizer.bands.size < maxBands,
        onClick = {
            onUpdate(
                profile.copy(
                    parametricEqualizer = equalizer.copy(
                        bands = equalizer.bands + ParametricEqBand()
                    )
                )
            )
        },
    )
}

@Composable
private fun DynamicsControls(
    profile: AudioEffectProfile,
    settingsEnabled: Boolean,
    compressorSupported: Boolean,
    loudnessSupported: Boolean,
    dynamicEqSupported: Boolean,
    limiterSupported: Boolean,
    truePeakSupported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.settings_dynamics_section)) {
        if (compressorSupported) {
            val compressor = profile.compressor
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_compressor),
                checked = compressor.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(compressor = compressor.copy(enabled = it)))
                },
            )
            compressorSliders(profile, compressor, settingsEnabled, onUpdate)
        }
        if (loudnessSupported) {
            val loudness = profile.loudness
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_loudness),
                summary = stringResource(Res.string.settings_loudness_summary),
                checked = loudness.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(loudness = loudness.copy(enabled = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_loudness_amount),
                value = loudness.amountPercent,
                valueRange = 0..100,
                valueText = percent(loudness.amountPercent),
                enabled = settingsEnabled && loudness.enabled,
                onValueChange = {
                    onUpdate(profile.copy(loudness = loudness.copy(amountPercent = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_loudness_balance),
                value = loudness.balancePercent,
                valueRange = -100..100,
                valueText = percent(loudness.balancePercent),
                enabled = settingsEnabled && loudness.enabled,
                onValueChange = {
                    onUpdate(profile.copy(loudness = loudness.copy(balancePercent = it)))
                },
            )
        }
        if (dynamicEqSupported) {
            val dynamicEq = profile.dynamicEq
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_dynamic_eq),
                checked = dynamicEq.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(dynamicEq = dynamicEq.copy(enabled = it)))
                },
            )
            dynamicEqSliders(profile, dynamicEq, settingsEnabled, onUpdate)
        }
        if (limiterSupported) {
            val limiter = profile.limiter
            SettingsSwitchRow(
                title = if (limiter.truePeakEnabled) {
                    stringResource(Res.string.settings_true_peak_limiter)
                } else {
                    stringResource(Res.string.settings_peak_limiter)
                },
                summary = if (limiter.truePeakEnabled) {
                    stringResource(Res.string.settings_true_peak_limiter_summary)
                } else {
                    stringResource(Res.string.settings_peak_limiter_summary)
                },
                checked = limiter.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(limiter = limiter.copy(enabled = it)))
                },
            )
            if (truePeakSupported) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_true_peak_mode),
                    summary = stringResource(Res.string.settings_true_peak_mode_summary),
                    checked = limiter.truePeakEnabled,
                    enabled = settingsEnabled && limiter.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate(
                            profile.copy(
                                limiter = limiter.copy(
                                    truePeakEnabled = enabled,
                                    oversampling = if (enabled) 4 else 1,
                                    ceilingTenthsDb = if (
                                        enabled && limiter.ceilingTenthsDb == -5
                                    ) {
                                        -10
                                    } else {
                                        limiter.ceilingTenthsDb
                                    },
                                )
                            )
                        )
                    },
                )
            }
            SettingsSliderRow(
                title = stringResource(Res.string.settings_limiter_ceiling),
                value = limiter.ceilingTenthsDb,
                valueRange = -120..0,
                valueText = tenthsDb(limiter.ceilingTenthsDb),
                enabled = settingsEnabled && limiter.enabled,
                onValueChange = {
                    onUpdate(profile.copy(limiter = limiter.copy(ceilingTenthsDb = it)))
                },
            )
            if (!limiter.truePeakEnabled) {
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_limiter_attack),
                    value = limiter.attackHundredthsMs,
                    valueRange = 1..2_000,
                    valueText = hundredthsMs(limiter.attackHundredthsMs),
                    enabled = settingsEnabled && limiter.enabled,
                    onValueChange = {
                        onUpdate(profile.copy(limiter = limiter.copy(attackHundredthsMs = it)))
                    },
                )
            }
            if (truePeakSupported && limiter.truePeakEnabled) {
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_limiter_lookahead),
                    value = limiter.lookaheadMs,
                    valueRange = 1..10,
                    valueText = milliseconds(limiter.lookaheadMs),
                    enabled = settingsEnabled && limiter.enabled,
                    onValueChange = {
                        onUpdate(profile.copy(limiter = limiter.copy(lookaheadMs = it)))
                    },
                )
            }
            SettingsSliderRow(
                title = stringResource(Res.string.settings_limiter_release),
                value = limiter.releaseMs,
                valueRange = 5..2_000,
                valueText = milliseconds(limiter.releaseMs),
                enabled = settingsEnabled && limiter.enabled,
                onValueChange = {
                    onUpdate(profile.copy(limiter = limiter.copy(releaseMs = it)))
                },
            )
        }
    }
}

@Composable
private fun compressorSliders(
    profile: AudioEffectProfile,
    compressor: CompressorSettings,
    settingsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val enabled = settingsEnabled && compressor.enabled
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_threshold),
        value = compressor.thresholdDb,
        valueRange = -60..0,
        valueText = db(compressor.thresholdDb),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(thresholdDb = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_ratio),
        value = compressor.ratio,
        valueRange = 1..30,
        valueText = "${compressor.ratio}:1",
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(ratio = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_attack),
        value = compressor.attackMs,
        valueRange = 1..500,
        valueText = milliseconds(compressor.attackMs),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(attackMs = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_release),
        value = compressor.releaseMs,
        valueRange = 5..5_000,
        valueText = milliseconds(compressor.releaseMs),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(releaseMs = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_makeup),
        value = compressor.makeupGainDb,
        valueRange = -12..24,
        valueText = db(compressor.makeupGainDb),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(makeupGainDb = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_compressor_knee),
        value = compressor.kneeDb,
        valueRange = 0..24,
        valueText = db(compressor.kneeDb),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(compressor = compressor.copy(kneeDb = it)))
        },
    )
}

@Composable
private fun dynamicEqSliders(
    profile: AudioEffectProfile,
    dynamicEq: DynamicEqSettings,
    settingsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val enabled = settingsEnabled && dynamicEq.enabled
    SettingsSliderRow(
        title = stringResource(Res.string.settings_dynamic_eq_amount),
        value = dynamicEq.amountPercent,
        valueRange = 0..100,
        valueText = percent(dynamicEq.amountPercent),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(dynamicEq = dynamicEq.copy(amountPercent = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_de_esser_amount),
        value = dynamicEq.deEsserAmountPercent,
        valueRange = 0..100,
        valueText = percent(dynamicEq.deEsserAmountPercent),
        enabled = enabled,
        onValueChange = {
            onUpdate(profile.copy(dynamicEq = dynamicEq.copy(deEsserAmountPercent = it)))
        },
    )
    SettingsSliderRow(
        title = stringResource(Res.string.settings_de_esser_frequency),
        value = dynamicEq.deEsserFrequencyHz / 100,
        valueRange = 40..100,
        valueText = hz(dynamicEq.deEsserFrequencyHz),
        enabled = enabled,
        onValueChange = {
            onUpdate(
                profile.copy(dynamicEq = dynamicEq.copy(deEsserFrequencyHz = it * 100))
            )
        },
    )
}

@Composable
private fun SoundFieldControls(
    profile: AudioEffectProfile,
    settingsEnabled: Boolean,
    monoBassSupported: Boolean,
    widthSupported: Boolean,
    crossfeedSupported: Boolean,
    surroundSupported: Boolean,
    panoramicSupported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.settings_sound_field_section)) {
        if (monoBassSupported) {
            val monoBass = profile.monoBass
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_mono_bass),
                summary = stringResource(Res.string.settings_mono_bass_summary),
                checked = monoBass.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(monoBass = monoBass.copy(enabled = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_mono_bass_crossover),
                value = monoBass.crossoverHz,
                valueRange = 60..300,
                valueText = hz(monoBass.crossoverHz),
                enabled = settingsEnabled && monoBass.enabled,
                onValueChange = {
                    onUpdate(profile.copy(monoBass = monoBass.copy(crossoverHz = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_mono_bass_amount),
                value = monoBass.amountPercent,
                valueRange = 0..100,
                valueText = percent(monoBass.amountPercent),
                enabled = settingsEnabled && monoBass.enabled,
                onValueChange = {
                    onUpdate(profile.copy(monoBass = monoBass.copy(amountPercent = it)))
                },
            )
        }

        val availableModes = SpatialAudioMode.entries.filter { mode ->
            when (mode) {
                SpatialAudioMode.None -> true
                SpatialAudioMode.CrossfeedAndWidth -> widthSupported || crossfeedSupported
                SpatialAudioMode.Surround360 -> surroundSupported
                SpatialAudioMode.Panoramic360 -> panoramicSupported
            }
        }
        val spatial = profile.spatialAudio
        SettingsSelectRow(
            label = stringResource(Res.string.settings_spatial_mode),
            subtitle = stringResource(Res.string.settings_spatial_mode_summary),
            selected = spatial.mode,
            options = availableModes,
            optionLabel = { mode -> stringResource(mode.titleResource()) },
            enabled = settingsEnabled,
            onSelect = { mode ->
                onUpdate(profile.copy(spatialAudio = spatial.copy(mode = mode)))
            },
        )
        if (spatial.mode == SpatialAudioMode.CrossfeedAndWidth) {
            if (widthSupported) {
                val width = profile.stereoWidth
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_stereo_width),
                    checked = width.enabled,
                    enabled = settingsEnabled,
                    onCheckedChange = {
                        onUpdate(profile.copy(stereoWidth = width.copy(enabled = it)))
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_stereo_width),
                    value = width.widthPercent,
                    valueRange = 0..200,
                    valueText = percent(width.widthPercent),
                    enabled = settingsEnabled && width.enabled,
                    onValueChange = {
                        onUpdate(profile.copy(stereoWidth = width.copy(widthPercent = it)))
                    },
                )
            }
            if (crossfeedSupported) {
                val crossfeed = profile.crossfeed
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_crossfeed),
                    checked = crossfeed.enabled,
                    enabled = settingsEnabled,
                    onCheckedChange = {
                        onUpdate(profile.copy(crossfeed = crossfeed.copy(enabled = it)))
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_crossfeed_low_cut),
                    value = crossfeed.lowCutHz,
                    valueRange = 50..1_000,
                    valueText = hz(crossfeed.lowCutHz),
                    enabled = settingsEnabled && crossfeed.enabled,
                    onValueChange = {
                        onUpdate(profile.copy(crossfeed = crossfeed.copy(lowCutHz = it)))
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_crossfeed_high_cut),
                    value = crossfeed.highCutHz / 10,
                    valueRange = 50..800,
                    valueText = hz(crossfeed.highCutHz),
                    enabled = settingsEnabled && crossfeed.enabled,
                    onValueChange = {
                        onUpdate(profile.copy(crossfeed = crossfeed.copy(highCutHz = it * 10)))
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_crossfeed_attenuation),
                    value = crossfeed.attenuationTenthsDb,
                    valueRange = 0..150,
                    valueText = tenthsDb(-crossfeed.attenuationTenthsDb),
                    enabled = settingsEnabled && crossfeed.enabled,
                    onValueChange = {
                        onUpdate(
                            profile.copy(
                                crossfeed = crossfeed.copy(attenuationTenthsDb = it)
                            )
                        )
                    },
                )
            }
        } else if (spatial.mode != SpatialAudioMode.None) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_spatial_intensity),
                value = spatial.intensityPercent,
                valueRange = 0..100,
                valueText = percent(spatial.intensityPercent),
                enabled = settingsEnabled,
                onValueChange = {
                    onUpdate(profile.copy(spatialAudio = spatial.copy(intensityPercent = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_spatial_azimuth),
                value = spatial.azimuthDegrees,
                valueRange = 0..359,
                valueText = degrees(spatial.azimuthDegrees),
                enabled = settingsEnabled,
                onValueChange = {
                    onUpdate(profile.copy(spatialAudio = spatial.copy(azimuthDegrees = it)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_spatial_auto_rotate),
                value = spatial.autoRotateDegreesPerSecond,
                valueRange = -180..180,
                valueText = "${spatial.autoRotateDegreesPerSecond}°/s",
                enabled = settingsEnabled,
                onValueChange = {
                    onUpdate(
                        profile.copy(
                            spatialAudio = spatial.copy(autoRotateDegreesPerSecond = it)
                        )
                    )
                },
            )
            if (spatial.mode == SpatialAudioMode.Panoramic360) {
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_spatial_elevation),
                    value = spatial.elevationDegrees,
                    valueRange = -90..90,
                    valueText = degrees(spatial.elevationDegrees),
                    enabled = settingsEnabled,
                    onValueChange = {
                        onUpdate(
                            profile.copy(spatialAudio = spatial.copy(elevationDegrees = it))
                        )
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_spatial_room),
                    value = spatial.roomAmountPercent,
                    valueRange = 0..100,
                    valueText = percent(spatial.roomAmountPercent),
                    enabled = settingsEnabled,
                    onValueChange = {
                        onUpdate(
                            profile.copy(spatialAudio = spatial.copy(roomAmountPercent = it))
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MoogControls(
    profile: AudioEffectProfile,
    settingsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val moog = profile.moogFilter
    SettingsSection(title = stringResource(Res.string.settings_moog_section)) {
        SettingsSwitchRow(
            title = stringResource(Res.string.settings_moog_enabled),
            checked = moog.enabled,
            enabled = settingsEnabled,
            onCheckedChange = {
                onUpdate(profile.copy(moogFilter = moog.copy(enabled = it)))
            },
        )
        SettingsSelectRow(
            label = stringResource(Res.string.settings_moog_mode),
            selected = moog.mode,
            options = MoogFilterMode.entries.toList(),
            optionLabel = { mode -> stringResource(mode.titleResource()) },
            enabled = settingsEnabled && moog.enabled,
            onSelect = { mode ->
                onUpdate(profile.copy(moogFilter = moog.copy(mode = mode)))
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_moog_cutoff),
            value = moog.cutoffHz,
            valueRange = 20..20_000,
            valueText = hz(moog.cutoffHz),
            enabled = settingsEnabled && moog.enabled,
            onValueChange = {
                onUpdate(profile.copy(moogFilter = moog.copy(cutoffHz = it)))
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_moog_resonance),
            value = moog.resonancePercent,
            valueRange = 0..100,
            valueText = percent(moog.resonancePercent),
            enabled = settingsEnabled && moog.enabled,
            onValueChange = {
                onUpdate(profile.copy(moogFilter = moog.copy(resonancePercent = it)))
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_moog_drive),
            value = moog.driveTenthsDb,
            valueRange = 0..180,
            valueText = tenthsDb(moog.driveTenthsDb),
            enabled = settingsEnabled && moog.enabled,
            onValueChange = {
                onUpdate(profile.copy(moogFilter = moog.copy(driveTenthsDb = it)))
            },
        )
        SettingsSliderRow(
            title = stringResource(Res.string.settings_moog_mix),
            value = moog.mixPercent,
            valueRange = 0..100,
            valueText = percent(moog.mixPercent),
            enabled = settingsEnabled && moog.enabled,
            onValueChange = {
                onUpdate(profile.copy(moogFilter = moog.copy(mixPercent = it)))
            },
        )
    }
}

@Composable
private fun OutputControls(
    profile: AudioEffectProfile,
    settingsEnabled: Boolean,
    speakerSupported: Boolean,
    reverbSupported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.settings_output_effects_section)) {
        if (speakerSupported) {
            val speaker = profile.speakerOutput
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_speaker_output),
                checked = speaker.enabled,
                enabled = settingsEnabled,
                onCheckedChange = {
                    onUpdate(profile.copy(speakerOutput = speaker.copy(enabled = it)))
                },
            )
            SettingsSelectRow(
                label = stringResource(Res.string.settings_speaker_mode),
                selected = speaker.mode,
                options = SpeakerOutputMode.entries.toList(),
                optionLabel = { mode -> stringResource(mode.titleResource()) },
                enabled = settingsEnabled && speaker.enabled,
                onSelect = { mode ->
                    onUpdate(profile.copy(speakerOutput = speaker.copy(mode = mode)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_speaker_strength),
                value = speaker.strengthPercent,
                valueRange = 0..100,
                valueText = percent(speaker.strengthPercent),
                enabled = settingsEnabled && speaker.enabled,
                onValueChange = {
                    onUpdate(
                        profile.copy(speakerOutput = speaker.copy(strengthPercent = it))
                    )
                },
            )
        }
        if (reverbSupported) {
            val reverb = profile.reverb
            SettingsSelectRow(
                label = stringResource(Res.string.settings_reverb),
                selected = reverb.preset,
                options = ReverbPreset.entries.toList(),
                optionLabel = { preset -> stringResource(preset.titleResource()) },
                enabled = settingsEnabled,
                onSelect = { preset ->
                    onUpdate(profile.copy(reverb = reverb.copy(preset = preset)))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_reverb_wet),
                value = reverb.wetPercent,
                valueRange = 0..50,
                valueText = percent(reverb.wetPercent),
                enabled = settingsEnabled && reverb.preset != ReverbPreset.None,
                onValueChange = {
                    onUpdate(profile.copy(reverb = reverb.copy(wetPercent = it)))
                },
            )
        }
    }
}

private fun updatePeqBand(
    profile: AudioEffectProfile,
    index: Int,
    band: ParametricEqBand,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val equalizer = profile.parametricEqualizer
    val bands = equalizer.bands.toMutableList()
    bands[index] = band
    onUpdate(profile.copy(parametricEqualizer = equalizer.copy(bands = bands)))
}

private data class PresetChoice(
    val id: String,
    val name: String,
    val profile: AudioEffectProfile?,
)

@Composable
private fun builtInPresets(): List<PresetChoice> {
    return listOf(
        PresetChoice(
            id = "builtin:flat",
            name = stringResource(Res.string.settings_dsp_preset_flat),
            profile = AudioEffectProfile.Default,
        ),
        PresetChoice(
            id = "builtin:bass",
            name = stringResource(Res.string.settings_dsp_preset_bass),
            profile = AudioEffectProfile.Default.copy(
                graphicEqualizer = GraphicEqualizerSettings(
                    bandGainsDb = listOf(5, 5, 4, 2, 0, 0, 0, 0, 0, 0)
                ),
            ),
        ),
        PresetChoice(
            id = "builtin:vocal",
            name = stringResource(Res.string.settings_dsp_preset_vocal),
            profile = AudioEffectProfile.Default.copy(
                graphicEqualizer = GraphicEqualizerSettings(
                    bandGainsDb = listOf(-2, -1, 0, 1, 2, 3, 3, 2, 0, -1)
                ),
                compressor = CompressorSettings(
                    enabled = true,
                    thresholdDb = -20,
                    ratio = 3,
                ),
            ),
        ),
        PresetChoice(
            id = "builtin:night",
            name = stringResource(Res.string.settings_dsp_preset_night),
            profile = AudioEffectProfile.Default.copy(
                loudness = LoudnessSettings(enabled = true, amountPercent = 35),
                compressor = CompressorSettings(
                    enabled = true,
                    thresholdDb = -24,
                    ratio = 4,
                ),
            ),
        ),
    )
}

private fun nextPresetId(existing: List<AudioEffectPreset>, name: String): String {
    val prefix = "user:${name.hashCode().toUInt().toString(16)}"
    var suffix = 1
    var candidate = prefix
    val ids = existing.mapTo(mutableSetOf(), AudioEffectPreset::id)
    while (candidate in ids) {
        candidate = "$prefix:$suffix"
        suffix += 1
    }
    return candidate
}

private val EQ_BAND_LABELS =
    listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")

private fun db(value: Int): String = "$value dB"
private fun tenthsDb(value: Int): String =
    "${if (value > 0) "+" else ""}${value / 10}.${kotlin.math.abs(value % 10)} dB"
private fun hundredths(value: Int): String =
    "${value / 100}.${kotlin.math.abs(value % 100).toString().padStart(2, '0')}"
private fun hundredthsMs(value: Int): String = "${hundredths(value)} ms"
private fun milliseconds(value: Int): String = "$value ms"
private fun percent(value: Int): String = "$value%"
private fun hz(value: Int): String = if (value >= 1_000) {
    val tenths = value / 100
    "${tenths / 10}.${tenths % 10} kHz"
} else {
    "$value Hz"
}
private fun degrees(value: Int): String = "$value°"

private fun meterNumber(value: Float): String =
    (kotlin.math.round(value * 10f) / 10f).toString()

private fun meterDb(value: Float): String = "${meterNumber(value)} dB"

@Composable
private fun AudioSampleFormat.localizedName(): String = when (this) {
    AudioSampleFormat.Pcm16 -> stringResource(Res.string.settings_dsp_format_pcm16)
    AudioSampleFormat.Float32 -> stringResource(Res.string.settings_dsp_format_float32)
}

@Composable
private fun AudioDspRuntimeState.localizedName(): String = when (this) {
    AudioDspRuntimeState.Inactive -> stringResource(Res.string.settings_dsp_status_inactive)
    AudioDspRuntimeState.Active -> stringResource(Res.string.settings_dsp_status_active)
    AudioDspRuntimeState.Bypassed -> stringResource(Res.string.settings_dsp_status_bypassed)
    AudioDspRuntimeState.Unavailable -> stringResource(Res.string.settings_dsp_status_unavailable)
    AudioDspRuntimeState.Error -> stringResource(Res.string.settings_dsp_status_error)
}

@Composable
private fun AudioDspBypassReason.localizedName(): String = when (this) {
    AudioDspBypassReason.EffectsDisabled ->
        stringResource(Res.string.settings_dsp_reason_effects_disabled)
    AudioDspBypassReason.UnsupportedSampleFormat ->
        stringResource(Res.string.settings_dsp_reason_sample_format)
    AudioDspBypassReason.UnsupportedChannelCount ->
        stringResource(Res.string.settings_dsp_reason_channel_count)
    AudioDspBypassReason.UnsupportedSampleRate ->
        stringResource(Res.string.settings_dsp_reason_sample_rate)
    AudioDspBypassReason.PlatformProcessingUnavailable ->
        stringResource(Res.string.settings_dsp_reason_platform_unavailable)
    AudioDspBypassReason.ProtectedContent ->
        stringResource(Res.string.settings_dsp_reason_protected_content)
    AudioDspBypassReason.AudioTapUnavailable ->
        stringResource(Res.string.settings_dsp_reason_audio_tap)
    AudioDspBypassReason.OutputRouteUnavailable ->
        stringResource(Res.string.settings_dsp_reason_output_route)
    AudioDspBypassReason.NativeProcessingError ->
        stringResource(Res.string.settings_dsp_reason_native_error)
}

@Composable
private fun HeadroomMode.localizedName(): String = when (this) {
    HeadroomMode.Off -> stringResource(Res.string.settings_headroom_off)
    HeadroomMode.Automatic -> stringResource(Res.string.settings_headroom_automatic)
    HeadroomMode.Manual -> stringResource(Res.string.settings_headroom_manual)
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

private fun SpatialAudioMode.titleResource() = when (this) {
    SpatialAudioMode.None -> Res.string.settings_spatial_none
    SpatialAudioMode.CrossfeedAndWidth -> Res.string.settings_spatial_crossfeed_width
    SpatialAudioMode.Surround360 -> Res.string.settings_spatial_surround
    SpatialAudioMode.Panoramic360 -> Res.string.settings_spatial_panoramic
}

private fun MoogFilterMode.titleResource() = when (this) {
    MoogFilterMode.LowPass24 -> Res.string.settings_moog_low_pass_24
    MoogFilterMode.LowPass12 -> Res.string.settings_moog_low_pass_12
    MoogFilterMode.HighPass24 -> Res.string.settings_moog_high_pass_24
    MoogFilterMode.BandPass12 -> Res.string.settings_moog_band_pass_12
    MoogFilterMode.Notch -> Res.string.settings_moog_notch
}

private fun SpeakerOutputMode.titleResource() = when (this) {
    SpeakerOutputMode.Elasticity -> Res.string.settings_speaker_elasticity
    SpeakerOutputMode.Powerful -> Res.string.settings_speaker_powerful
    SpeakerOutputMode.Wide -> Res.string.settings_speaker_wide
}

private fun ReverbPreset.titleResource() = when (this) {
    ReverbPreset.None -> Res.string.settings_reverb_none
    ReverbPreset.SmallRoom -> Res.string.settings_reverb_small_room
    ReverbPreset.MediumRoom -> Res.string.settings_reverb_medium_room
    ReverbPreset.LargeRoom -> Res.string.settings_reverb_large_room
    ReverbPreset.Hall -> Res.string.settings_reverb_hall
    ReverbPreset.Plate -> Res.string.settings_reverb_plate
}
