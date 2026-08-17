package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.AudioReactiveSnapshot
import kotlinx.coroutines.flow.StateFlow

interface AudioReactiveRepository {
    val snapshot: StateFlow<AudioReactiveSnapshot>
}
