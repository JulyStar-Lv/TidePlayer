package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineCapabilities
import io.github.julystar.musicapp.service.playback.domain.PlaybackEnhancementSettings
import io.github.julystar.musicapp.service.playback.domain.ReplayGainMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAdvancedPlaybackController : AdvancedPlaybackController {
    private val mutableEnhancements = MutableStateFlow(PlaybackEnhancementSettings.Default)

    override val capabilities: StateFlow<PlaybackEngineCapabilities> =
        MutableStateFlow(PlaybackEngineCapabilities.None).asStateFlow()
    override val enhancementSettings: StateFlow<PlaybackEnhancementSettings> =
        mutableEnhancements.asStateFlow()
    override val outputState: StateFlow<AudioOutputState> =
        MutableStateFlow(AudioOutputState.Empty).asStateFlow()

    override fun setGaplessEnabled(enabled: Boolean) {
        mutableEnhancements.value = mutableEnhancements.value.copy(gaplessEnabled = enabled)
    }

    override fun setCrossfadeDurationMs(durationMs: Long) {
        mutableEnhancements.value = mutableEnhancements.value.copy(
            crossfadeDurationMs = durationMs.coerceIn(
                PlaybackEnhancementSettings.MIN_CROSSFADE_MS,
                PlaybackEnhancementSettings.MAX_CROSSFADE_MS,
            )
        )
    }

    override fun setReplayGainMode(mode: ReplayGainMode) {
        mutableEnhancements.value = mutableEnhancements.value.copy(replayGainMode = mode)
    }

    override fun setReplayGainPreampDb(preampDb: Float) {
        mutableEnhancements.value = mutableEnhancements.value.copy(
            replayGainPreampDb = preampDb.coerceIn(
                PlaybackEnhancementSettings.MIN_REPLAY_GAIN_PREAMP_DB,
                PlaybackEnhancementSettings.MAX_REPLAY_GAIN_PREAMP_DB,
            )
        )
    }

    override fun selectOutputDevice(deviceId: AudioOutputDeviceId?) = Unit

    override fun refreshOutputDevices() = Unit
}
