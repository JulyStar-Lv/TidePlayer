package io.github.julystar.musicapp.platform

import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPipelineCapabilitiesTest {
    @Test
    fun androidPublishesStablePcm16OutputFallback() {
        val capabilities = platformSettingsCapabilities()
        val pipeline = capabilities.audioPipeline

        assertEquals(setOf(AudioSampleFormat.Pcm16), pipeline.dspOutputSampleFormats)
        assertFalse(pipeline.highResolutionDspOutput)
        assertTrue(capabilities.audioPreloadSupported)
    }
}
