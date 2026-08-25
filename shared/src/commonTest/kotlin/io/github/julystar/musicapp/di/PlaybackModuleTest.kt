package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.service.playback.data.TrackPreparationOperations
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinInternalApi

class PlaybackModuleTest {
    @OptIn(KoinInternalApi::class)
    @Test
    fun exposesTrackPreparationOperationsContract() {
        assertTrue(
            playbackModule.mappings.values.any {
                it.beanDefinition.primaryType == TrackPreparationOperations::class
            },
        )
    }
}
