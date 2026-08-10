package io.github.julystar.musicapp.platform

import io.github.julystar.musicapp.core.domain.model.AudioSampleFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPipelineCapabilitiesTest {
    @Test
    fun desktopPublishesFloat32DspPipeline() {
        val pipeline = platformSettingsCapabilities().audioPipeline

        assertEquals(setOf(AudioSampleFormat.Float32), pipeline.dspOutputSampleFormats)
        assertTrue(pipeline.highResolutionDspOutput)
    }
}
