package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioEffectPreset
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import io.github.julystar.musicapp.core.domain.model.CompressorSettings
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.LoudnessSettings
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import io.github.julystar.musicapp.core.presentation.components.AppSwitch
import io.github.julystar.musicapp.core.presentation.components.DesignChevron
import io.github.julystar.musicapp.core.presentation.components.DesignChevronDirection
import io.github.julystar.musicapp.core.presentation.components.DesignPreferenceRow
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DspEffectCard(
    title: String,
    summary: String,
    enabled: Boolean,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    val canExpand = enabled && checked != false
    DesignPreferenceRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { if (canExpand) expanded = !expanded },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (checked != null && onCheckedChange != null) {
                    AppSwitch(
                        checked = checked,
                        enabled = enabled,
                        onCheckedChange = { value ->
                            if (!value) expanded = false
                            onCheckedChange(value)
                        },
                    )
                }
                DesignChevron(
                    direction = DesignChevronDirection.Right,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = if (expanded) 90f else 0f),
                )
            }
        },
    )
    if (expanded && canExpand) content()
}

@Composable
internal fun DspProfilePresetSection(
    effects: AudioEffectSettings,
    onUpdate: (AudioEffectSettings) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val builtIns = builtInDspPresets()
    val choices = listOf(
        DspPresetChoice(null, stringResource(Res.string.settings_dsp_preset_custom), null),
    ) + builtIns + effects.userPresets.map { preset ->
        DspPresetChoice(preset.id, preset.name, preset.profile)
    }
    val selected = choices.firstOrNull { it.profile == effects.profile } ?: choices.first()

    SettingsSection(title = stringResource(Res.string.settings_dsp_presets_section)) {
        SettingsSelectRow(
            label = stringResource(Res.string.settings_dsp_preset),
            selected = selected,
            options = choices,
            optionLabel = { choice -> choice.name },
            enabled = effects.enabled,
            onSelect = { choice ->
                choice.profile?.let { profile -> onUpdate(effects.withAudioEffectProfile(profile)) }
            },
        )
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_save_preset),
            value = stringResource(Res.string.settings_dsp_save_preset_summary),
            enabled = effects.enabled,
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
                optionLabel = { preset -> preset.name },
                enabled = effects.enabled,
                onSelect = { preset ->
                    onUpdate(
                        effects.copy(
                            userPresets = effects.userPresets.filterNot { it.id == preset.id },
                        ),
                    )
                },
            )
        }
        SettingsInfoRow(
            title = stringResource(Res.string.settings_dsp_restore_defaults),
            value = stringResource(Res.string.settings_dsp_restore_defaults_summary),
            enabled = effects.enabled,
            onClick = {
                onUpdate(
                    AudioEffectSettings.Default.copy(
                        enabled = effects.enabled,
                        headroom = effects.headroom,
                        userPresets = effects.userPresets,
                    ),
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
                onUpdate(
                    effects.copy(
                        userPresets = effects.userPresets + AudioEffectPreset(
                            id = nextDspPresetId(effects.userPresets, name),
                            name = name,
                            profile = effects.profile,
                        ),
                    ),
                )
                showSaveDialog = false
            }
        },
        onDismiss = { showSaveDialog = false },
    )
}

@Composable
internal fun DspProcessingChainSection(state: SettingsUiState) {
    val effects = state.settings.audioEffects
    val profile = effects.profile
    val effectsEnabled = effects.enabled
    val eqActive = effectsEnabled && when (profile.equalizerMode) {
        io.github.julystar.musicapp.core.domain.model.EqualizerMode.Graphic ->
            profile.graphicEqualizer.enabled
        io.github.julystar.musicapp.core.domain.model.EqualizerMode.Parametric ->
            profile.parametricEqualizer.enabled
    }
    val spatialActive = effectsEnabled && when (profile.spatialAudio.mode) {
        SpatialAudioMode.None -> false
        SpatialAudioMode.CrossfeedAndWidth ->
            profile.stereoWidth.enabled || profile.crossfeed.enabled
        SpatialAudioMode.Surround360,
        SpatialAudioMode.Panoramic360,
        -> true
    }
    val stages = listOf(
        DspChainStage(
            stringResource(Res.string.settings_replay_gain),
            state.settings.playbackAdvanced.replayGainMode != ReplayGainMode.Off,
        ),
        DspChainStage(
            stringResource(Res.string.settings_headroom_mode),
            effects.headroom.mode != HeadroomMode.Off,
        ),
        DspChainStage(stringResource(Res.string.settings_equalizer_section), eqActive),
        DspChainStage(stringResource(Res.string.settings_tone_enabled), effectsEnabled && profile.tone.enabled),
        DspChainStage(stringResource(Res.string.settings_loudness), effectsEnabled && profile.loudness.enabled),
        DspChainStage(stringResource(Res.string.settings_mono_bass), effectsEnabled && profile.monoBass.enabled),
        DspChainStage(stringResource(Res.string.settings_dynamic_eq), effectsEnabled && profile.dynamicEq.enabled),
        DspChainStage(stringResource(Res.string.settings_moog_section), effectsEnabled && profile.moogFilter.enabled),
        DspChainStage(stringResource(Res.string.settings_compressor), effectsEnabled && profile.compressor.enabled),
        DspChainStage(
            stringResource(Res.string.settings_reverb),
            effectsEnabled && profile.reverb.preset != io.github.julystar.musicapp.core.domain.model.ReverbPreset.None,
        ),
        DspChainStage(
            stringResource(Res.string.settings_spatial_mode),
            spatialActive,
        ),
        DspChainStage(stringResource(Res.string.settings_speaker_output), effectsEnabled && profile.speakerOutput.enabled),
        DspChainStage(
            stringResource(Res.string.settings_peak_limiter),
            profile.limiter.enabled && (
                effectsEnabled ||
                    effects.headroom.mode != HeadroomMode.Off ||
                    state.settings.playbackAdvanced.replayGainMode != ReplayGainMode.Off
                ),
        ),
    )
    SettingsSection(title = stringResource(Res.string.settings_dsp_processing_chain)) {
        Text(
            text = stringResource(Res.string.settings_dsp_processing_chain_summary),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stages.forEachIndexed { index, stage ->
                DspChainChip(stage)
                if (index != stages.lastIndex) {
                    Text(
                        text = "→",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DspChainChip(stage: DspChainStage) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    Text(
        text = stage.label,
        style = MiuixTheme.textStyles.footnote1,
        color = if (stage.active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .alpha(if (stage.active) 1f else 0.6f)
            .clip(shape)
            .background(
                if (stage.active) MiuixTheme.colorScheme.primaryContainer
                else MiuixTheme.colorScheme.surfaceContainerHigh,
            )
            .border(1.dp, MiuixTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
internal fun DspRuntimeStatusSection(state: SettingsUiState) {
    val status = state.audioDspRuntimeStatus
    val compactSummary = buildList {
        add(status.state.localizedName())
        status.sampleRate?.let { add("$it Hz") }
        status.channelCount?.let { channels ->
            add(
                if (channels == 1) stringResource(Res.string.settings_dsp_mono)
                else stringResource(Res.string.settings_dsp_stereo),
            )
        }
    }.joinToString(" · ")
    SettingsSection(title = stringResource(Res.string.settings_dsp_runtime_section)) {
        DspEffectCard(
            title = stringResource(Res.string.settings_dsp_runtime_section),
            summary = compactSummary,
            enabled = true,
        ) {
            status.sampleFormat?.let { format ->
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_runtime_format),
                    value = format.localizedName(),
                )
            }
            status.bypassReason?.let { reason ->
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_bypass_reason),
                    value = reason.localizedName(),
                )
            }
            status.lastErrorCode?.let { error ->
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_error_code),
                    value = error.toString(),
                )
            }
            if (status.latencyFrames > 0) {
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_latency),
                    value = stringResource(Res.string.settings_dsp_latency_frames, status.latencyFrames),
                )
            }
            if (status.state == AudioDspRuntimeState.Active) {
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_meter_peaks),
                    value = stringResource(
                        Res.string.settings_dsp_meter_peaks_value,
                        formatMeter(state.audioDspMeter.inputPeakDb),
                        formatMeter(state.audioDspMeter.outputPeakDb),
                    ),
                )
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_meter_reduction),
                    value = stringResource(
                        Res.string.settings_dsp_meter_reduction_value,
                        formatMeter(state.audioDspMeter.compressorGainReductionDb),
                        formatMeter(state.audioDspMeter.limiterGainReductionDb),
                        formatMeter(state.audioDspMeter.appliedHeadroomDb),
                    ),
                )
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_dsp_meter_recovery),
                    value = stringResource(
                        Res.string.settings_dsp_meter_recovery_value,
                        state.audioDspMeter.clippedSamples,
                        state.audioDspMeter.nonFiniteRecoveryCount,
                    ),
                )
                if (state.audioDspPerformance.processCount > 0) {
                    SettingsInfoRow(
                        title = stringResource(Res.string.settings_dsp_performance),
                        value = stringResource(
                            Res.string.settings_dsp_performance_value,
                            state.audioDspPerformance.averageProcessingTimeUs,
                            state.audioDspPerformance.maxProcessingTimeUs,
                            state.audioDspPerformance.deadlineUtilization * 100f,
                        ),
                    )
                }
            }
        }
    }
}

private data class DspPresetChoice(
    val id: String?,
    val name: String,
    val profile: AudioEffectProfile?,
)

private data class DspChainStage(val label: String, val active: Boolean)

@Composable
private fun builtInDspPresets(): List<DspPresetChoice> = listOf(
    DspPresetChoice(
        id = "builtin:flat",
        name = stringResource(Res.string.settings_dsp_preset_flat),
        profile = AudioEffectProfile.Default,
    ),
    DspPresetChoice(
        id = "builtin:bass",
        name = stringResource(Res.string.settings_dsp_preset_bass),
        profile = AudioEffectProfile.Default.copy(
            graphicEqualizer = io.github.julystar.musicapp.core.domain.model.GraphicEqualizerSettings(
                bandGainsDb = listOf(5, 5, 4, 2, 0, 0, 0, 0, 0, 0),
            ),
        ),
    ),
    DspPresetChoice(
        id = "builtin:vocal",
        name = stringResource(Res.string.settings_dsp_preset_vocal),
        profile = AudioEffectProfile.Default.copy(
            graphicEqualizer = io.github.julystar.musicapp.core.domain.model.GraphicEqualizerSettings(
                bandGainsDb = listOf(-2, -1, 0, 1, 2, 3, 3, 2, 0, -1),
            ),
            compressor = CompressorSettings(enabled = true, thresholdDb = -20, ratio = 3),
        ),
    ),
    DspPresetChoice(
        id = "builtin:night",
        name = stringResource(Res.string.settings_dsp_preset_night),
        profile = AudioEffectProfile.Default.copy(
            loudness = LoudnessSettings(enabled = true, amountPercent = 35),
            compressor = CompressorSettings(enabled = true, thresholdDb = -24, ratio = 4),
        ),
    ),
)

private fun nextDspPresetId(existing: List<AudioEffectPreset>, name: String): String {
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

@Composable
internal fun AudioSampleFormat.localizedName(): String = when (this) {
    AudioSampleFormat.Pcm16 -> stringResource(Res.string.settings_dsp_format_pcm16)
    AudioSampleFormat.Float32 -> stringResource(Res.string.settings_dsp_format_float32)
}

@Composable
internal fun AudioDspRuntimeState.localizedName(): String = when (this) {
    AudioDspRuntimeState.Inactive -> stringResource(Res.string.settings_dsp_status_inactive)
    AudioDspRuntimeState.Active -> stringResource(Res.string.settings_dsp_status_active)
    AudioDspRuntimeState.Bypassed -> stringResource(Res.string.settings_dsp_status_bypassed)
    AudioDspRuntimeState.Unavailable -> stringResource(Res.string.settings_dsp_status_unavailable)
    AudioDspRuntimeState.Error -> stringResource(Res.string.settings_dsp_status_error)
}

@Composable
private fun AudioDspBypassReason.localizedName(): String = when (this) {
    AudioDspBypassReason.EffectsDisabled -> stringResource(Res.string.settings_dsp_reason_effects_disabled)
    AudioDspBypassReason.UnsupportedSampleFormat -> stringResource(Res.string.settings_dsp_reason_sample_format)
    AudioDspBypassReason.UnsupportedChannelCount -> stringResource(Res.string.settings_dsp_reason_channel_count)
    AudioDspBypassReason.UnsupportedSampleRate -> stringResource(Res.string.settings_dsp_reason_sample_rate)
    AudioDspBypassReason.PlatformProcessingUnavailable -> stringResource(Res.string.settings_dsp_reason_platform_unavailable)
    AudioDspBypassReason.ProtectedContent -> stringResource(Res.string.settings_dsp_reason_protected_content)
    AudioDspBypassReason.AudioTapUnavailable -> stringResource(Res.string.settings_dsp_reason_audio_tap)
    AudioDspBypassReason.OutputRouteUnavailable -> stringResource(Res.string.settings_dsp_reason_output_route)
    AudioDspBypassReason.NativeProcessingError -> stringResource(Res.string.settings_dsp_reason_native_error)
}
