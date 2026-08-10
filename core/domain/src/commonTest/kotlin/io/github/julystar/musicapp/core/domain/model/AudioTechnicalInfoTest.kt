package io.github.julystar.musicapp.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioTechnicalInfoTest {
    @Test
    fun formatterOmitsMissingAndInvalidFields() {
        val formatted = AudioTechnicalInfoFormatter.format(
            AudioTechnicalInfo(
                codec = "FLAC",
                bitrateKbps = 2_784,
                sampleRateHz = 96_000,
                bitDepth = 24,
                channels = 2,
            )
        )

        assertEquals("FLAC · 24-bit · 96 kHz · Stereo · 2784 kbps", formatted)
        assertEquals(null, AudioTechnicalInfoFormatter.format(AudioTechnicalInfo()))
        assertEquals(
            null,
            AudioTechnicalInfoFormatter.format(
                AudioTechnicalInfo(codec = "Unknown", container = "N/A")
            ),
        )
    }

    @Test
    fun formatterUsesFractionalSampleRateAndUnknownChannelCount() {
        val formatted = AudioTechnicalInfoFormatter.format(
            AudioTechnicalInfo(
                codec = "AAC",
                bitrateKbps = 256,
                sampleRateHz = 44_100,
                channels = 6,
            )
        )

        assertEquals("AAC · 44.1 kHz · 6 ch · 256 kbps", formatted)
    }

    @Test
    fun playbackPrefersEffectiveTranscodedAudio() {
        val info = PlaybackAudioInfo(
            source = AudioTechnicalInfo(
                codec = "FLAC",
                bitrateKbps = 5_640,
                sampleRateHz = 192_000,
                bitDepth = 24,
            ),
            effective = AudioTechnicalInfo(
                codec = "AAC",
                bitrateKbps = 320,
                sampleRateHz = 48_000,
            ),
            deliveryMode = AudioDeliveryMode.Transcode,
        )

        assertEquals("AAC · 48 kHz · 320 kbps", AudioTechnicalInfoFormatter.format(info))
    }

    @Test
    fun directPlayFallsBackToSourceAudio() {
        val info = PlaybackAudioInfo(
            source = AudioTechnicalInfo(codec = "FLAC", sampleRateHz = 96_000, bitDepth = 24),
            effective = null,
            deliveryMode = AudioDeliveryMode.DirectPlay,
        )

        assertEquals("FLAC · 24-bit · 96 kHz", AudioTechnicalInfoFormatter.format(info))
    }

    @Test
    fun sourcePropertiesUseFieldByFieldTrackFallback() {
        val merged = AudioTechnicalInfo(
            codec = "FLAC",
            sampleRateHz = 96_000,
        ).withFallback(
            AudioTechnicalInfo(
                bitDepth = 24,
                channels = 2,
            )
        )

        assertEquals("FLAC · 24-bit · 96 kHz · Stereo", AudioTechnicalInfoFormatter.format(merged))
    }
}
