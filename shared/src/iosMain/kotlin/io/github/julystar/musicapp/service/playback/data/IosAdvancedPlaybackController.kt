package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDevice
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceType
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineCapabilities
import io.github.julystar.musicapp.service.playback.domain.PlaybackEnhancementSettings
import io.github.julystar.musicapp.service.playback.domain.PlaybackFeature
import io.github.julystar.musicapp.service.playback.domain.ReplayGainMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionPortAirPlay
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortBuiltInReceiver
import platform.AVFAudio.AVAudioSessionPortBuiltInSpeaker
import platform.AVFAudio.AVAudioSessionPortCarAudio
import platform.AVFAudio.AVAudioSessionPortHDMI
import platform.AVFAudio.AVAudioSessionPortHeadphones
import platform.AVFAudio.AVAudioSessionPortUSBAudio
import platform.AVFAudio.currentRoute

class IosAdvancedPlaybackController : AdvancedPlaybackController {
    private val mutableOutputState = MutableStateFlow(readOutputState())
    private val mutableEnhancements = MutableStateFlow(PlaybackEnhancementSettings.Default)

    override val capabilities: StateFlow<PlaybackEngineCapabilities> = MutableStateFlow(
        PlaybackEngineCapabilities(
            supportedFeatures = setOf(
                PlaybackFeature.AirPlay,
                PlaybackFeature.CarPlayNowPlaying,
            )
        )
    ).asStateFlow()
    override val enhancementSettings: StateFlow<PlaybackEnhancementSettings> =
        mutableEnhancements.asStateFlow()
    override val outputState: StateFlow<AudioOutputState> = mutableOutputState.asStateFlow()

    override fun selectOutputDevice(deviceId: AudioOutputDeviceId?) {
        // Route selection is owned by AVRoutePickerView and the iOS route chooser.
        refreshOutputDevices()
    }

    override fun refreshOutputDevices() {
        mutableOutputState.value = readOutputState()
    }

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
}

private fun readOutputState(): AudioOutputState {
    val outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        .filterIsInstance<AVAudioSessionPortDescription>()
    val devices = outputs.map { output ->
        val portType = output.portType.orEmpty()
        AudioOutputDevice(
            id = AudioOutputDeviceId("ios:${output.UID}"),
            name = output.portName,
            type = portType.toAudioOutputDeviceType(),
            isSystemDefault = portType == AVAudioSessionPortBuiltInSpeaker ||
                portType == AVAudioSessionPortBuiltInReceiver,
        )
    }
    return AudioOutputState(
        devices = devices,
        selectedDeviceId = devices.firstOrNull()?.id,
    )
}

private fun String.toAudioOutputDeviceType(): AudioOutputDeviceType = when (this) {
    AVAudioSessionPortAirPlay -> AudioOutputDeviceType.AirPlay
    AVAudioSessionPortBluetoothA2DP,
    AVAudioSessionPortBluetoothHFP,
    AVAudioSessionPortBluetoothLE -> AudioOutputDeviceType.Bluetooth
    AVAudioSessionPortUSBAudio -> AudioOutputDeviceType.Usb
    AVAudioSessionPortHDMI -> AudioOutputDeviceType.Hdmi
    AVAudioSessionPortCarAudio -> AudioOutputDeviceType.CarPlay
    AVAudioSessionPortBuiltInSpeaker,
    AVAudioSessionPortBuiltInReceiver,
    AVAudioSessionPortHeadphones -> AudioOutputDeviceType.BuiltIn
    else -> AudioOutputDeviceType.Unknown
}
