package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.AUDIO_PRELOAD_PRESETS_BYTES
import io.github.julystar.musicapp.core.domain.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioPreloadPresentationTest {
    @Test
    fun nextTrackPreloadOffersOnlySupportedTargets() {
        assertEquals(
            listOf(0L, 2L, 4L, 8L, 16L).map { it * 1_048_576L },
            AUDIO_PRELOAD_PRESETS_BYTES,
        )
        assertEquals(4L * 1_048_576L, AppSettings.Default.audioPreloadBytes)
    }
}
