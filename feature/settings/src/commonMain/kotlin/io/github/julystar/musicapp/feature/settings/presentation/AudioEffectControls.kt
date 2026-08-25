package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.MoogFilterMode
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.SpeakerOutputMode
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

@Composable
private fun DspParameterSlider(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    onValueChange: (Int) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var previewValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    SliderPreference(
        title = title,
        summary = summary,
        value = previewValue,
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        valueText = valueText,
        enabled = enabled,
        onValueChange = { previewValue = it },
        onValueChangeFinished = { onValueChange(previewValue.roundToInt()) },
    )
}

@Composable
internal fun AudioEffectsDynamicsSection(
    state: SettingsUiState,
    profile: AudioEffectProfile,
    effectsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val capabilities = state.capabilities.audioDsp
    SmallTitle(
        text = stringResource(Res.string.settings_audio_effects_dynamics_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        if (capabilities.loudness) {
            val settings = profile.loudness
            DspEffectCard(
                title = stringResource(Res.string.settings_loudness),
                summary = if (settings.enabled) {
                    stringResource(Res.string.settings_loudness_summary) +
                        " · ${formatPercent(settings.amountPercent)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(loudness = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_loudness_amount),
                    value = settings.amountPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.amountPercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(loudness = settings.copy(amountPercent = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_loudness_balance),
                    value = settings.balancePercent,
                    valueRange = -100..100,
                    valueText = formatPercent(settings.balancePercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(loudness = settings.copy(balancePercent = value)))
                    },
                )
            }
        }

        if (capabilities.dynamicEq) {
            val settings = profile.dynamicEq
            DspEffectCard(
                title = stringResource(Res.string.settings_dynamic_eq),
                summary = if (settings.enabled) {
                    "${formatPercent(settings.amountPercent)} · " +
                        stringResource(Res.string.settings_de_esser_amount) +
                        " ${formatPercent(settings.deEsserAmountPercent)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(dynamicEq = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_dynamic_eq_amount),
                    value = settings.amountPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.amountPercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(dynamicEq = settings.copy(amountPercent = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_de_esser_amount),
                    value = settings.deEsserAmountPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.deEsserAmountPercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(dynamicEq = settings.copy(deEsserAmountPercent = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_de_esser_frequency),
                    value = settings.deEsserFrequencyHz / 100,
                    valueRange = 40..100,
                    valueText = formatHz(settings.deEsserFrequencyHz),
                    onValueChange = { value ->
                        onUpdate(
                            profile.copy(
                                dynamicEq = settings.copy(deEsserFrequencyHz = value * 100),
                            ),
                        )
                    },
                )
            }
        }

        if (capabilities.compressor) {
            val settings = profile.compressor
            DspEffectCard(
                title = stringResource(Res.string.settings_compressor),
                summary = if (settings.enabled) {
                    "${formatDb(settings.thresholdDb)} · ${settings.ratio}:1"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(compressor = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_threshold),
                    value = settings.thresholdDb,
                    valueRange = -60..0,
                    valueText = formatDb(settings.thresholdDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(thresholdDb = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_ratio),
                    value = settings.ratio,
                    valueRange = 1..30,
                    valueText = "${settings.ratio}:1",
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(ratio = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_attack),
                    value = settings.attackMs,
                    valueRange = 1..500,
                    valueText = formatMilliseconds(settings.attackMs),
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(attackMs = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_release),
                    value = settings.releaseMs,
                    valueRange = 5..5_000,
                    valueText = formatMilliseconds(settings.releaseMs),
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(releaseMs = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_makeup),
                    value = settings.makeupGainDb,
                    valueRange = -12..24,
                    valueText = formatDb(settings.makeupGainDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(makeupGainDb = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_compressor_knee),
                    value = settings.kneeDb,
                    valueRange = 0..24,
                    valueText = formatDb(settings.kneeDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(compressor = settings.copy(kneeDb = value)))
                    },
                )
            }
        }
    }
}

@Composable
internal fun AudioEffectsSoundFieldSection(
    state: SettingsUiState,
    profile: AudioEffectProfile,
    effectsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val capabilities = state.capabilities.audioDsp
    SmallTitle(
        text = stringResource(Res.string.settings_audio_effects_sound_field_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        if (capabilities.monoBass) {
            val settings = profile.monoBass
            DspEffectCard(
                title = stringResource(Res.string.settings_mono_bass),
                summary = if (settings.enabled) {
                    "${formatHz(settings.crossoverHz)} · ${formatPercent(settings.amountPercent)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(monoBass = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_mono_bass_crossover),
                    value = settings.crossoverHz,
                    valueRange = 60..300,
                    valueText = formatHz(settings.crossoverHz),
                    onValueChange = { value ->
                        onUpdate(profile.copy(monoBass = settings.copy(crossoverHz = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_mono_bass_amount),
                    value = settings.amountPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.amountPercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(monoBass = settings.copy(amountPercent = value)))
                    },
                )
            }
        }

        val availableModes = SpatialAudioMode.entries.filter { mode ->
            when (mode) {
                SpatialAudioMode.None -> true
                SpatialAudioMode.CrossfeedAndWidth -> capabilities.stereoWidth || capabilities.crossfeed
                SpatialAudioMode.Surround360 -> capabilities.surround360
                SpatialAudioMode.Panoramic360 -> capabilities.panoramic360
            }
        }
        if (availableModes.size > 1) {
            val spatial = profile.spatialAudio
            DspEffectCard(
                title = stringResource(Res.string.settings_spatial_mode),
                summary = spatial.mode.localizedName(),
                enabled = effectsEnabled,
            ) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_spatial_mode),
                    summary = stringResource(Res.string.settings_spatial_mode_summary),
                    entries = listOf(DropdownEntry(items = availableModes.map { mode ->
                        DropdownItem(
                            text = mode.localizedName(),
                            selected = mode == spatial.mode,
                            onClick = {
                                onUpdate(profile.copy(spatialAudio = spatial.copy(mode = mode)))
                            },
                        )
                    })),
                )
                if (spatial.mode == SpatialAudioMode.CrossfeedAndWidth) {
                    if (capabilities.stereoWidth) {
                        val width = profile.stereoWidth
                        SwitchPreference(
                            title = stringResource(Res.string.settings_stereo_width),
                            checked = width.enabled,
                            onCheckedChange = { enabled ->
                                onUpdate(profile.copy(stereoWidth = width.copy(enabled = enabled)))
                            },
                        )
                        if (width.enabled) {
                            DspParameterSlider(
                                title = stringResource(Res.string.settings_stereo_width),
                                value = width.widthPercent,
                                valueRange = 0..200,
                                valueText = formatPercent(width.widthPercent),
                                onValueChange = { value ->
                                    onUpdate(profile.copy(stereoWidth = width.copy(widthPercent = value)))
                                },
                            )
                        }
                    }
                    if (capabilities.crossfeed) {
                        val crossfeed = profile.crossfeed
                        SwitchPreference(
                            title = stringResource(Res.string.settings_crossfeed),
                            checked = crossfeed.enabled,
                            onCheckedChange = { enabled ->
                                onUpdate(profile.copy(crossfeed = crossfeed.copy(enabled = enabled)))
                            },
                        )
                        if (crossfeed.enabled) {
                            DspParameterSlider(
                                title = stringResource(Res.string.settings_crossfeed_low_cut),
                                value = crossfeed.lowCutHz,
                                valueRange = 50..1_000,
                                valueText = formatHz(crossfeed.lowCutHz),
                                onValueChange = { value ->
                                    onUpdate(profile.copy(crossfeed = crossfeed.copy(lowCutHz = value)))
                                },
                            )
                            DspParameterSlider(
                                title = stringResource(Res.string.settings_crossfeed_high_cut),
                                value = crossfeed.highCutHz / 10,
                                valueRange = 50..800,
                                valueText = formatHz(crossfeed.highCutHz),
                                onValueChange = { value ->
                                    onUpdate(profile.copy(crossfeed = crossfeed.copy(highCutHz = value * 10)))
                                },
                            )
                            DspParameterSlider(
                                title = stringResource(Res.string.settings_crossfeed_attenuation),
                                value = crossfeed.attenuationTenthsDb,
                                valueRange = 0..150,
                                valueText = formatTenthsDb(-crossfeed.attenuationTenthsDb),
                                onValueChange = { value ->
                                    onUpdate(
                                        profile.copy(
                                            crossfeed = crossfeed.copy(attenuationTenthsDb = value),
                                        ),
                                    )
                                },
                            )
                        }
                    }
                } else if (spatial.mode != SpatialAudioMode.None) {
                    DspParameterSlider(
                        title = stringResource(Res.string.settings_spatial_intensity),
                        value = spatial.intensityPercent,
                        valueRange = 0..100,
                        valueText = formatPercent(spatial.intensityPercent),
                        onValueChange = { value ->
                            onUpdate(profile.copy(spatialAudio = spatial.copy(intensityPercent = value)))
                        },
                    )
                    DspParameterSlider(
                        title = stringResource(Res.string.settings_spatial_azimuth),
                        value = spatial.azimuthDegrees,
                        valueRange = 0..359,
                        valueText = formatDegrees(spatial.azimuthDegrees),
                        onValueChange = { value ->
                            onUpdate(profile.copy(spatialAudio = spatial.copy(azimuthDegrees = value)))
                        },
                    )
                    DspParameterSlider(
                        title = stringResource(Res.string.settings_spatial_auto_rotate),
                        value = spatial.autoRotateDegreesPerSecond,
                        valueRange = -180..180,
                        valueText = "${spatial.autoRotateDegreesPerSecond}°/s",
                        onValueChange = { value ->
                            onUpdate(
                                profile.copy(
                                    spatialAudio = spatial.copy(autoRotateDegreesPerSecond = value),
                                ),
                            )
                        },
                    )
                    if (spatial.mode == SpatialAudioMode.Panoramic360) {
                        DspParameterSlider(
                            title = stringResource(Res.string.settings_spatial_elevation),
                            value = spatial.elevationDegrees,
                            valueRange = -90..90,
                            valueText = formatDegrees(spatial.elevationDegrees),
                            onValueChange = { value ->
                                onUpdate(profile.copy(spatialAudio = spatial.copy(elevationDegrees = value)))
                            },
                        )
                        DspParameterSlider(
                            title = stringResource(Res.string.settings_spatial_room),
                            value = spatial.roomAmountPercent,
                            valueRange = 0..100,
                            valueText = formatPercent(spatial.roomAmountPercent),
                            onValueChange = { value ->
                                onUpdate(profile.copy(spatialAudio = spatial.copy(roomAmountPercent = value)))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioEffectsToneFilterSection(
    state: SettingsUiState,
    profile: AudioEffectProfile,
    effectsEnabled: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    val capabilities = state.capabilities.audioDsp
    SmallTitle(
        text = stringResource(Res.string.settings_audio_effects_tone_filters_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        if (capabilities.toneControl) {
            val settings = profile.tone
            DspEffectCard(
                title = stringResource(Res.string.settings_tone_enabled),
                summary = if (settings.enabled) {
                    "${stringResource(Res.string.settings_bass)} ${formatDb(settings.bassGainDb)} · " +
                        "${stringResource(Res.string.settings_treble)} ${formatDb(settings.trebleGainDb)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(tone = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_bass),
                    summary = stringResource(Res.string.settings_bass_shelf_summary),
                    value = settings.bassGainDb,
                    valueRange = -24..24,
                    valueText = formatDb(settings.bassGainDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(tone = settings.copy(bassGainDb = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_bass_frequency),
                    value = settings.bassFrequencyHz,
                    valueRange = 50..500,
                    valueText = formatHz(settings.bassFrequencyHz),
                    onValueChange = { value ->
                        onUpdate(profile.copy(tone = settings.copy(bassFrequencyHz = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_treble),
                    summary = stringResource(Res.string.settings_treble_shelf_summary),
                    value = settings.trebleGainDb,
                    valueRange = -24..24,
                    valueText = formatDb(settings.trebleGainDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(tone = settings.copy(trebleGainDb = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_treble_frequency),
                    value = settings.trebleFrequencyHz / 100,
                    valueRange = 20..160,
                    valueText = formatHz(settings.trebleFrequencyHz),
                    onValueChange = { value ->
                        onUpdate(profile.copy(tone = settings.copy(trebleFrequencyHz = value * 100)))
                    },
                )
            }
        }

        if (capabilities.moogFilter) {
            val settings = profile.moogFilter
            DspEffectCard(
                title = stringResource(Res.string.settings_moog_section),
                summary = if (settings.enabled) {
                    "${settings.mode.localizedName()} · ${formatHz(settings.cutoffHz)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdate(profile.copy(moogFilter = settings.copy(enabled = enabled)))
                },
            ) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_moog_mode),
                    entries = listOf(DropdownEntry(items = MoogFilterMode.entries.map { mode ->
                        DropdownItem(
                            text = mode.localizedName(),
                            selected = mode == settings.mode,
                            onClick = { onUpdate(profile.copy(moogFilter = settings.copy(mode = mode))) },
                        )
                    })),
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_moog_cutoff),
                    value = settings.cutoffHz,
                    valueRange = 20..20_000,
                    valueText = formatHz(settings.cutoffHz),
                    onValueChange = { value ->
                        onUpdate(profile.copy(moogFilter = settings.copy(cutoffHz = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_moog_resonance),
                    value = settings.resonancePercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.resonancePercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(moogFilter = settings.copy(resonancePercent = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_moog_drive),
                    value = settings.driveTenthsDb,
                    valueRange = 0..180,
                    valueText = formatTenthsDb(settings.driveTenthsDb),
                    onValueChange = { value ->
                        onUpdate(profile.copy(moogFilter = settings.copy(driveTenthsDb = value)))
                    },
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_moog_mix),
                    value = settings.mixPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.mixPercent),
                    onValueChange = { value ->
                        onUpdate(profile.copy(moogFilter = settings.copy(mixPercent = value)))
                    },
                )
            }
        }
    }
}

@Composable
internal fun AudioEffectsOutputSection(
    state: SettingsUiState,
    effects: AudioEffectSettings,
    onUpdateProfile: (AudioEffectProfile) -> Unit,
    onUpdateEffects: (AudioEffectSettings) -> Unit,
) {
    val profile = effects.profile
    val capabilities = state.capabilities.audioDsp
    val effectsEnabled = effects.enabled
    SmallTitle(
        text = stringResource(Res.string.settings_audio_effects_output_safety_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        if (capabilities.speakerOutput) {
            val settings = profile.speakerOutput
            DspEffectCard(
                title = stringResource(Res.string.settings_speaker_output),
                summary = if (settings.enabled) {
                    "${settings.mode.localizedName()} · ${formatPercent(settings.strengthPercent)}"
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdateProfile(profile.copy(speakerOutput = settings.copy(enabled = enabled)))
                },
            ) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_speaker_mode),
                    entries = listOf(DropdownEntry(items = SpeakerOutputMode.entries.map { mode ->
                        DropdownItem(
                            text = mode.localizedName(),
                            selected = mode == settings.mode,
                            onClick = { onUpdateProfile(profile.copy(speakerOutput = settings.copy(mode = mode))) },
                        )
                    })),
                )
                DspParameterSlider(
                    title = stringResource(Res.string.settings_speaker_strength),
                    value = settings.strengthPercent,
                    valueRange = 0..100,
                    valueText = formatPercent(settings.strengthPercent),
                    onValueChange = { value ->
                        onUpdateProfile(profile.copy(speakerOutput = settings.copy(strengthPercent = value)))
                    },
                )
            }
        }

        if (capabilities.reverb) {
            val settings = profile.reverb
            DspEffectCard(
                title = stringResource(Res.string.settings_reverb),
                summary = settings.preset.localizedName(),
                enabled = effectsEnabled,
            ) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_reverb),
                    entries = listOf(DropdownEntry(items = ReverbPreset.entries.map { preset ->
                        DropdownItem(
                            text = preset.localizedName(),
                            selected = preset == settings.preset,
                            onClick = { onUpdateProfile(profile.copy(reverb = settings.copy(preset = preset))) },
                        )
                    })),
                )
                if (settings.preset != ReverbPreset.None) {
                    DspParameterSlider(
                    title = stringResource(Res.string.settings_reverb_wet),
                        value = settings.wetPercent,
                        valueRange = 0..50,
                        valueText = formatPercent(settings.wetPercent),
                        onValueChange = { value ->
                            onUpdateProfile(profile.copy(reverb = settings.copy(wetPercent = value)))
                        },
                    )
                }
            }
        }

        val headroom = effects.headroom
        DspEffectCard(
            title = stringResource(Res.string.settings_headroom_mode),
            summary = headroom.mode.localizedName(),
            enabled = true,
        ) {
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_headroom_mode),
                entries = listOf(DropdownEntry(items = HeadroomMode.entries.map { mode ->
                    DropdownItem(
                        text = mode.localizedName(),
                        selected = mode == headroom.mode,
                        onClick = { onUpdateEffects(effects.copy(headroom = headroom.copy(mode = mode))) },
                    )
                })),
            )
            if (headroom.mode == HeadroomMode.Automatic) {
                BasicComponent(
                    title = stringResource(Res.string.settings_headroom_automatic_info),
                    summary = "${formatMeter(state.audioDspMeter.appliedHeadroomDb)} · " +
                        stringResource(Res.string.settings_eq_automatic_headroom_hint),
                )
            }
            if (headroom.mode == HeadroomMode.Manual) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_headroom_manual),
                    value = headroom.manualTenthsDb,
                    valueRange = -240..0,
                    valueText = formatTenthsDb(headroom.manualTenthsDb),
                    onValueChange = { value ->
                        onUpdateEffects(effects.copy(headroom = headroom.copy(manualTenthsDb = value)))
                    },
                )
            }
        }

        if (capabilities.peakLimiter) {
            val settings = profile.limiter
            DspEffectCard(
                title = stringResource(Res.string.settings_peak_limiter),
                summary = if (settings.enabled) {
                    formatTenthsDb(settings.ceilingTenthsDb)
                } else {
                    stringResource(Res.string.settings_effect_disabled_summary)
                },
                enabled = effectsEnabled,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onUpdateProfile(profile.copy(limiter = settings.copy(enabled = enabled)))
                },
            ) {
                DspParameterSlider(
                    title = stringResource(Res.string.settings_limiter_ceiling),
                    value = settings.ceilingTenthsDb,
                    valueRange = -120..0,
                    valueText = formatTenthsDb(settings.ceilingTenthsDb),
                    onValueChange = { value ->
                        onUpdateProfile(profile.copy(limiter = settings.copy(ceilingTenthsDb = value)))
                    },
                )
                if (!settings.truePeakEnabled) {
                    DspParameterSlider(
                        title = stringResource(Res.string.settings_limiter_attack),
                        value = settings.attackHundredthsMs,
                        valueRange = 1..2_000,
                        valueText = formatHundredthsMs(settings.attackHundredthsMs),
                        onValueChange = { value ->
                            onUpdateProfile(profile.copy(limiter = settings.copy(attackHundredthsMs = value)))
                        },
                    )
                }
                DspParameterSlider(
                    title = stringResource(Res.string.settings_limiter_release),
                    value = settings.releaseMs,
                    valueRange = 5..2_000,
                    valueText = formatMilliseconds(settings.releaseMs),
                    onValueChange = { value ->
                        onUpdateProfile(profile.copy(limiter = settings.copy(releaseMs = value)))
                    },
                )
                if (capabilities.truePeakLimiter) {
                    SwitchPreference(
                        title = stringResource(Res.string.settings_true_peak_mode),
                        summary = stringResource(Res.string.settings_true_peak_mode_summary),
                        checked = settings.truePeakEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateProfile(
                                profile.copy(
                                    limiter = settings.copy(
                                        truePeakEnabled = enabled,
                                        oversampling = if (enabled) 4 else 1,
                                        ceilingTenthsDb = if (enabled && settings.ceilingTenthsDb == -5) {
                                            -10
                                        } else {
                                            settings.ceilingTenthsDb
                                        },
                                    ),
                                ),
                            )
                        },
                    )
                    if (settings.truePeakEnabled) {
                        BasicComponent(
                            title = stringResource(Res.string.settings_limiter_oversampling),
                            summary = stringResource(
                                Res.string.settings_limiter_oversampling_value,
                                settings.oversampling,
                            ),
                        )
                        DspParameterSlider(
                            title = stringResource(Res.string.settings_limiter_lookahead),
                            value = settings.lookaheadMs,
                            valueRange = 1..10,
                            valueText = formatMilliseconds(settings.lookaheadMs),
                            onValueChange = { value ->
                                onUpdateProfile(profile.copy(limiter = settings.copy(lookaheadMs = value)))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpatialAudioMode.localizedName(): String = when (this) {
    SpatialAudioMode.None -> stringResource(Res.string.settings_spatial_none)
    SpatialAudioMode.CrossfeedAndWidth -> stringResource(Res.string.settings_spatial_crossfeed_width)
    SpatialAudioMode.Surround360 -> stringResource(Res.string.settings_spatial_surround)
    SpatialAudioMode.Panoramic360 -> stringResource(Res.string.settings_spatial_panoramic)
}

@Composable
private fun MoogFilterMode.localizedName(): String = when (this) {
    MoogFilterMode.LowPass24 -> stringResource(Res.string.settings_moog_low_pass_24)
    MoogFilterMode.LowPass12 -> stringResource(Res.string.settings_moog_low_pass_12)
    MoogFilterMode.HighPass24 -> stringResource(Res.string.settings_moog_high_pass_24)
    MoogFilterMode.BandPass12 -> stringResource(Res.string.settings_moog_band_pass_12)
    MoogFilterMode.Notch -> stringResource(Res.string.settings_moog_notch)
}

@Composable
private fun SpeakerOutputMode.localizedName(): String = when (this) {
    SpeakerOutputMode.Elasticity -> stringResource(Res.string.settings_speaker_elasticity)
    SpeakerOutputMode.Powerful -> stringResource(Res.string.settings_speaker_powerful)
    SpeakerOutputMode.Wide -> stringResource(Res.string.settings_speaker_wide)
}

@Composable
private fun ReverbPreset.localizedName(): String = when (this) {
    ReverbPreset.None -> stringResource(Res.string.settings_reverb_none)
    ReverbPreset.SmallRoom -> stringResource(Res.string.settings_reverb_small_room)
    ReverbPreset.MediumRoom -> stringResource(Res.string.settings_reverb_medium_room)
    ReverbPreset.LargeRoom -> stringResource(Res.string.settings_reverb_large_room)
    ReverbPreset.Hall -> stringResource(Res.string.settings_reverb_hall)
    ReverbPreset.Plate -> stringResource(Res.string.settings_reverb_plate)
}

@Composable
private fun HeadroomMode.localizedName(): String = when (this) {
    HeadroomMode.Off -> stringResource(Res.string.settings_headroom_off)
    HeadroomMode.Automatic -> stringResource(Res.string.settings_headroom_automatic)
    HeadroomMode.Manual -> stringResource(Res.string.settings_headroom_manual)
}
