package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.ParametricEqBand
import io.github.julystar.musicapp.core.domain.model.ParametricEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineFailureReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineResource
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineUnsupportedReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.app_backend.DspConfiguration
import uniffi.app_backend.DspEqMode
import uniffi.app_backend.NativeAudioReactiveSnapshot

class DesktopPlaybackEngineTest {
    @Test
    fun noopEngineReportsUnsupportedWithoutPlaybackState() {
        val engine = NoopDesktopPlaybackEngine()

        assertEquals(
            PlaybackEngineLoadResult.Unsupported(
                PlaybackEngineUnsupportedReason.MissingPlatformEngine
            ),
            engine.load(loadRequest()),
        )
        engine.play()
        engine.pause()
        engine.seekTo(1_000)
        engine.stop()

        assertEquals(0L, engine.readPosition().positionMs)
        assertEquals(0L, engine.readPosition().bufferedMs)
        assertEquals(0L, engine.readPosition().durationMs)
    }

    @Test
    fun rodioEngineDelegatesPlaybackCommandsToRuntime() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true)
        val engine = RodioDesktopPlaybackEngine(runtime)

        assertEquals(
            PlaybackEngineLoadResult.Ready,
            engine.load(loadRequest()),
        )
        engine.play()
        engine.pause()
        engine.seekTo(2_500)
        engine.stop()

        assertEquals(listOf("http://127.0.0.1/track.flac"), runtime.loadedUris)
        assertEquals(listOf(emptyMap()), runtime.loadedHeaders)
        assertEquals(1, runtime.playCalls)
        assertEquals(1, runtime.pauseCalls)
        assertEquals(listOf(2_500UL), runtime.seekCalls)
        assertEquals(1, runtime.stopCalls)
        assertEquals(1_000L, engine.readPosition().positionMs)
        assertEquals(1_000L, engine.readPosition().bufferedMs)
        assertEquals(123_000L, engine.readPosition().durationMs)
    }

    @Test
    fun rodioEngineConsumesNativePlaybackCompletion() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true)
        val engine = RodioDesktopPlaybackEngine(runtime)
        runtime.playbackCompleted = true

        assertTrue(engine.takePlaybackCompleted())
        assertFalse(engine.takePlaybackCompleted())
    }

    @Test
    fun rodioEnginePassesThroughReactiveSnapshot() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true).apply {
            reactiveSnapshot = NativeAudioReactiveSnapshot(level = 0.75f, beat = 1.2f)
        }
        val engine = RodioDesktopPlaybackEngine(runtime)

        assertEquals(0.75f, engine.audioReactiveSnapshot().level)
        assertEquals(1f, engine.audioReactiveSnapshot().beat)
    }

    @Test
    fun rodioEngineDelegatesAudioOutputStateAndSelection() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true).apply {
            audioDevices = listOf(
                DesktopAudioOutputDescriptor("cpal:one", "Speakers", true),
                DesktopAudioOutputDescriptor("cpal:two", "USB DAC", false),
            )
            currentAudioDevice = audioDevices.first()
            audioSelectionResult = DesktopAudioOutputSelectionResult.Ready
        }
        val engine = RodioDesktopPlaybackEngine(runtime)

        assertEquals(runtime.audioDevices, engine.listAudioOutputDevices())
        assertEquals(runtime.currentAudioDevice, engine.currentAudioOutputDevice())
        assertEquals(
            DesktopAudioOutputSelectionResult.Ready,
            engine.selectAudioOutputDevice("cpal:two"),
        )
        assertEquals(listOf<String?>("cpal:two"), runtime.selectedAudioDeviceIds)
    }

    @Test
    fun rodioEnginePassesPlaybackHeadersToRuntime() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true)
        val engine = RodioDesktopPlaybackEngine(runtime)
        val request = loadRequest(
            headers = mapOf(
                "Authorization" to "Bearer token",
                "User-Agent" to "TidePlayer",
            )
        )

        assertEquals(PlaybackEngineLoadResult.Ready, engine.load(request))

        assertEquals(listOf(request.resource.uri), runtime.loadedUris)
        assertEquals(listOf(request.resource.headers), runtime.loadedHeaders)
    }

    @Test
    fun rodioEngineReportsUnsupportedWhenRuntimeCannotLoad() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = false)
        val engine = RodioDesktopPlaybackEngine(runtime)

        assertEquals(
            PlaybackEngineLoadResult.Unsupported(
                PlaybackEngineUnsupportedReason.UnsupportedResource
            ),
            engine.load(loadRequest()),
        )
        assertFalse(runtime.loaded)
        assertEquals(listOf("http://127.0.0.1/track.flac"), runtime.loadedUris)
    }

    @Test
    fun rodioEngineDoesNotLoadExpiredResource() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true)
        val engine = RodioDesktopPlaybackEngine(runtime)
        val request = loadRequest().copy(
            resource = loadRequest().resource.copy(expiresAtEpochMs = 1)
        )

        assertEquals(
            PlaybackEngineLoadResult.Failure(PlaybackEngineFailureReason.ExpiredResource),
            engine.load(request),
        )
        assertEquals(emptyList(), runtime.loadedUris)
    }

    @Test
    fun rodioEnginePassesTheCompleteSharedDspConfiguration() {
        val runtime = RecordingDesktopRodioRuntime(loadResult = true)
        val engine = RodioDesktopPlaybackEngine(runtime)
        val effects = AudioEffectSettings.Default.copy(enabled = true)
            .withAudioEffectProfile(
                AudioEffectProfile.Default.copy(
                    equalizerMode = EqualizerMode.Parametric,
                    parametricEqualizer = ParametricEqualizerSettings(
                        enabled = true,
                        bands = listOf(
                            ParametricEqBand(
                                frequencyHz = 2_500,
                                gainTenthsDb = 35,
                                qHundredths = 175,
                            )
                        ),
                    ),
                    moogFilter = AudioEffectProfile.Default.moogFilter.copy(enabled = true),
                )
            )

        engine.configureAudioProcessing(
            effects = effects,
            playback = PlaybackAdvancedSettings.Default.copy(crossfadeDurationMs = 2_000),
            replayGainDb = -4.5f,
        )

        val config = requireNotNull(runtime.configuredDsp)
        assertEquals(DspEqMode.PARAMETRIC, config.equalizerMode)
        assertEquals(2_500f, config.parametricEqualizer.bands.single().frequencyHz)
        assertEquals(3.5f, config.parametricEqualizer.bands.single().gainDb)
        assertTrue(config.moogFilter.enabled)
        assertEquals(-4.5f, config.inputGainDb)
        assertEquals(2_000UL, runtime.configuredCrossfadeMs)
    }

    private fun loadRequest(
        headers: Map<String, String> = emptyMap(),
    ): PlaybackEngineLoadRequest {
        return PlaybackEngineLoadRequest(
            item = PlayableItem(title = "Track", libraryTrackId = 1),
            resource = PlaybackEngineResource(
                uri = "http://127.0.0.1/track.flac",
                headers = headers,
            ),
        )
    }
}

private class RecordingDesktopRodioRuntime(
    private val loadResult: Boolean,
) : DesktopRodioRuntime {
    val loadedUris = mutableListOf<String>()
    val loadedHeaders = mutableListOf<Map<String, String>>()
    val seekCalls = mutableListOf<ULong>()
    var loaded = false
        private set
    var playCalls = 0
        private set
    var pauseCalls = 0
        private set
    var stopCalls = 0
        private set
    var playbackCompleted = false
    var reactiveSnapshot = NativeAudioReactiveSnapshot(level = 0f, beat = 0f)
    var configuredDsp: DspConfiguration? = null
        private set
    var configuredCrossfadeMs: ULong? = null
        private set
    var audioDevices: List<DesktopAudioOutputDescriptor> = emptyList()
    var currentAudioDevice: DesktopAudioOutputDescriptor? = null
    var audioSelectionResult = DesktopAudioOutputSelectionResult.Unsupported
    val selectedAudioDeviceIds = mutableListOf<String?>()

    override fun load(uri: String, headers: Map<String, String>): Boolean {
        loadedUris += uri
        loadedHeaders += headers
        loaded = loadResult
        return loadResult
    }

    override fun play() {
        assertTrue(loaded)
        playCalls += 1
    }

    override fun pause() {
        assertTrue(loaded)
        pauseCalls += 1
    }

    override fun stop() {
        loaded = false
        stopCalls += 1
    }

    override fun seek(ms: ULong) {
        assertTrue(loaded)
        seekCalls += ms
    }

    override fun currentPositionMs(): Long = 1_000L

    override fun bufferedPositionMs(): Long = 1_000L

    override fun durationMs(): Long = 123_000L

    override fun takePlaybackCompleted(): Boolean = playbackCompleted.also {
        playbackCompleted = false
    }

    override fun audioReactiveSnapshot(): NativeAudioReactiveSnapshot = reactiveSnapshot

    override fun listAudioOutputDevices(): List<DesktopAudioOutputDescriptor> = audioDevices

    override fun currentAudioOutputDevice(): DesktopAudioOutputDescriptor? = currentAudioDevice

    override fun refreshAudioOutputDevices(): List<DesktopAudioOutputDescriptor> = audioDevices

    override fun selectAudioOutputDevice(deviceId: String?): DesktopAudioOutputSelectionResult {
        selectedAudioDeviceIds += deviceId
        return audioSelectionResult
    }

    override fun configureAudioProcessing(
        config: DspConfiguration,
        crossfadeDurationMs: ULong,
    ) {
        configuredDsp = config
        configuredCrossfadeMs = crossfadeDurationMs
    }
}
