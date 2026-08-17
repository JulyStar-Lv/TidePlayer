package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import io.github.julystar.musicapp.core.domain.model.AudioReactiveSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.AudioDspRuntimeRepository
import io.github.julystar.musicapp.core.domain.repository.AudioMonitoringRequester
import io.github.julystar.musicapp.core.domain.repository.AudioReactiveRepository
import io.github.julystar.musicapp.diagnostics.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import uniffi.app_backend.DspRuntimeBypassReason
import uniffi.app_backend.DspRuntimeState
import uniffi.app_backend.DspSampleFormat
import uniffi.app_backend.NativeAudioReactiveSnapshot
import uniffi.app_backend.NativeDspRuntimeSnapshot

internal const val AUDIO_DSP_DIAGNOSTICS_INTERVAL_MS = 150L
internal const val AUDIO_REACTIVE_VISUALIZATION_INTERVAL_MS = 33L

object AudioDspRuntimeMonitor : AudioDspRuntimeRepository {
    private val mutableStatus = MutableStateFlow(AudioDspRuntimeStatus())
    private val mutableMeter = MutableStateFlow(AudioDspMeterSnapshot())
    private val mutablePerformance = MutableStateFlow(AudioDspPerformanceSnapshot())
    private val mutableMonitoringRequesters = MutableStateFlow<Set<AudioMonitoringRequester>>(emptySet())

    override val status = mutableStatus.asStateFlow()
    override val meter = mutableMeter.asStateFlow()
    override val performance = mutablePerformance.asStateFlow()
    internal val monitoringRequesters = mutableMonitoringRequesters.asStateFlow()

    override fun requestMonitoring(requester: AudioMonitoringRequester) {
        mutableMonitoringRequesters.update { it + requester }
    }

    override fun releaseMonitoring(requester: AudioMonitoringRequester) {
        mutableMonitoringRequesters.update { it - requester }
    }

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

object AudioReactiveMonitor : AudioReactiveRepository {
    private val mutableSnapshot = MutableStateFlow(AudioReactiveSnapshot())

    override val snapshot = mutableSnapshot.asStateFlow()

    fun publish(snapshot: AudioReactiveSnapshot) {
        mutableSnapshot.value = AudioReactiveSnapshot(
            level = snapshot.level.safeUnitInterval(),
            beat = snapshot.beat.safeUnitInterval(),
        )
    }

    fun reset() {
        mutableSnapshot.value = AudioReactiveSnapshot()
    }
}

internal fun Flow<Set<AudioMonitoringRequester>>.monitoringRequested(
    requester: AudioMonitoringRequester,
): Flow<Boolean> = map { requester in it }.distinctUntilChanged()

fun NativeAudioReactiveSnapshot.toDomainAudioReactiveSnapshot(): AudioReactiveSnapshot {
    return AudioReactiveSnapshot(
        level = level.safeUnitInterval(),
        beat = beat.safeUnitInterval(),
    )
}

private fun Float.safeUnitInterval(): Float = takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

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
