package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.app_backend.DspRuntimeBypassReason
import uniffi.app_backend.DspRuntimeState
import uniffi.app_backend.DspSampleFormat
import uniffi.app_backend.NativeDspRuntimeSnapshot

class AudioDspRuntimeMonitorTest {
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
