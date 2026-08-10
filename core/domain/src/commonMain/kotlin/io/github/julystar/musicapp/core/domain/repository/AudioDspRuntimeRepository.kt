package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import kotlinx.coroutines.flow.StateFlow

interface AudioDspRuntimeRepository {
    val status: StateFlow<AudioDspRuntimeStatus>
    val meter: StateFlow<AudioDspMeterSnapshot>
    val performance: StateFlow<AudioDspPerformanceSnapshot>
}
