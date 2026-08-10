package io.github.julystar.musicapp.core.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioEffectSettingsTest {
    @Test
    fun legacyFieldsMigrateIntoVersionedProfile() {
        val normalized = normalizeAudioEffectSettings(
            AudioEffectSettings(
                eqBandGainsDb = listOf(6),
                bassDb = 4,
                compressorEnabled = true,
                stereoWidthPercent = 125,
            )
        )

        assertEquals(AUDIO_DSP_SCHEMA_VERSION, normalized.schemaVersion)
        assertEquals(6, normalized.profile.graphicEqualizer.bandGainsDb.first())
        assertEquals(4, normalized.profile.tone.bassGainDb)
        assertTrue(normalized.profile.compressor.enabled)
        assertEquals(125, normalized.profile.stereoWidth.widthPercent)
    }

    @Test
    fun versionedProfileIsNotOverwrittenByLegacyMirrors() {
        val settings = AudioEffectSettings.Default.withAudioEffectProfile(
            AudioEffectProfile.Default.copy(
                equalizerMode = EqualizerMode.Parametric,
                parametricEqualizer = ParametricEqualizerSettings(
                    enabled = true,
                    bands = listOf(ParametricEqBand(frequencyHz = 2_400)),
                ),
                tone = ToneControlSettings(bassGainDb = 7),
            )
        )
        val normalized = normalizeAudioEffectSettings(settings.copy(bassDb = -12))

        assertEquals(EqualizerMode.Parametric, normalized.profile.equalizerMode)
        assertEquals(2_400, normalized.profile.parametricEqualizer.bands.single().frequencyHz)
        assertEquals(7, normalized.profile.tone.bassGainDb)
        assertEquals(7, normalized.bassDb)
    }

    @Test
    fun profileAndLegacyMirrorsStaySynchronized() {
        val normalized = AudioEffectSettings.Default.withAudioEffectProfile(
            AudioEffectProfile.Default.copy(
                graphicEqualizer = GraphicEqualizerSettings(
                    bandGainsDb = List(EQ_BAND_COUNT) { it - 5 },
                    qHundredths = 175,
                ),
                reverb = ReverbSettings(preset = ReverbPreset.Hall),
            )
        )

        assertEquals(normalized.profile.graphicEqualizer.bandGainsDb, normalized.eqBandGainsDb)
        assertEquals(175, normalized.eqQHundredths)
        assertEquals(ReverbPreset.Hall, normalized.reverbPreset)
    }

    @Test
    fun versionOneProfileMigratesWithoutBeingReplacedByLegacyMirrors() {
        val normalized = normalizeAudioEffectSettings(
            AudioEffectSettings(
                schemaVersion = 1,
                bassDb = -12,
                profile = AudioEffectProfile.Default.copy(
                    tone = ToneControlSettings(enabled = true, bassGainDb = 7),
                    limiter = LimiterSettings(
                        truePeakEnabled = true,
                        oversampling = 2,
                        lookaheadMs = 99,
                    ),
                ),
            )
        )

        assertEquals(AUDIO_DSP_SCHEMA_VERSION, normalized.schemaVersion)
        assertEquals(7, normalized.profile.tone.bassGainDb)
        assertTrue(normalized.profile.limiter.truePeakEnabled)
        assertEquals(4, normalized.profile.limiter.oversampling)
        assertEquals(10, normalized.profile.limiter.lookaheadMs)
        assertEquals(HeadroomSettings(), normalized.headroom)
    }

    @Test
    fun headroomIsGlobalAndNotPartOfSoundEffectPresets() {
        val settings = AudioEffectSettings.Default.copy(
            headroom = HeadroomSettings(HeadroomMode.Manual, -120),
        )
        val updated = settings.withAudioEffectProfile(
            AudioEffectProfile.Default.copy(
                tone = ToneControlSettings(enabled = true, bassGainDb = 4),
            )
        )

        assertEquals(settings.headroom, updated.headroom)
    }

    @Test
    fun backupJsonRoundTripsPhaseTwoFieldsAndOldJsonGetsSafeDefaults() {
        val json = Json { encodeDefaults = true }
        val settings = AudioEffectSettings.Default.copy(
            headroom = HeadroomSettings(HeadroomMode.Manual, -75),
            profile = AudioEffectProfile.Default.copy(
                limiter = LimiterSettings(
                    truePeakEnabled = true,
                    oversampling = 4,
                    lookaheadMs = 5,
                ),
            ),
        )

        val restored = json.decodeFromString<AudioEffectSettings>(json.encodeToString(settings))
        val legacyWithoutPhaseTwoFields = json.decodeFromString<AudioEffectSettings>("{}")

        assertEquals(settings.headroom, restored.headroom)
        assertTrue(restored.profile.limiter.truePeakEnabled)
        assertEquals(5, restored.profile.limiter.lookaheadMs)
        assertEquals(HeadroomSettings(), legacyWithoutPhaseTwoFields.headroom)
        assertEquals(3, legacyWithoutPhaseTwoFields.profile.limiter.lookaheadMs)
    }
}
