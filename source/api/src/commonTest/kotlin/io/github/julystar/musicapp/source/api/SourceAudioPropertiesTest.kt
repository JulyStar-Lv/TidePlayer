package io.github.julystar.musicapp.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceAudioPropertiesTest {
    @Test
    fun mapsNormalizedSourceUnitsWithoutAdditionalConversion() {
        val technicalInfo = SourceAudioProperties(
            codec = "FLAC",
            bitrateKbps = 2_784,
            sampleRateHz = 96_000,
            bitDepth = 24,
            channels = 2,
            channelLayout = "stereo",
            lossless = true,
        ).toAudioTechnicalInfo()

        assertEquals(2_784, technicalInfo.bitrateKbps)
        assertEquals(96_000, technicalInfo.sampleRateHz)
        assertEquals(24, technicalInfo.bitDepth)
        assertEquals(2, technicalInfo.channels)
        assertEquals("stereo", technicalInfo.channelLayout?.value)
    }

    @Test
    fun invalidNumericPropertiesBecomeMissing() {
        val technicalInfo = SourceAudioProperties(
            bitrateKbps = 0,
            sampleRateHz = -1,
            bitDepth = 0,
            channels = -2,
        ).toAudioTechnicalInfo()

        assertNull(technicalInfo.bitrateKbps)
        assertNull(technicalInfo.sampleRateHz)
        assertNull(technicalInfo.bitDepth)
        assertNull(technicalInfo.channels)
    }
}
