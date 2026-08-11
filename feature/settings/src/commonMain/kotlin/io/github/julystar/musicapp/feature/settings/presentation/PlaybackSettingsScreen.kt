package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
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
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.core.domain.model.StartupPlaybackMode
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*

@Composable
fun PlaybackSettingsSection(
    state: SettingsUiState,
    audioOutputState: AudioOutputState,
    onSelectAudioOutput: (AudioOutputDeviceId?) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    val capabilities = state.capabilities

    SettingsPageLayout(title = stringResource(Res.string.settings_playback_title), onBack = onBack) {
        if (
            capabilities.audioOutputSelectionSupported ||
            capabilities.audioRoutePickerSupported
        ) {
            SettingsSection(title = stringResource(Res.string.settings_audio_output_section)) {
                SettingsInfoRow(
                    title = stringResource(Res.string.settings_current_audio_output),
                    value = audioOutputState.selectedDevice?.name
                        ?: stringResource(Res.string.settings_audio_output_unavailable),
                    onClick = onRefreshAudioOutputs,
                )
                if (
                    capabilities.audioOutputSelectionSupported &&
                    audioOutputState.devices.isNotEmpty()
                ) {
                    val selected = audioOutputState.selectedDevice ?: audioOutputState.devices.first()
                    SettingsSelectRow(
                        label = stringResource(Res.string.settings_select_audio_output),
                        selected = selected,
                        options = audioOutputState.devices,
                        optionLabel = { device ->
                            if (device.isSystemDefault) {
                                stringResource(Res.string.settings_audio_output_default, device.name)
                            } else {
                                device.name
                            }
                        },
                        onSelect = { onSelectAudioOutput(it.id) },
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
            SettingsSection(title = stringResource(Res.string.settings_audio_focus_section)) {
                SettingsSelectRow(
                    label = stringResource(Res.string.settings_audio_focus_section),
                    selected = settings.audioFocusMode,
                    options = AudioFocusMode.entries.toList(),
                    optionLabel = { mode -> stringResource(mode.titleResource()) },
                    onSelect = { onAction(SettingsAction.SetAudioFocusMode(it)) },
                )
            }
        }

        SettingsSection(title = stringResource(Res.string.settings_playback_behavior_section)) {
            if (capabilities.deviceDisconnectSupported) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_pause_disconnect),
                    summary = stringResource(Res.string.settings_pause_disconnect_summary),
                    checked = settings.pauseOnDisconnect,
                    onCheckedChange = { onAction(SettingsAction.SetPauseOnDisconnect(it)) },
                )
            }
            if (capabilities.gaplessPlaybackSupported) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_gapless),
                    summary = stringResource(Res.string.settings_gapless_summary),
                    checked = settings.gaplessPlaybackEnabled,
                    onCheckedChange = { onAction(SettingsAction.SetGaplessPlaybackEnabled(it)) },
                )
            }
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_retry_playback),
                summary = stringResource(Res.string.settings_retry_playback_summary),
                checked = settings.retryPlaybackOnFailure,
                onCheckedChange = { onAction(SettingsAction.SetRetryPlaybackOnFailure(it)) },
            )
            if (capabilities.networkStatusSupported) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_resume_network),
                    summary = stringResource(Res.string.settings_resume_network_summary),
                    checked = settings.resumePlaybackAfterNetworkRecovery,
                    onCheckedChange = {
                        onAction(SettingsAction.SetResumePlaybackAfterNetworkRecovery(it))
                    },
                )
            }
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_keep_screen_on),
                summary = stringResource(Res.string.settings_keep_screen_on_summary),
                checked = settings.keepScreenOnInPlayer,
                onCheckedChange = { onAction(SettingsAction.SetKeepScreenOnInPlayer(it)) },
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_playback_advanced_section)) {
            SettingsSwitchRow(
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
            SettingsSelectRow(
                label = stringResource(Res.string.settings_startup_playback),
                selected = settings.playbackAdvanced.startupPlaybackMode,
                options = StartupPlaybackMode.entries.toList(),
                optionLabel = { mode -> stringResource(mode.titleResource()) },
                onSelect = { mode ->
                    onAction(
                        SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(startupPlaybackMode = mode)
                        )
                    )
                },
            )
            SettingsSelectRow(
                label = stringResource(Res.string.settings_previous_button_behavior),
                selected = settings.playbackAdvanced.previousButtonBehavior,
                options = PreviousButtonBehavior.entries.toList(),
                optionLabel = { behavior -> stringResource(behavior.titleResource()) },
                onSelect = { behavior ->
                    onAction(
                        SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(previousButtonBehavior = behavior)
                        )
                    )
                },
            )
            SettingsSelectRow(
                label = stringResource(Res.string.settings_play_next_mode),
                selected = settings.playbackAdvanced.playNextMode,
                options = PlayNextMode.entries.toList(),
                optionLabel = { mode -> stringResource(mode.titleResource()) },
                onSelect = { mode ->
                    onAction(
                        SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(playNextMode = mode)
                        )
                    )
                },
            )
            SettingsSelectRow(
                label = stringResource(Res.string.settings_shuffle_strategy),
                selected = settings.playbackAdvanced.shuffleStrategy,
                options = ShuffleStrategy.entries.toList(),
                optionLabel = { strategy -> stringResource(strategy.titleResource()) },
                onSelect = { strategy ->
                    onAction(
                        SettingsAction.SetPlaybackAdvancedSettings(
                            settings.playbackAdvanced.copy(shuffleStrategy = strategy)
                        )
                    )
                },
            )
        }

        if (capabilities.crossfadeSupported || capabilities.replayGainSupported) {
            SettingsSection(title = stringResource(Res.string.settings_playback_enhancement_section)) {
                if (capabilities.crossfadeSupported) {
                    SettingsSliderRow(
                        title = stringResource(Res.string.settings_crossfade),
                        summary = stringResource(Res.string.settings_crossfade_summary),
                        value = settings.playbackAdvanced.crossfadeDurationMs / 1_000,
                        valueRange = 0..30,
                        valueText = stringResource(
                            Res.string.settings_seconds_value,
                            settings.playbackAdvanced.crossfadeDurationMs / 1_000,
                        ),
                        onValueChange = {
                            onAction(
                                SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(crossfadeDurationMs = it * 1_000)
                                )
                            )
                        },
                    )
                }
                if (capabilities.replayGainSupported) {
                    SettingsSelectRow(
                        label = stringResource(Res.string.settings_replay_gain),
                        selected = settings.playbackAdvanced.replayGainMode,
                        options = ReplayGainMode.entries.toList(),
                        optionLabel = { mode -> stringResource(mode.titleResource()) },
                        onSelect = { mode ->
                            onAction(
                                SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(replayGainMode = mode)
                                )
                            )
                        },
                    )
                    SettingsSliderRow(
                        title = stringResource(Res.string.settings_replay_gain_preamp),
                        value = settings.playbackAdvanced.replayGainPreampTenthsDb,
                        valueRange = MIN_REPLAY_GAIN_PREAMP_TENTHS_DB..
                            MAX_REPLAY_GAIN_PREAMP_TENTHS_DB,
                        valueText = settings.playbackAdvanced.replayGainPreampTenthsDb.formatTenthsDb(),
                        onValueChange = {
                            onAction(
                                SettingsAction.SetPlaybackAdvancedSettings(
                                    settings.playbackAdvanced.copy(replayGainPreampTenthsDb = it)
                                )
                            )
                        },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(Res.string.settings_player_interaction_section)) {
            val interaction = settings.playerInteraction
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_now_playing_entry),
                summary = stringResource(Res.string.settings_now_playing_entry_summary),
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            SettingsSwitchRow(
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
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_player_tap_progress),
                checked = interaction.tapProgressToSeekEnabled,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(tapProgressToSeekEnabled = it)
                        )
                    )
                },
            )
            SettingsSwitchRow(
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
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_player_song_annotation),
                checked = interaction.showSongAnnotation,
                onCheckedChange = {
                    onAction(
                        SettingsAction.SetPlayerInteractionSettings(
                            interaction.copy(showSongAnnotation = it)
                        )
                    )
                },
            )
            if (capabilities.desktopShortcutsSupported) {
                SettingsSwitchRow(
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
            AudioEffectsSettingsSection(state = state, onAction = onAction)
        }
    }
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
