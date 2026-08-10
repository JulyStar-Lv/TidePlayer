package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.CompressorSettings
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.HeadroomSettings
import io.github.julystar.musicapp.core.domain.model.LimiterSettings
import io.github.julystar.musicapp.core.domain.model.MoogFilterSettings
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.ReverbSettings
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.SpatialAudioSettings
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.app_backend.DspReverbPreset
import uniffi.app_backend.DspHeadroomMode
import uniffi.app_backend.DspSpatialMode

class AudioDspConfigMapperTest {
    @Test
    fun replayGainDoesNotEnableSavedEffectsWhenMasterSwitchIsOff() {
        val settings = AudioEffectSettings.Default.withAudioEffectProfile(
            AudioEffectProfile.Default.copy(
                compressor = CompressorSettings(enabled = true),
                moogFilter = MoogFilterSettings(enabled = true),
                spatialAudio = SpatialAudioSettings(mode = SpatialAudioMode.Surround360),
                reverb = ReverbSettings(preset = ReverbPreset.Hall),
            )
        )

        val config = settings.toNativeDspConfiguration(inputGainDb = -4.5f)

        assertTrue(config.enabled)
        assertEquals(-4.5f, config.inputGainDb)
        assertFalse(config.graphicEqualizer.enabled)
        assertFalse(config.compressor.enabled)
        assertFalse(config.moogFilter.enabled)
        assertEquals(DspSpatialMode.NONE, config.spatialAudio.mode)
        assertEquals(DspReverbPreset.NONE, config.reverb.preset)
        assertTrue(config.limiter.enabled)
    }

    @Test
    fun masterSwitchEnablesConfiguredEffects() {
        val settings = AudioEffectSettings.Default.copy(enabled = true)
            .withAudioEffectProfile(
                AudioEffectProfile.Default.copy(
                    compressor = CompressorSettings(enabled = true),
                    spatialAudio = SpatialAudioSettings(mode = SpatialAudioMode.Surround360),
                )
            )

        val config = settings.toNativeDspConfiguration()

        assertTrue(config.graphicEqualizer.enabled)
        assertTrue(config.compressor.enabled)
        assertEquals(DspSpatialMode.SURROUND360, config.spatialAudio.mode)
    }

    @Test
    fun mapsGlobalHeadroomAndTruePeakLimiter() {
        val settings = AudioEffectSettings.Default.copy(
            enabled = true,
            headroom = HeadroomSettings(HeadroomMode.Manual, -85),
        ).withAudioEffectProfile(
            AudioEffectProfile.Default.copy(
                limiter = LimiterSettings(
                    enabled = true,
                    ceilingTenthsDb = -10,
                    truePeakEnabled = true,
                    oversampling = 4,
                    lookaheadMs = 7,
                ),
            )
        )

        val config = settings.toNativeDspConfiguration()

        assertEquals(DspHeadroomMode.MANUAL, config.headroom.mode)
        assertEquals(-8.5f, config.headroom.manualDb)
        assertTrue(config.limiter.truePeakEnabled)
        assertEquals(4u, config.limiter.oversampling.toUInt())
        assertEquals(7f, config.limiter.lookaheadMs)
        assertEquals(-1f, config.limiter.ceilingDb)
    }

    @Test
    fun automaticHeadroomEnablesProcessingWithoutSavedEffects() {
        val config = AudioEffectSettings.Default.copy(
            headroom = HeadroomSettings(mode = HeadroomMode.Automatic),
        ).toNativeDspConfiguration()

        assertTrue(config.enabled)
        assertEquals(DspHeadroomMode.AUTOMATIC, config.headroom.mode)
    }
}
