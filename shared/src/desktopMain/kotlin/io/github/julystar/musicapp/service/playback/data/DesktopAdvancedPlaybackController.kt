package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineCapabilities
import io.github.julystar.musicapp.service.playback.domain.PlaybackEnhancementSettings
import io.github.julystar.musicapp.service.playback.domain.PlaybackFeature
import io.github.julystar.musicapp.service.playback.domain.ReplayGainMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop implementation of [AdvancedPlaybackController].
 *
 * Wraps [DesktopAudioOutputController] for device enumeration and adds
 * playback enhancement control (gapless, crossfade, ReplayGain).
 *
 * Enhancement effects need to be applied through the Rust/FFI rodio backend.
 * The Rust `DesktopRodioPlayer` needs corresponding methods
 * (setGapless, setCrossfade, setReplayGain) exposed via uniffi.
 * Until those are available, settings are stored in-memory only.
 */
class DesktopAdvancedPlaybackController(
    private val audioOutput: DesktopAudioOutputController,
) : AdvancedPlaybackController {
    private val _capabilities = MutableStateFlow(
        PlaybackEngineCapabilities(
            supportedFeatures = setOf(
                PlaybackFeature.GaplessPlayback,
                PlaybackFeature.Crossfade,
                PlaybackFeature.ReplayGain,
                PlaybackFeature.OutputDeviceSelection,
            )
        )
    )

    private val _enhancementSettings = MutableStateFlow(PlaybackEnhancementSettings())

    override val capabilities: StateFlow<PlaybackEngineCapabilities>
        get() = _capabilities.asStateFlow()

    override val enhancementSettings: StateFlow<PlaybackEnhancementSettings>
        get() = _enhancementSettings.asStateFlow()

    override val outputState: StateFlow<AudioOutputState>
        get() = audioOutput.outputState

    override fun setGaplessEnabled(enabled: Boolean) {
        _enhancementSettings.value = _enhancementSettings.value.copy(
            gaplessEnabled = enabled,
        )
    }

    override fun setCrossfadeDurationMs(durationMs: Long) {
        _enhancementSettings.value = _enhancementSettings.value.copy(
            crossfadeDurationMs = durationMs.coerceIn(
                PlaybackEnhancementSettings.MIN_CROSSFADE_MS,
                PlaybackEnhancementSettings.MAX_CROSSFADE_MS,
            ),
        )
    }

    override fun setReplayGainMode(mode: ReplayGainMode) {
        _enhancementSettings.value = _enhancementSettings.value.copy(
            replayGainMode = mode,
        )
    }

    override fun setReplayGainPreampDb(preampDb: Float) {
        _enhancementSettings.value = _enhancementSettings.value.copy(
            replayGainPreampDb = preampDb.coerceIn(
                PlaybackEnhancementSettings.MIN_REPLAY_GAIN_PREAMP_DB,
                PlaybackEnhancementSettings.MAX_REPLAY_GAIN_PREAMP_DB,
            ),
        )
    }

    override fun selectOutputDevice(deviceId: AudioOutputDeviceId?) {
        audioOutput.selectOutputDevice(deviceId)
    }

    override fun refreshOutputDevices() {
        audioOutput.refreshOutputDevices()
    }
}
