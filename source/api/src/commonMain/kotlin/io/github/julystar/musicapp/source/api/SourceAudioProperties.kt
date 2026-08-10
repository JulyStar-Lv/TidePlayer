package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.AudioChannelLayout
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfo

data class SourceAudioProperties(
    val codec: String? = null,
    val container: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val lossless: Boolean? = null,
)

fun SourceAudioProperties.toAudioTechnicalInfo(): AudioTechnicalInfo = AudioTechnicalInfo(
    codec = codec.nonBlankOrNull(),
    container = container.nonBlankOrNull(),
    bitrateKbps = bitrateKbps.positiveOrNull(),
    sampleRateHz = sampleRateHz.positiveOrNull(),
    bitDepth = bitDepth.positiveOrNull(),
    channels = channels.positiveOrNull(),
    channelLayout = channelLayout.nonBlankOrNull()?.let(::AudioChannelLayout),
    lossless = lossless,
)

private fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
private fun Int?.positiveOrNull(): Int? = this?.takeIf { it > 0 }
