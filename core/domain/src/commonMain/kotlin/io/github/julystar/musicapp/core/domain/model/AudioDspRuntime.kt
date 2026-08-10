package io.github.julystar.musicapp.core.domain.model

enum class AudioDspRuntimeState {
    Inactive,
    Active,
    Bypassed,
    Unavailable,
    Error,
}

enum class AudioDspBypassReason {
    EffectsDisabled,
    UnsupportedSampleFormat,
    UnsupportedChannelCount,
    UnsupportedSampleRate,
    PlatformProcessingUnavailable,
    ProtectedContent,
    AudioTapUnavailable,
    OutputRouteUnavailable,
    NativeProcessingError,
}

data class AudioDspRuntimeStatus(
    val state: AudioDspRuntimeState = AudioDspRuntimeState.Inactive,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val sampleFormat: AudioSampleFormat? = null,
    val bypassReason: AudioDspBypassReason? = null,
    val lastErrorCode: Int? = null,
    val latencyFrames: Int = 0,
)

data class AudioDspMeterSnapshot(
    val inputPeakDb: Float = -120f,
    val outputPeakDb: Float = -120f,
    val compressorGainReductionDb: Float = 0f,
    val limiterGainReductionDb: Float = 0f,
    val clippedSamples: Long = 0,
    val nonFiniteRecoveryCount: Long = 0,
    val appliedHeadroomDb: Float = 0f,
)

data class AudioDspPerformanceSnapshot(
    val processCount: Long = 0,
    val averageProcessingTimeUs: Float = 0f,
    val maxProcessingTimeUs: Float = 0f,
    val bufferDurationUs: Float = 0f,
    val deadlineUtilization: Float = 0f,
)

data class AudioDspRuntimeSnapshot(
    val status: AudioDspRuntimeStatus = AudioDspRuntimeStatus(),
    val meter: AudioDspMeterSnapshot = AudioDspMeterSnapshot(),
    val performance: AudioDspPerformanceSnapshot = AudioDspPerformanceSnapshot(),
)
