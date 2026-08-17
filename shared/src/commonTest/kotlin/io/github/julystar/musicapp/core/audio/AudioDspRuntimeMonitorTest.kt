package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import io.github.julystar.musicapp.core.domain.model.AudioReactiveSnapshot
import io.github.julystar.musicapp.core.domain.repository.AudioMonitoringRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import uniffi.app_backend.DspRuntimeBypassReason
import uniffi.app_backend.DspRuntimeState
import uniffi.app_backend.DspSampleFormat
import uniffi.app_backend.NativeAudioReactiveSnapshot
import uniffi.app_backend.NativeDspRuntimeSnapshot

class AudioDspRuntimeMonitorTest {
    @Test
    fun requesterFlowSeparatesDiagnosticsAndVisualizationCadence() = runTest {
        assertEquals(150L, AUDIO_DSP_DIAGNOSTICS_INTERVAL_MS)
        assertEquals(33L, AUDIO_REACTIVE_VISUALIZATION_INTERVAL_MS)
        val requesters = flowOf(
            emptySet(),
            setOf(AudioMonitoringRequester.Diagnostics),
            setOf(AudioMonitoringRequester.Visualization),
        )

        assertEquals(
            listOf(false, true, false),
            requesters.monitoringRequested(AudioMonitoringRequester.Diagnostics).toList(),
        )
        assertEquals(
            listOf(false, true),
            requesters.monitoringRequested(AudioMonitoringRequester.Visualization).toList(),
        )
    }

    @Test
    fun reactiveMapperClampsNonFiniteValuesAndMonitorResets() {
        val mapped = NativeAudioReactiveSnapshot(
            level = Float.POSITIVE_INFINITY,
            beat = -0.5f,
        ).toDomainAudioReactiveSnapshot()

        assertEquals(0f, mapped.level)
        assertEquals(0f, mapped.beat)

        try {
            AudioReactiveMonitor.publish(AudioReactiveSnapshot(level = 1.5f, beat = 0.5f))
            assertEquals(1f, AudioReactiveMonitor.snapshot.value.level)
            assertEquals(0.5f, AudioReactiveMonitor.snapshot.value.beat)
        } finally {
            AudioReactiveMonitor.reset()
        }
        assertEquals(AudioReactiveSnapshot(), AudioReactiveMonitor.snapshot.value)
    }

    @Test
    fun monitoringRequestersAreIndependentAndIdempotent() {
        try {
            AudioDspRuntimeMonitor.requestMonitoring(AudioMonitoringRequester.Diagnostics)
            assertTrue(AudioDspRuntimeMonitor.monitoringRequesters.value.isNotEmpty())
            AudioDspRuntimeMonitor.requestMonitoring(AudioMonitoringRequester.Diagnostics)
            assertEquals(
                setOf(AudioMonitoringRequester.Diagnostics),
                AudioDspRuntimeMonitor.monitoringRequesters.value,
            )

            AudioDspRuntimeMonitor.requestMonitoring(AudioMonitoringRequester.Visualization)
            assertEquals(
                setOf(
                    AudioMonitoringRequester.Diagnostics,
                    AudioMonitoringRequester.Visualization,
                ),
                AudioDspRuntimeMonitor.monitoringRequesters.value,
            )

            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Diagnostics)
            assertEquals(
                setOf(AudioMonitoringRequester.Visualization),
                AudioDspRuntimeMonitor.monitoringRequesters.value,
            )
            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Diagnostics)

            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Visualization)
            assertTrue(AudioDspRuntimeMonitor.monitoringRequesters.value.isEmpty())
            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Visualization)
        } finally {
            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Diagnostics)
            AudioDspRuntimeMonitor.releaseMonitoring(AudioMonitoringRequester.Visualization)
        }

        assertTrue(AudioDspRuntimeMonitor.monitoringRequesters.value.isEmpty())
    }

    @Test
    fun mapsNativeStatusMeterAndTimingWithoutLosingUnits() {
        val snapshot = nativeSnapshot(
            state = DspRuntimeState.BYPASSED,
            bypassReason = DspRuntimeBypassReason.UNSUPPORTED_SAMPLE_FORMAT,
        ).toDomainAudioDspRuntimeSnapshot()

        assertEquals(AudioDspRuntimeState.Bypassed, snapshot.status.state)
        assertEquals(AudioDspBypassReason.UnsupportedSampleFormat, snapshot.status.bypassReason)
        assertEquals(96_000, snapshot.status.sampleRate)
        assertEquals(2, snapshot.status.channelCount)
        assertEquals(AudioSampleFormat.Float32, snapshot.status.sampleFormat)
        assertEquals(288, snapshot.status.latencyFrames)
        assertEquals(-3.5f, snapshot.meter.inputPeakDb)
        assertEquals(4L, snapshot.meter.clippedSamples)
        assertEquals(12.5f, snapshot.performance.averageProcessingTimeUs)
        assertEquals(0.025f, snapshot.performance.deadlineUtilization)
    }

    @Test
    fun unknownFormatAndNoReasonMapToNull() {
        val snapshot = nativeSnapshot(
            state = DspRuntimeState.INACTIVE,
            bypassReason = DspRuntimeBypassReason.NONE,
            sampleFormat = DspSampleFormat.UNKNOWN,
        ).toDomainAudioDspRuntimeSnapshot()

        assertNull(snapshot.status.sampleFormat)
        assertNull(snapshot.status.bypassReason)
    }

    private fun nativeSnapshot(
        state: DspRuntimeState,
        bypassReason: DspRuntimeBypassReason,
        sampleFormat: DspSampleFormat = DspSampleFormat.FLOAT32,
    ) = NativeDspRuntimeSnapshot(
        state = state,
        sampleRate = 96_000u,
        channelCount = 2u,
        sampleFormat = sampleFormat,
        bypassReason = bypassReason,
        lastErrorCode = 0,
        latencyFrames = 288u,
        inputPeakDb = -3.5f,
        outputPeakDb = -1f,
        compressorGainReductionDb = 2f,
        limiterGainReductionDb = 1.5f,
        clippedSamples = 4uL,
        nonFiniteRecoveryCount = 1uL,
        appliedHeadroomDb = -6f,
        processCount = 8uL,
        averageProcessingTimeUs = 12.5f,
        maxProcessingTimeUs = 20f,
        bufferDurationUs = 500f,
        deadlineUtilization = 0.025f,
    )
}
