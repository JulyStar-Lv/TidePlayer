package io.github.julystar.musicapp.core.audio

import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.MoogFilterMode
import io.github.julystar.musicapp.core.domain.model.ParametricEqFilterType
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.SpeakerOutputMode
import io.github.julystar.musicapp.core.domain.model.normalizeAudioEffectSettings
import kotlin.math.abs
import uniffi.app_backend.DspCompressor
import uniffi.app_backend.DspConfiguration
import uniffi.app_backend.DspCrossfeed
import uniffi.app_backend.DspDynamicEqualizer
import uniffi.app_backend.DspEqMode
import uniffi.app_backend.DspFilterType
import uniffi.app_backend.DspGraphicEqualizer
import uniffi.app_backend.DspHeadroom
import uniffi.app_backend.DspHeadroomMode
import uniffi.app_backend.DspLimiter
import uniffi.app_backend.DspLoudness
import uniffi.app_backend.DspMonoBass
import uniffi.app_backend.DspMoogFilter
import uniffi.app_backend.DspMoogMode
import uniffi.app_backend.DspParametricEqBand
import uniffi.app_backend.DspParametricEqualizer
import uniffi.app_backend.DspReverb
import uniffi.app_backend.DspReverbPreset
import uniffi.app_backend.DspSpatialAudio
import uniffi.app_backend.DspSpatialMode
import uniffi.app_backend.DspSpeakerMode
import uniffi.app_backend.DspSpeakerOutput
import uniffi.app_backend.DspStereoWidth
import uniffi.app_backend.DspToneControl

/**
 * Maps persisted, platform-independent settings to the single Rust DSP schema.
 *
 * [inputGainDb] is deliberately separate so ReplayGain can be applied before
 * every effect without becoming part of a saved effect preset.
 */
fun AudioEffectSettings.toNativeDspConfiguration(
    inputGainDb: Float = 0f,
): DspConfiguration {
    val settings = normalizeAudioEffectSettings(this)
    val profile = settings.profile
    val effectsEnabled = settings.enabled
    val replayGainEnabled = abs(inputGainDb) > 0.0001f
    val processingEnabled = effectsEnabled || replayGainEnabled ||
        settings.headroom.mode != HeadroomMode.Off
    return DspConfiguration(
        enabled = processingEnabled,
        inputGainDb = inputGainDb,
        headroom = DspHeadroom(
            mode = when (settings.headroom.mode) {
                HeadroomMode.Off -> DspHeadroomMode.OFF
                HeadroomMode.Automatic -> DspHeadroomMode.AUTOMATIC
                HeadroomMode.Manual -> DspHeadroomMode.MANUAL
            },
            manualDb = settings.headroom.manualTenthsDb / 10f,
        ),
        equalizerMode = when (profile.equalizerMode) {
            EqualizerMode.Graphic -> DspEqMode.GRAPHIC
            EqualizerMode.Parametric -> DspEqMode.PARAMETRIC
        },
        graphicEqualizer = DspGraphicEqualizer(
            enabled = effectsEnabled && profile.graphicEqualizer.enabled,
            preampDb = profile.graphicEqualizer.preampTenthsDb / 10f,
            q = profile.graphicEqualizer.qHundredths / 100f,
            gainsDb = profile.graphicEqualizer.bandGainsDb.map(Int::toFloat),
        ),
        parametricEqualizer = DspParametricEqualizer(
            enabled = effectsEnabled && profile.parametricEqualizer.enabled,
            preampDb = profile.parametricEqualizer.preampTenthsDb / 10f,
            bands = profile.parametricEqualizer.bands.map { band ->
                DspParametricEqBand(
                    enabled = band.enabled,
                    filterType = band.type.toNative(),
                    frequencyHz = band.frequencyHz.toFloat(),
                    gainDb = band.gainTenthsDb / 10f,
                    q = band.qHundredths / 100f,
                )
            },
        ),
        tone = DspToneControl(
            enabled = effectsEnabled && profile.tone.enabled,
            bassGainDb = profile.tone.bassGainDb.toFloat(),
            bassFrequencyHz = profile.tone.bassFrequencyHz.toFloat(),
            trebleGainDb = profile.tone.trebleGainDb.toFloat(),
            trebleFrequencyHz = profile.tone.trebleFrequencyHz.toFloat(),
        ),
        compressor = DspCompressor(
            enabled = effectsEnabled && profile.compressor.enabled,
            thresholdDb = profile.compressor.thresholdDb.toFloat(),
            ratio = profile.compressor.ratio.toFloat(),
            attackMs = profile.compressor.attackMs.toFloat(),
            releaseMs = profile.compressor.releaseMs.toFloat(),
            makeupGainDb = profile.compressor.makeupGainDb.toFloat(),
            kneeDb = profile.compressor.kneeDb.toFloat(),
        ),
        loudness = DspLoudness(
            enabled = effectsEnabled && profile.loudness.enabled,
            amount = profile.loudness.amountPercent / 100f,
            balance = profile.loudness.balancePercent / 100f,
        ),
        dynamicEqualizer = DspDynamicEqualizer(
            enabled = effectsEnabled && profile.dynamicEq.enabled,
            amount = profile.dynamicEq.amountPercent / 100f,
            deEsserAmount = profile.dynamicEq.deEsserAmountPercent / 100f,
            deEsserFrequencyHz = profile.dynamicEq.deEsserFrequencyHz.toFloat(),
        ),
        monoBass = DspMonoBass(
            enabled = effectsEnabled && profile.monoBass.enabled,
            crossoverHz = profile.monoBass.crossoverHz.toFloat(),
            amount = profile.monoBass.amountPercent / 100f,
        ),
        stereoWidth = DspStereoWidth(
            enabled = effectsEnabled && profile.stereoWidth.enabled,
            width = profile.stereoWidth.widthPercent / 100f,
        ),
        crossfeed = DspCrossfeed(
            enabled = effectsEnabled && profile.crossfeed.enabled,
            lowCutHz = profile.crossfeed.lowCutHz.toFloat(),
            highCutHz = profile.crossfeed.highCutHz.toFloat(),
            attenuationDb = profile.crossfeed.attenuationTenthsDb / 10f,
        ),
        spatialAudio = DspSpatialAudio(
            mode = if (!effectsEnabled) {
                DspSpatialMode.NONE
            } else {
                when (profile.spatialAudio.mode) {
                    SpatialAudioMode.None -> DspSpatialMode.NONE
                    SpatialAudioMode.CrossfeedAndWidth -> DspSpatialMode.CROSSFEED_AND_WIDTH
                    SpatialAudioMode.Surround360 -> DspSpatialMode.SURROUND360
                    SpatialAudioMode.Panoramic360 -> DspSpatialMode.PANORAMIC360
                }
            },
            intensity = profile.spatialAudio.intensityPercent / 100f,
            azimuthDegrees = profile.spatialAudio.azimuthDegrees.toFloat(),
            elevationDegrees = profile.spatialAudio.elevationDegrees.toFloat(),
            autoRotateDegreesPerSecond =
                profile.spatialAudio.autoRotateDegreesPerSecond.toFloat(),
            roomAmount = profile.spatialAudio.roomAmountPercent / 100f,
        ),
        moogFilter = DspMoogFilter(
            enabled = effectsEnabled && profile.moogFilter.enabled,
            mode = when (profile.moogFilter.mode) {
                MoogFilterMode.LowPass24 -> DspMoogMode.LOW_PASS24
                MoogFilterMode.LowPass12 -> DspMoogMode.LOW_PASS12
                MoogFilterMode.HighPass24 -> DspMoogMode.HIGH_PASS24
                MoogFilterMode.BandPass12 -> DspMoogMode.BAND_PASS12
                MoogFilterMode.Notch -> DspMoogMode.NOTCH
            },
            cutoffHz = profile.moogFilter.cutoffHz.toFloat(),
            resonance = profile.moogFilter.resonancePercent / 100f,
            driveDb = profile.moogFilter.driveTenthsDb / 10f,
            mix = profile.moogFilter.mixPercent / 100f,
        ),
        speakerOutput = DspSpeakerOutput(
            enabled = effectsEnabled && profile.speakerOutput.enabled,
            mode = when (profile.speakerOutput.mode) {
                SpeakerOutputMode.Elasticity -> DspSpeakerMode.ELASTICITY
                SpeakerOutputMode.Powerful -> DspSpeakerMode.POWERFUL
                SpeakerOutputMode.Wide -> DspSpeakerMode.WIDE
            },
            strength = profile.speakerOutput.strengthPercent / 100f,
        ),
        limiter = DspLimiter(
            enabled = profile.limiter.enabled && processingEnabled,
            ceilingDb = profile.limiter.ceilingTenthsDb / 10f,
            attackMs = profile.limiter.attackHundredthsMs / 100f,
            releaseMs = profile.limiter.releaseMs.toFloat(),
            truePeakEnabled = profile.limiter.truePeakEnabled,
            oversampling = profile.limiter.oversampling.toUByte(),
            lookaheadMs = profile.limiter.lookaheadMs.toFloat(),
        ),
        reverb = DspReverb(
            preset = if (!effectsEnabled) {
                DspReverbPreset.NONE
            } else {
                when (profile.reverb.preset) {
                    ReverbPreset.None -> DspReverbPreset.NONE
                    ReverbPreset.SmallRoom -> DspReverbPreset.SMALL_ROOM
                    ReverbPreset.MediumRoom -> DspReverbPreset.MEDIUM_ROOM
                    ReverbPreset.LargeRoom -> DspReverbPreset.LARGE_ROOM
                    ReverbPreset.Hall -> DspReverbPreset.HALL
                    ReverbPreset.Plate -> DspReverbPreset.PLATE
                }
            },
            wet = profile.reverb.wetPercent / 100f,
        ),
    )
}

private fun ParametricEqFilterType.toNative(): DspFilterType = when (this) {
    ParametricEqFilterType.Peak -> DspFilterType.PEAK
    ParametricEqFilterType.LowShelf -> DspFilterType.LOW_SHELF
    ParametricEqFilterType.HighShelf -> DspFilterType.HIGH_SHELF
    ParametricEqFilterType.LowPass -> DspFilterType.LOW_PASS
    ParametricEqFilterType.HighPass -> DspFilterType.HIGH_PASS
    ParametricEqFilterType.BandPass -> DspFilterType.BAND_PASS
    ParametricEqFilterType.Notch -> DspFilterType.NOTCH
}
