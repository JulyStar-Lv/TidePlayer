package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.AudioDspRuntimeRepository
import io.github.julystar.musicapp.diagnostics.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.app_backend.DspRuntimeBypassReason
import uniffi.app_backend.DspRuntimeState
import uniffi.app_backend.DspSampleFormat
import uniffi.app_backend.NativeDspRuntimeSnapshot

object AudioDspRuntimeMonitor : AudioDspRuntimeRepository {
    private val mutableStatus = MutableStateFlow(AudioDspRuntimeStatus())
    private val mutableMeter = MutableStateFlow(AudioDspMeterSnapshot())
    private val mutablePerformance = MutableStateFlow(AudioDspPerformanceSnapshot())

    override val status = mutableStatus.asStateFlow()
    override val meter = mutableMeter.asStateFlow()
    override val performance = mutablePerformance.asStateFlow()

    fun publish(snapshot: AudioDspRuntimeSnapshot) {
        val previous = mutableStatus.value
        mutableStatus.value = snapshot.status
        mutableMeter.value = snapshot.meter
        mutablePerformance.value = snapshot.performance
        if (previous != snapshot.status) {
            AppLogger.info(
                category = DiagnosticLogCategory.Dsp,
                target = "AudioDspRuntimeMonitor",
                message = "DSP runtime status changed",
                fields = mapOf(
                    "state" to snapshot.status.state.name,
                    "sampleRate" to snapshot.status.sampleRate.toString(),
                    "channels" to snapshot.status.channelCount.toString(),
                    "sampleFormat" to snapshot.status.sampleFormat?.name.orEmpty(),
                    "bypassReason" to snapshot.status.bypassReason?.name.orEmpty(),
                    "errorCode" to snapshot.status.lastErrorCode.toString(),
                ),
            )
        }
    }

    fun publishStatus(status: AudioDspRuntimeStatus) {
        publish(AudioDspRuntimeSnapshot(status = status))
    }

    fun reset() {
        publish(AudioDspRuntimeSnapshot())
    }
}

fun NativeDspRuntimeSnapshot.toDomainAudioDspRuntimeSnapshot(): AudioDspRuntimeSnapshot {
    return AudioDspRuntimeSnapshot(
        status = AudioDspRuntimeStatus(
            state = when (state) {
                DspRuntimeState.INACTIVE -> AudioDspRuntimeState.Inactive
                DspRuntimeState.ACTIVE -> AudioDspRuntimeState.Active
                DspRuntimeState.BYPASSED -> AudioDspRuntimeState.Bypassed
                DspRuntimeState.UNAVAILABLE -> AudioDspRuntimeState.Unavailable
                DspRuntimeState.ERROR -> AudioDspRuntimeState.Error
            },
            sampleRate = sampleRate.takeIf { it > 0u }?.toInt(),
            channelCount = channelCount.takeIf { it > 0u }?.toInt(),
            sampleFormat = when (sampleFormat) {
                DspSampleFormat.UNKNOWN -> null
                DspSampleFormat.PCM16 -> AudioSampleFormat.Pcm16
                DspSampleFormat.FLOAT32 -> AudioSampleFormat.Float32
            },
            bypassReason = when (bypassReason) {
                DspRuntimeBypassReason.NONE -> null
                DspRuntimeBypassReason.EFFECTS_DISABLED -> AudioDspBypassReason.EffectsDisabled
                DspRuntimeBypassReason.UNSUPPORTED_SAMPLE_FORMAT ->
                    AudioDspBypassReason.UnsupportedSampleFormat
                DspRuntimeBypassReason.UNSUPPORTED_CHANNEL_COUNT ->
                    AudioDspBypassReason.UnsupportedChannelCount
                DspRuntimeBypassReason.UNSUPPORTED_SAMPLE_RATE ->
                    AudioDspBypassReason.UnsupportedSampleRate
                DspRuntimeBypassReason.PLATFORM_PROCESSING_UNAVAILABLE ->
                    AudioDspBypassReason.PlatformProcessingUnavailable
                DspRuntimeBypassReason.PROTECTED_CONTENT -> AudioDspBypassReason.ProtectedContent
                DspRuntimeBypassReason.AUDIO_TAP_UNAVAILABLE ->
                    AudioDspBypassReason.AudioTapUnavailable
                DspRuntimeBypassReason.OUTPUT_ROUTE_UNAVAILABLE ->
                    AudioDspBypassReason.OutputRouteUnavailable
                DspRuntimeBypassReason.NATIVE_PROCESSING_ERROR ->
                    AudioDspBypassReason.NativeProcessingError
            },
            lastErrorCode = lastErrorCode.takeIf { it != 0 },
            latencyFrames = latencyFrames.toInt(),
        ),
        meter = AudioDspMeterSnapshot(
            inputPeakDb = inputPeakDb,
            outputPeakDb = outputPeakDb,
            compressorGainReductionDb = compressorGainReductionDb,
            limiterGainReductionDb = limiterGainReductionDb,
            clippedSamples = clippedSamples.toLong(),
            nonFiniteRecoveryCount = nonFiniteRecoveryCount.toLong(),
            appliedHeadroomDb = appliedHeadroomDb,
        ),
        performance = AudioDspPerformanceSnapshot(
            processCount = processCount.toLong(),
            averageProcessingTimeUs = averageProcessingTimeUs,
            maxProcessingTimeUs = maxProcessingTimeUs,
            bufferDurationUs = bufferDurationUs,
            deadlineUtilization = deadlineUtilization,
        ),
    )
}
