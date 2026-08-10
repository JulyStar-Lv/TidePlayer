package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.AudioChannelLayout
import io.github.julystar.musicapp.core.domain.model.AudioDeliveryMode
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfo
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate

internal fun TrackSourcePlaybackCandidate.toPlaybackAudioInfo(
    fallbackTrack: TrackEntity?,
): PlaybackAudioInfo {
    val fallback = fallbackTrack?.toAudioTechnicalInfo()
    val source = AudioTechnicalInfo(
        codec = ref.codec.nonBlankOrNull(),
        container = ref.container.nonBlankOrNull(),
        bitrateKbps = ref.bitRate.positiveOrNull(),
        sampleRateHz = ref.sampleRate.positiveOrNull(),
        bitDepth = ref.bitsPerSample.positiveOrNull(),
        channels = ref.channels.positiveOrNull(),
        channelLayout = ref.channelLayout.toAudioChannelLayoutOrNull(),
        lossless = ref.lossless,
    ).withFallback(fallback).takeUnlessEmpty()
    val deliveryMode = account.providerType.deliveryMode()
    return PlaybackAudioInfo(
        source = source,
        effective = source.takeIf { deliveryMode == AudioDeliveryMode.DirectPlay },
        deliveryMode = deliveryMode,
    )
}

internal fun TrackEntity.toPlaybackAudioInfo(): PlaybackAudioInfo = PlaybackAudioInfo(
    source = toAudioTechnicalInfo(),
    effective = null,
    deliveryMode = AudioDeliveryMode.Unknown,
)

private fun TrackEntity.toAudioTechnicalInfo(): AudioTechnicalInfo? = AudioTechnicalInfo(
    codec = codec.nonBlankOrNull(),
    container = container.nonBlankOrNull(),
    bitrateKbps = bitRate.positiveOrNull(),
    sampleRateHz = sampleRate.positiveOrNull(),
    bitDepth = bitsPerSample.positiveOrNull(),
    channels = channels.positiveOrNull(),
    channelLayout = channelLayout.toAudioChannelLayoutOrNull(),
    lossless = lossless,
).takeUnlessEmpty()

private fun String.deliveryMode(): AudioDeliveryMode = when (this) {
    ProviderTypes.Local,
    ProviderTypes.WebDav,
    ProviderTypes.Smb,
    ProviderTypes.OneDrive,
    ProviderTypes.Emby -> AudioDeliveryMode.DirectPlay
    ProviderTypes.Navidrome,
    ProviderTypes.OpenSubsonic -> AudioDeliveryMode.Unknown
    else -> AudioDeliveryMode.Unknown
}

private fun String?.toAudioChannelLayoutOrNull(): AudioChannelLayout? =
    nonBlankOrNull()?.let(::AudioChannelLayout)

private fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
private fun Int?.positiveOrNull(): Int? = this?.takeIf { it > 0 }
