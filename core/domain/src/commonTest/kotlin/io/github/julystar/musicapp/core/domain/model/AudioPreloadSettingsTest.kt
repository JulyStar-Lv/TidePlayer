package io.github.julystar.musicapp.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioPreloadSettingsTest {
    @Test
    fun normalizationAllowsOffThroughSixteenMiB() {
        assertEquals(0L, normalizeAudioPreloadBytes(0L))
        assertEquals(MAX_AUDIO_PRELOAD_BYTES, normalizeAudioPreloadBytes(MAX_AUDIO_PRELOAD_BYTES))
        assertEquals(MAX_AUDIO_PRELOAD_BYTES, normalizeAudioPreloadBytes(Long.MAX_VALUE))
    }
}
