package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.MAX_REPLAY_GAIN_PREAMP_TENTHS_DB
import io.github.julystar.musicapp.core.domain.model.MIN_REPLAY_GAIN_PREAMP_TENTHS_DB
import io.github.julystar.musicapp.core.domain.model.PlayNextMode
import io.github.julystar.musicapp.core.domain.model.PreviousButtonBehavior
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.core.domain.model.StartupPlaybackMode
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

@Composable
fun PlaybackSettingsSection(
    state: SettingsUiState,
    audioOutputState: AudioOutputState,
    onSelectAudioOutput: (AudioOutputDeviceId?) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onBack: (() -> Unit)?,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToAudioEffects: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    val capabilities = state.capabilities

    SettingsPageLayout(title = stringResource(Res.string.settings_playback_title), onBack = onBack) {
        if (
            capabilities.audioOutputSelectionSupported ||
            capabilities.audioRoutePickerSupported
        ) {
            SmallTitle(
                text = stringResource(Res.string.settings_audio_output_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                ArrowPreference(
                    title = stringResource(Res.string.settings_current_audio_output),
                    summary = audioOutputState.selectedDevice?.name
                        ?: stringResource(Res.string.settings_audio_output_unavailable),
                    onClick = onRefreshAudioOutputs,
                )
                if (
                    capabilities.audioOutputSelectionSupported &&
                    audioOutputState.devices.isNotEmpty()
                ) {
                    val selected = audioOutputState.selectedDevice ?: audioOutputState.devices.first()
                    OverlayDropdownPreference(
                        title = stringResource(Res.string.settings_select_audio_output),
                        entries = listOf(DropdownEntry(
                            items = audioOutputState.devices.map { device ->
                                DropdownItem(
                                    text = if (device.isSystemDefault) {
                                        stringResource(Res.string.settings_audio_output_default, device.name)
                                    } else {
                                        device.name
                                    },
                                    selected = device == selected,
                                    onClick = { onSelectAudioOutput(device.id) },
                                )
                            },
                        )),
                    )
                }
                if (capabilities.audioRoutePickerSupported) {
                    PlatformAudioRoutePicker(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    )
                }
            }
        }

        if (capabilities.audioFocusSupported) {
            SmallTitle(
                text = stringResource(Res.string.settings_audio_focus_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                val modes = AudioFocusMode.entries.toList()
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_audio_focus_section),
                    entries = listOf(DropdownEntry(
                        items = modes.map { mode ->
                            DropdownItem(
                                text = stringResource(mode.titleResource()),
                                selected = mode == settings.audioFocusMode,
                                onClick = { onAction(SettingsAction.SetAudioFocusMode(mode)) },
                            )
                        },
                    )),
                )
            }
        }

        SmallTitle(
            text = stringResource(Res.string.settings_playback_behavior_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            if (capabilities.deviceDisconnectSupported) {
                SwitchPreference(
                    title = stringResource(Res.string.settings_pause_disconnect),
                    summary = stringResource(Res.string.settings_pause_disconnect_summary),
                    checked = settings.pauseOnDisconnect,
                    onCheckedChange = { onAction(SettingsAction.SetPauseOnDisconnect(it)) },
                )
            }
            if (capabilities.gaplessPlaybackSupported) {
                SwitchPreference(
                    title = stringResource(Res.string.settings_gapless),
                    summary = stringResource(Res.string.settings_gapless_summary),
                    checked = settings.gaplessPlaybackEnabled,
                    onCheckedChange = { onAction(SettingsAction.SetGaplessPlaybackEnabled(it)) },
                )
            }
            SwitchPreference(
                title = stringResource(Res.string.settings_retry_playback),
                summary = stringResource(Res.string.settings_retry_playback_summary),
                checked = settings.retryPlaybackOnFailure,
                onCheckedChange = { onAction(SettingsAction.SetRetryPlaybackOnFailure(it)) },
            )
            if (capabilities.networkStatusSupported) {
                SwitchPreference(
                    title = stringResource(Res.string.settings_resume_network),
                    summary = stringResource(Res.string.settings_resume_network_summary),
                    checked = settings.resumePlaybackAfterNetworkRecovery,
                    onCheckedChange = {
                        onAction(SettingsAction.SetResumePlaybackAfterNetworkRecovery(it))
                    },
                )
            }
            SwitchPreference(
                title = stringResource(Res.string.settings_keep_screen_on),
                summary = stringResource(Res.string.settings_keep_screen_on_summary),
                checked = settings.keepScreenOnInPlayer,
                onCheckedChange = { onAction(SettingsAction.SetKeepScreenOnInPlayer(it)) },
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_playback_advanced_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_resume_playback_position),
                summary = stringResource(Res.string.settings_resume_playback_position_summary),
                checked = settings.playbackAdvanced.resumePlaybackPosition,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(resumePlaybackPosition = it)
                        )
                    )
                },
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_startup_playback),
                entries = listOf(DropdownEntry(items = StartupPlaybackMode.entries.map { mode ->
                    DropdownItem(
                        text = stringResource(mode.titleResource()),
                        selected = mode == settings.playbackAdvanced.startupPlaybackMode,
                        onClick = { onAction(SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(startupPlaybackMode = mode),
                        )) },
                    )
                })),
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_previous_button_behavior),
                entries = listOf(DropdownEntry(items = PreviousButtonBehavior.entries.map { behavior ->
                    DropdownItem(
                        text = stringResource(behavior.titleResource()),
                        selected = behavior == settings.playbackAdvanced.previousButtonBehavior,
                        onClick = { onAction(SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(previousButtonBehavior = behavior),
                        )) },
                    )
                })),
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_play_next_mode),
                entries = listOf(DropdownEntry(items = PlayNextMode.entries.map { mode ->
                    DropdownItem(
                        text = stringResource(mode.titleResource()),
                        selected = mode == settings.playbackAdvanced.playNextMode,
                        onClick = { onAction(SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(playNextMode = mode),
                        )) },
                    )
                })),
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_shuffle_strategy),
                entries = listOf(DropdownEntry(items = ShuffleStrategy.entries.map { strategy ->
                    DropdownItem(
                        text = stringResource(strategy.titleResource()),
                        selected = strategy == settings.playbackAdvanced.shuffleStrategy,
                        onClick = { onAction(SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(shuffleStrategy = strategy),
                        )) },
                    )
                })),
            )
        }

        if (capabilities.crossfadeSupported || capabilities.replayGainSupported) {
            SmallTitle(
                text = stringResource(Res.string.settings_playback_enhancement_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                if (capabilities.crossfadeSupported) {
                    var crossfadePreview by remember(settings.playbackAdvanced.crossfadeDurationMs) {
                        mutableFloatStateOf((settings.playbackAdvanced.crossfadeDurationMs / 1_000).toFloat())
                    }
                    SliderPreference(
                        title = stringResource(Res.string.settings_crossfade),
                        summary = stringResource(Res.string.settings_crossfade_summary),
                        value = crossfadePreview,
                        valueRange = 0f..30f,
                        steps = 29,
                        valueText = stringResource(
                            Res.string.settings_seconds_value,
                            crossfadePreview.roundToInt(),
                        ),
                        onValueChange = { crossfadePreview = it },
                        onValueChangeFinished = {
                            onAction(
                                SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(
                                        crossfadeDurationMs = crossfadePreview.roundToInt() * 1_000,
                                    ),
                                )
                            )
                        },
                    )
                }
                if (capabilities.replayGainSupported) {
                    OverlayDropdownPreference(
                        title = stringResource(Res.string.settings_replay_gain),
                        entries = listOf(DropdownEntry(items = ReplayGainMode.entries.map { mode ->
                            DropdownItem(
                                text = stringResource(mode.titleResource()),
                                selected = mode == settings.playbackAdvanced.replayGainMode,
                                onClick = { onAction(SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(replayGainMode = mode),
                                )) },
                            )
                        })),
                    )
                    var preampPreview by remember(settings.playbackAdvanced.replayGainPreampTenthsDb) {
                        mutableFloatStateOf(settings.playbackAdvanced.replayGainPreampTenthsDb.toFloat())
                    }
                    SliderPreference(
                        title = stringResource(Res.string.settings_replay_gain_preamp),
                        value = preampPreview,
                        valueRange = MIN_REPLAY_GAIN_PREAMP_TENTHS_DB.toFloat()..
                            MAX_REPLAY_GAIN_PREAMP_TENTHS_DB.toFloat(),
                        steps = MAX_REPLAY_GAIN_PREAMP_TENTHS_DB - MIN_REPLAY_GAIN_PREAMP_TENTHS_DB - 1,
                        valueText = preampPreview.roundToInt().formatTenthsDb(),
                        onValueChange = { preampPreview = it },
                        onValueChangeFinished = {
                            onAction(
                                SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(
                                        replayGainPreampTenthsDb = preampPreview.roundToInt(),
                                    ),
                                )
                            )
                        },
                    )
                }
            }
        }

        SmallTitle(
            text = stringResource(Res.string.settings_player_interaction_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            val interaction = settings.playerInteraction
            SwitchPreference(
                title = stringResource(Res.string.settings_player_immersive_album_cover),
                summary = stringResource(Res.string.settings_player_immersive_album_cover_summary),
                checked = interaction.immersiveAlbumCoverEnabled,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(immersiveAlbumCoverEnabled = it)
                        )
                    )
                },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_player_audio_reactive_background),
                summary = stringResource(Res.string.settings_player_audio_reactive_background_summary),
                checked = interaction.audioReactiveBackgroundEnabled,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(audioReactiveBackgroundEnabled = it)
                        )
                    )
                },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_player_cover_swipe),
                checked = interaction.coverSwipeEnabled,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(coverSwipeEnabled = it)
                        )
                    )
                },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_player_total_duration),
                checked = interaction.showTotalDuration,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(showTotalDuration = it)
                        )
                    )
                },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_player_audio_technical_info),
                summary = stringResource(Res.string.settings_player_audio_technical_info_summary),
                checked = interaction.showAudioTechnicalInfo,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(showAudioTechnicalInfo = it)
                        )
                    )
                },
            )
            if (capabilities.desktopShortcutsSupported) {
                SwitchPreference(
                    title = stringResource(Res.string.settings_desktop_shortcuts),
                    summary = stringResource(Res.string.settings_desktop_shortcuts_summary),
                    checked = interaction.desktopShortcutsEnabled,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetPlayerInteractionSettings(
                                interaction.copy(desktopShortcutsEnabled = it)
                            )
                        )
                    },
                )
            }
        }

        if (capabilities.audioEffectsSupported) {
            val effects = settings.audioEffects
            SmallTitle(
                text = stringResource(Res.string.settings_audio_processing_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                ArrowPreference(
                    title = stringResource(Res.string.settings_equalizer_section),
                    summary = effects.localizedEqualizerSummary(),
                    onClick = onNavigateToEqualizer,
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_audio_effects_title),
                    summary = effects.localizedAudioEffectsSummary(),
                    onClick = onNavigateToAudioEffects,
                )
            }
        }
    }
}

@Composable
private fun io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
    .localizedEqualizerSummary(): String {
    val summary = equalizerUiSummary()
    if (!summary.enabled) return stringResource(Res.string.settings_audio_processing_off)
    return when (summary.mode) {
        EqualizerMode.Graphic -> {
            val presetName = summary.presetId?.let { id -> equalizerPresetName(id) }
            if (presetName != null) {
                presetName
            } else {
                stringResource(
                    Res.string.settings_equalizer_custom_bands,
                    summary.bandCount,
                )
            }
        }
        EqualizerMode.Parametric -> stringResource(
            Res.string.settings_equalizer_parametric_bands,
            summary.bandCount,
        )
    }
}

@Composable
private fun io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
    .localizedAudioEffectsSummary(): String {
    if (!enabled) return stringResource(Res.string.settings_audio_processing_off)
    val modules = activeAudioEffectModules()
    return when {
        modules.isEmpty() -> stringResource(Res.string.settings_audio_effects_none_enabled)
        modules.size == 1 -> modules.first().localizedName()
        modules.size == 2 ->
            modules.first().localizedName() + " · " + modules.last().localizedName()
        else -> stringResource(Res.string.settings_audio_effects_enabled_count, modules.size)
    }
}

@Composable
internal fun equalizerPresetName(id: String): String? = when (id) {
    "flat" -> stringResource(Res.string.settings_eq_preset_flat)
    "pop" -> stringResource(Res.string.settings_eq_preset_pop)
    "rock" -> stringResource(Res.string.settings_eq_preset_rock)
    "jazz" -> stringResource(Res.string.settings_eq_preset_jazz)
    "classical" -> stringResource(Res.string.settings_eq_preset_classical)
    "vocal" -> stringResource(Res.string.settings_eq_preset_vocal)
    "bass_boost" -> stringResource(Res.string.settings_eq_preset_bass_boost)
    "treble_boost" -> stringResource(Res.string.settings_eq_preset_treble_boost)
    else -> null
}

@Composable
internal fun AudioEffectModule.localizedName(): String = when (this) {
    AudioEffectModule.Tone -> stringResource(Res.string.settings_tone_enabled)
    AudioEffectModule.Loudness -> stringResource(Res.string.settings_loudness)
    AudioEffectModule.MonoBass -> stringResource(Res.string.settings_mono_bass)
    AudioEffectModule.DynamicEqualizer -> stringResource(Res.string.settings_dynamic_eq)
    AudioEffectModule.MoogFilter -> stringResource(Res.string.settings_moog_section)
    AudioEffectModule.Compressor -> stringResource(Res.string.settings_compressor)
    AudioEffectModule.Reverb -> stringResource(Res.string.settings_reverb)
    AudioEffectModule.StereoWidth -> stringResource(Res.string.settings_stereo_width)
    AudioEffectModule.Crossfeed -> stringResource(Res.string.settings_crossfeed)
    AudioEffectModule.Spatial -> stringResource(Res.string.settings_spatial_mode)
    AudioEffectModule.SpeakerOutput -> stringResource(Res.string.settings_speaker_output)
    AudioEffectModule.Limiter -> stringResource(Res.string.settings_peak_limiter)
}

private fun Int.formatTenthsDb(): String {
    val sign = if (this > 0) "+" else ""
    return "$sign${this / 10}.${kotlin.math.abs(this % 10)} dB"
}

private fun AudioFocusMode.titleResource() = when (this) {
    AudioFocusMode.Pause -> Res.string.settings_audio_focus_pause
    AudioFocusMode.Duck -> Res.string.settings_audio_focus_duck
    AudioFocusMode.Mix -> Res.string.settings_audio_focus_mix
}

private fun StartupPlaybackMode.titleResource() = when (this) {
    StartupPlaybackMode.Off -> Res.string.settings_startup_off
    StartupPlaybackMode.ResumeLastQueue -> Res.string.settings_startup_resume
    StartupPlaybackMode.ShuffleLibrary -> Res.string.settings_startup_shuffle
}

private fun PreviousButtonBehavior.titleResource() = when (this) {
    PreviousButtonBehavior.PreviousTrack -> Res.string.settings_previous_track
    PreviousButtonBehavior.RestartCurrentTrack -> Res.string.settings_previous_restart
}

private fun PlayNextMode.titleResource() = when (this) {
    PlayNextMode.FirstRequestedFirst -> Res.string.settings_play_next_fifo
    PlayNextMode.LastRequestedFirst -> Res.string.settings_play_next_lifo
}

private fun ShuffleStrategy.titleResource() = when (this) {
    ShuffleStrategy.QueueOrder -> Res.string.settings_shuffle_queue
    ShuffleStrategy.TrueRandom -> Res.string.settings_shuffle_true_random
}

private fun ReplayGainMode.titleResource() = when (this) {
    ReplayGainMode.Off -> Res.string.settings_replay_gain_off
    ReplayGainMode.Track -> Res.string.settings_replay_gain_track
    ReplayGainMode.Album -> Res.string.settings_replay_gain_album
    ReplayGainMode.Auto -> Res.string.settings_replay_gain_auto
}
