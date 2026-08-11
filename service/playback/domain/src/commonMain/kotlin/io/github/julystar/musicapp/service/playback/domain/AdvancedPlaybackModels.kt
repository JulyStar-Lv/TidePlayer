package io.github.julystar.musicapp.service.playback.domain

import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmInline

interface AdvancedPlaybackController : AudioOutputController {
    val capabilities: StateFlow<PlaybackEngineCapabilities>
    val enhancementSettings: StateFlow<PlaybackEnhancementSettings>
    override val outputState: StateFlow<AudioOutputState>

    fun setGaplessEnabled(enabled: Boolean)
    fun setCrossfadeDurationMs(durationMs: Long)
    fun setReplayGainMode(mode: ReplayGainMode)
    fun setReplayGainPreampDb(preampDb: Float)
    override fun selectOutputDevice(deviceId: AudioOutputDeviceId?)
    override fun refreshOutputDevices()
}

data class PlaybackEngineCapabilities(
    val supportedFeatures: Set<PlaybackFeature> = emptySet(),
) {
    fun supports(feature: PlaybackFeature): Boolean {
        return feature in supportedFeatures
    }

    companion object {
        val None = PlaybackEngineCapabilities()
    }
}

enum class PlaybackFeature {
    GaplessPlayback,
    Crossfade,
    ReplayGain,
    OutputDeviceSelection,
    AndroidAuto,
    AirPlay,
    CarPlayNowPlaying,
    CarPlayLibraryBrowser,
}

data class PlaybackEnhancementSettings(
    val gaplessEnabled: Boolean = false,
    val crossfadeDurationMs: Long = 0,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreampDb: Float = 0f,
) {
    init {
        require(crossfadeDurationMs in MIN_CROSSFADE_MS..MAX_CROSSFADE_MS) {
            "crossfadeDurationMs must be in $MIN_CROSSFADE_MS..$MAX_CROSSFADE_MS"
        }
        require(replayGainPreampDb in MIN_REPLAY_GAIN_PREAMP_DB..MAX_REPLAY_GAIN_PREAMP_DB) {
            "replayGainPreampDb must be in $MIN_REPLAY_GAIN_PREAMP_DB..$MAX_REPLAY_GAIN_PREAMP_DB"
        }
    }

    val crossfadeEnabled: Boolean
        get() = crossfadeDurationMs > 0

    companion object {
        const val MIN_CROSSFADE_MS = 0L
        const val MAX_CROSSFADE_MS = 30_000L
        const val MIN_REPLAY_GAIN_PREAMP_DB = -20f
        const val MAX_REPLAY_GAIN_PREAMP_DB = 20f
        val Default = PlaybackEnhancementSettings()
    }
}

enum class ReplayGainMode {
    Off,
    Track,
    Album,
}

@JvmInline
value class AudioOutputDeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "AudioOutputDeviceId cannot be blank" }
    }
}

data class AudioOutputDevice(
    val id: AudioOutputDeviceId,
    val name: String,
    val type: AudioOutputDeviceType = AudioOutputDeviceType.Unknown,
    val isSystemDefault: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "AudioOutputDevice name cannot be blank" }
    }
}

enum class AudioOutputDeviceType {
    SystemDefault,
    BuiltIn,
    Bluetooth,
    Usb,
    Hdmi,
    Network,
    AirPlay,
    CarPlay,
    Unknown,
}

data class AudioOutputState(
    val devices: List<AudioOutputDevice> = emptyList(),
    val selectedDeviceId: AudioOutputDeviceId? = null,
) {
    val selectedDevice: AudioOutputDevice?
        get() = devices.firstOrNull { it.id == selectedDeviceId }

    companion object {
        val Empty = AudioOutputState()
    }
}
