package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.service.playback.domain.AudioOutputController
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDevice
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceType
import io.github.julystar.musicapp.service.playback.domain.AudioOutputState
import io.github.julystar.musicapp.singleton.DesktopAudioOutputDescriptor
import io.github.julystar.musicapp.singleton.DesktopAudioOutputSelectionResult
import io.github.julystar.musicapp.singleton.DesktopPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopAudioOutputController(
    private val playbackEngine: DesktopPlaybackEngine,
    private val toastRepository: ToastRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioOutputController {
    private val _outputState = MutableStateFlow(AudioOutputState.Empty)

    override val outputState: StateFlow<AudioOutputState> = _outputState.asStateFlow()

    init {
        refreshOutputDevices()
    }

    override fun selectOutputDevice(deviceId: AudioOutputDeviceId?) {
        scope.launch(dispatcher) {
            when (val result = playbackEngine.selectAudioOutputDevice(deviceId?.value)) {
                DesktopAudioOutputSelectionResult.Ready -> publishBackendState(refresh = true)
                else -> {
                    AppLogger.error(
                        DiagnosticLogCategory.Playback,
                        "DesktopAudioOutputController",
                        "Desktop audio output switch failed",
                        "deviceId=${deviceId?.value ?: "system-default"}; result=$result",
                    )
                    toastRepository.emit(UiMessageKey.AudioOutputSwitchFailed)
                    publishBackendState(refresh = true)
                }
            }
        }
    }

    override fun refreshOutputDevices() {
        scope.launch(dispatcher) {
            publishBackendState(refresh = true)
        }
    }

    private fun publishBackendState(refresh: Boolean) {
        val descriptors = if (refresh) {
            playbackEngine.refreshAudioOutputDevices()
        } else {
            playbackEngine.listAudioOutputDevices()
        }
        val current = playbackEngine.currentAudioOutputDevice()
        _outputState.value = descriptors.toOutputState(current?.id)
    }
}

private fun List<DesktopAudioOutputDescriptor>.toOutputState(
    selectedId: String?,
): AudioOutputState = AudioOutputState(
    devices = map { descriptor ->
        AudioOutputDevice(
            id = AudioOutputDeviceId(descriptor.id),
            name = descriptor.name,
            type = classifyAudioOutput(descriptor.name),
            isSystemDefault = descriptor.isDefault,
        )
    },
    selectedDeviceId = selectedId?.let(::AudioOutputDeviceId),
)

private fun classifyAudioOutput(name: String): AudioOutputDeviceType {
    val normalized = name.lowercase()
    return when {
        "bluetooth" in normalized || " bt " in " $normalized " -> AudioOutputDeviceType.Bluetooth
        "usb" in normalized -> AudioOutputDeviceType.Usb
        "hdmi" in normalized || "displayport" in normalized -> AudioOutputDeviceType.Hdmi
        "airplay" in normalized -> AudioOutputDeviceType.AirPlay
        "network" in normalized || "dlna" in normalized || "chromecast" in normalized ->
            AudioOutputDeviceType.Network
        "speaker" in normalized || "headphone" in normalized || "built-in" in normalized ->
            AudioOutputDeviceType.BuiltIn
        else -> AudioOutputDeviceType.Unknown
    }
}
