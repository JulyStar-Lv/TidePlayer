package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.CompressorSettings
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.GraphicEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.LimiterSettings
import io.github.julystar.musicapp.core.domain.model.ParametricEqBand
import io.github.julystar.musicapp.core.domain.model.ParametricEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.ToneControlSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioEffectPresentationTest {
    @Test
    fun equalizerSummaryDetectsBuiltInAndCustomCurves() {
        val flat = AudioEffectSettings.Default.equalizerUiSummary()
        val custom = AudioEffectSettings.Default.copy(
            profile = AudioEffectProfile.Default.copy(
                graphicEqualizer = GraphicEqualizerSettings(
                    bandGainsDb = listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                ),
            ),
        ).equalizerUiSummary()

        assertEquals("flat", flat.presetId)
        assertEquals(null, custom.presetId)
    }

    @Test
    fun equalizerSummaryUsesSelectedModeEnableState() {
        val summary = AudioEffectSettings.Default.copy(
            profile = AudioEffectProfile.Default.copy(
                equalizerMode = EqualizerMode.Parametric,
                parametricEqualizer = ParametricEqualizerSettings(
                    enabled = false,
                    bands = listOf(ParametricEqBand()),
                ),
            ),
        ).equalizerUiSummary()

        assertFalse(summary.enabled)
        assertEquals(EqualizerMode.Parametric, summary.mode)
        assertEquals(1, summary.bandCount)
    }

    @Test
    fun activeEffectsExcludeEqualizerAndRespectMasterSwitch() {
        val profile = AudioEffectProfile.Default.copy(
            tone = ToneControlSettings(enabled = false),
            compressor = CompressorSettings(enabled = true),
            limiter = LimiterSettings(enabled = true),
        )
        val disabled = AudioEffectSettings.Default.copy(enabled = false, profile = profile)
        val enabled = disabled.copy(enabled = true)

        assertTrue(disabled.activeAudioEffectModules().isEmpty())
        assertEquals(
            listOf(AudioEffectModule.Compressor, AudioEffectModule.Limiter),
            enabled.activeAudioEffectModules(),
        )
    }

    @Test
    fun audioValuesUseAccurateCompactFormatting() {
        assertEquals("-0.5 dB", formatTenthsDb(-5))
        assertEquals("+0.5 dB", formatTenthsDb(5))
        assertEquals("16.5 kHz", formatHz(16_500))
        assertEquals("1 kHz", formatHz(1_000))
        assertEquals("16k", compactFrequencyLabel(16_000))
    }
}
