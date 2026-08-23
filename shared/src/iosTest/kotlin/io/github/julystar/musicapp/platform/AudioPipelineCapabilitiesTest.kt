package io.github.julystar.musicapp.platform

import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPipelineCapabilitiesTest {
    @Test
    fun iosPublishesFloat32AndPcm16DspOutputFormats() {
        val capabilities = platformSettingsCapabilities()
        val pipeline = capabilities.audioPipeline

        assertEquals(
            setOf(AudioSampleFormat.Pcm16, AudioSampleFormat.Float32),
            pipeline.dspOutputSampleFormats,
        )
        assertTrue(pipeline.highResolutionDspOutput)
        assertTrue(capabilities.audioPreloadSupported)
    }
}
