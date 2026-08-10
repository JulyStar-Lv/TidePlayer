package io.github.julystar.musicapp.platform

import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AudioPipelineCapabilitiesTest {
    @Test
    fun androidPublishesStablePcm16OutputFallback() {
        val pipeline = platformSettingsCapabilities().audioPipeline

        assertEquals(setOf(AudioSampleFormat.Pcm16), pipeline.dspOutputSampleFormats)
        assertFalse(pipeline.highResolutionDspOutput)
    }
}
