package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import kotlinx.coroutines.flow.StateFlow

enum class AudioMonitoringRequester {
    Diagnostics,
    Visualization,
}

interface AudioMonitoringRepository {
    fun requestMonitoring(requester: AudioMonitoringRequester)
    fun releaseMonitoring(requester: AudioMonitoringRequester)
}

interface AudioDspRuntimeRepository : AudioMonitoringRepository {
    val status: StateFlow<AudioDspRuntimeStatus>
    val meter: StateFlow<AudioDspMeterSnapshot>
    val performance: StateFlow<AudioDspPerformanceSnapshot>
}
