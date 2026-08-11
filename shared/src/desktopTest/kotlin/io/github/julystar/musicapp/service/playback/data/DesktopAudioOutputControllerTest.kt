package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.service.playback.domain.AudioOutputDeviceId
import io.github.julystar.musicapp.singleton.DesktopAudioOutputDescriptor
import io.github.julystar.musicapp.singleton.DesktopAudioOutputSelectionResult
import io.github.julystar.musicapp.singleton.DesktopPlaybackEngine
import io.github.julystar.musicapp.singleton.NoopDesktopPlaybackEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAudioOutputControllerTest {
    @Test
    fun `selection publishes the backend current device only after success`() = runTest {
        val engine = RecordingAudioOutputEngine()
        val toast = RecordingToastRepository()
        val controller = DesktopAudioOutputController(
            playbackEngine = engine,
            toastRepository = toast,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        controller.selectOutputDevice(AudioOutputDeviceId("cpal:usb"))
        advanceUntilIdle()

        assertEquals(AudioOutputDeviceId("cpal:usb"), controller.outputState.value.selectedDeviceId)
        assertEquals(listOf<String?>("cpal:usb"), engine.selectionRequests)
        assertEquals(emptyList<UiMessage>(), toast.emitted)
    }

    @Test
    fun `failed selection keeps backend state and emits a localized message`() = runTest {
        val engine = RecordingAudioOutputEngine().apply {
            selectionResult = DesktopAudioOutputSelectionResult.OpenFailed
        }
        val toast = RecordingToastRepository()
        val controller = DesktopAudioOutputController(
            playbackEngine = engine,
            toastRepository = toast,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        controller.selectOutputDevice(AudioOutputDeviceId("cpal:usb"))
        advanceUntilIdle()

        assertEquals(
            AudioOutputDeviceId("cpal:built-in"),
            controller.outputState.value.selectedDeviceId,
        )
        assertEquals(
            listOf<UiMessage>(UiMessage.Resource(UiMessageKey.AudioOutputSwitchFailed)),
            toast.emitted,
        )
    }
}

private class RecordingAudioOutputEngine :
    DesktopPlaybackEngine by NoopDesktopPlaybackEngine() {
    private val devices = listOf(
        DesktopAudioOutputDescriptor("cpal:built-in", "Built-in Speakers", true),
        DesktopAudioOutputDescriptor("cpal:usb", "USB DAC", false),
    )
    var current = devices.first()
    var selectionResult = DesktopAudioOutputSelectionResult.Ready
    val selectionRequests = mutableListOf<String?>()

    override fun listAudioOutputDevices(): List<DesktopAudioOutputDescriptor> = devices

    override fun refreshAudioOutputDevices(): List<DesktopAudioOutputDescriptor> = devices

    override fun currentAudioOutputDevice(): DesktopAudioOutputDescriptor = current

    override fun selectAudioOutputDevice(deviceId: String?): DesktopAudioOutputSelectionResult {
        selectionRequests += deviceId
        if (selectionResult == DesktopAudioOutputSelectionResult.Ready) {
            current = devices.first { it.id == deviceId }
        }
        return selectionResult
    }
}

private class RecordingToastRepository : ToastRepository {
    private val flow = MutableSharedFlow<UiMessage>()
    override val messages: SharedFlow<UiMessage> = flow
    val emitted = mutableListOf<UiMessage>()

    override fun emit(message: UiMessage) {
        emitted += message
    }
}
