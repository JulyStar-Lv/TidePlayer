package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.ReverbPreset
import io.github.julystar.musicapp.core.domain.model.SpatialAudioMode
import io.github.julystar.musicapp.core.domain.model.matchingPreset

@Immutable
internal data class EqualizerUiSummary(
    val enabled: Boolean,
    val mode: EqualizerMode,
    val presetId: String? = null,
    val bandCount: Int = 0,
)

internal enum class AudioEffectModule {
    Tone,
    Loudness,
    MonoBass,
    DynamicEqualizer,
    MoogFilter,
    Compressor,
    Reverb,
    StereoWidth,
    Crossfeed,
    Spatial,
    SpeakerOutput,
    Limiter,
}

internal fun AudioEffectSettings.equalizerUiSummary(): EqualizerUiSummary {
    val profile = profile
    return when (profile.equalizerMode) {
        EqualizerMode.Graphic -> EqualizerUiSummary(
            enabled = profile.graphicEqualizer.enabled,
            mode = EqualizerMode.Graphic,
            presetId = profile.graphicEqualizer.matchingPreset()?.id,
            bandCount = profile.graphicEqualizer.bandGainsDb.size,
        )
        EqualizerMode.Parametric -> EqualizerUiSummary(
            enabled = profile.parametricEqualizer.enabled,
            mode = EqualizerMode.Parametric,
            bandCount = profile.parametricEqualizer.bands.size,
        )
    }
}

internal fun AudioEffectSettings.activeAudioEffectModules(): List<AudioEffectModule> {
    if (!enabled) return emptyList()
    return profile.activeAudioEffectModules()
}

internal fun AudioEffectProfile.activeAudioEffectModules(): List<AudioEffectModule> = buildList {
    if (tone.enabled) add(AudioEffectModule.Tone)
    if (loudness.enabled) add(AudioEffectModule.Loudness)
    if (monoBass.enabled) add(AudioEffectModule.MonoBass)
    if (dynamicEq.enabled) add(AudioEffectModule.DynamicEqualizer)
    if (moogFilter.enabled) add(AudioEffectModule.MoogFilter)
    if (compressor.enabled) add(AudioEffectModule.Compressor)
    if (reverb.preset != ReverbPreset.None) add(AudioEffectModule.Reverb)
    when (spatialAudio.mode) {
        SpatialAudioMode.None -> Unit
        SpatialAudioMode.CrossfeedAndWidth -> {
            if (stereoWidth.enabled) add(AudioEffectModule.StereoWidth)
            if (crossfeed.enabled) add(AudioEffectModule.Crossfeed)
        }
        SpatialAudioMode.Surround360,
        SpatialAudioMode.Panoramic360,
        -> add(AudioEffectModule.Spatial)
    }
    if (speakerOutput.enabled) add(AudioEffectModule.SpeakerOutput)
    if (limiter.enabled) add(AudioEffectModule.Limiter)
}
