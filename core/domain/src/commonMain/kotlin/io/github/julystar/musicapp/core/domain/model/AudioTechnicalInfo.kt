package io.github.julystar.musicapp.core.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class AudioChannelLayout(val value: String) {
    init {
        require(value.isNotBlank()) { "Audio channel layout cannot be blank" }
    }
}

data class AudioTechnicalInfo(
    val codec: String? = null,
    val container: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val channelLayout: AudioChannelLayout? = null,
    val lossless: Boolean? = null,
) {
    fun withFallback(fallback: AudioTechnicalInfo?): AudioTechnicalInfo = copy(
        codec = codec ?: fallback?.codec,
        container = container ?: fallback?.container,
        bitrateKbps = bitrateKbps ?: fallback?.bitrateKbps,
        sampleRateHz = sampleRateHz ?: fallback?.sampleRateHz,
        bitDepth = bitDepth ?: fallback?.bitDepth,
        channels = channels ?: fallback?.channels,
        channelLayout = channelLayout ?: fallback?.channelLayout,
        lossless = lossless ?: fallback?.lossless,
    )

    fun takeUnlessEmpty(): AudioTechnicalInfo? = takeUnless {
        codec == null &&
            container == null &&
            bitrateKbps == null &&
            sampleRateHz == null &&
            bitDepth == null &&
            channels == null &&
            channelLayout == null &&
            lossless == null
    }
}

enum class AudioDeliveryMode {
    DirectPlay,
    DirectStream,
    Transcode,
    Unknown,
}

data class PlaybackAudioInfo(
    val source: AudioTechnicalInfo? = null,
    val effective: AudioTechnicalInfo? = null,
    val deliveryMode: AudioDeliveryMode = AudioDeliveryMode.Unknown,
) {
    val preferred: AudioTechnicalInfo?
        get() = effective ?: source
}

object AudioTechnicalInfoFormatter {
    fun format(info: AudioTechnicalInfo?): String? {
        info ?: return null
        return listOfNotNull(
            info.codec.normalizedLabel() ?: info.container.normalizedLabel(),
            info.bitDepth.positiveOrNull()?.let { "$it-bit" },
            info.sampleRateHz.positiveOrNull()?.let(::formatSampleRate),
            formatChannels(info.channelLayout, info.channels),
            info.bitrateKbps.positiveOrNull()?.let { "$it kbps" },
        ).joinToString(" · ").takeIf(String::isNotBlank)
    }

    fun format(info: PlaybackAudioInfo?): String? = format(info?.preferred)
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val wholeKHz = sampleRateHz / 1_000
    val remainderHz = sampleRateHz % 1_000
    return if (remainderHz == 0) {
        "$wholeKHz kHz"
    } else {
        val decimalKHz = sampleRateHz / 1_000.0
        "${decimalKHz.toString().trimEnd('0').trimEnd('.')} kHz"
    }
}

private fun formatChannels(
    channelLayout: AudioChannelLayout?,
    channels: Int?,
): String? {
    val explicitLayout = channelLayout?.value.normalizedLabel()
    if (explicitLayout != null) {
        return when (explicitLayout.lowercase()) {
            "mono", "1.0" -> "Mono"
            "stereo", "2.0", "front_left|front_right" -> "Stereo"
            else -> explicitLayout
        }
    }
    return when (channels.positiveOrNull()) {
        1 -> "Mono"
        2 -> "Stereo"
        null -> null
        else -> "$channels ch"
    }
}

private fun String?.normalizedLabel(): String? = this
    ?.trim()
    ?.takeIf { value ->
        value.isNotBlank() && value.lowercase() !in AUDIO_PROPERTY_PLACEHOLDERS
    }

private fun Int?.positiveOrNull(): Int? = this?.takeIf { it > 0 }

private val AUDIO_PROPERTY_PLACEHOLDERS = setOf("unknown", "null", "n/a")
