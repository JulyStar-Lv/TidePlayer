package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.SourceAudioProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object SubsonicAudioPropertiesMapper {
    fun map(song: JsonObject): SourceAudioProperties? {
        // Transcode advertisement fields describe a possible delivery, not the source file.
        val format = resolveAudioFormat(
            codec = song.stringOrNull("codec"),
            contentType = song.stringOrNull("contentType"),
            suffix = song.stringOrNull("suffix"),
            container = song.stringOrNull("suffix"),
        )
        return SourceAudioProperties(
            codec = format.codec,
            container = format.container,
            bitrateKbps = song.positiveIntOrNull("bitRate"),
            sampleRateHz = song.positiveIntOrNull("samplingRate"),
            bitDepth = song.positiveIntOrNull("bitDepth"),
            channels = song.positiveIntOrNull("channelCount"),
            lossless = format.lossless,
        ).takeUnlessEmpty()
    }
}

internal data class EmbyAudioPropertiesMapping(
    val properties: SourceAudioProperties?,
    val sourceMediaId: String?,
    val mimeType: String?,
)

internal object EmbyAudioPropertiesMapper {
    fun map(item: JsonObject): EmbyAudioPropertiesMapping {
        val mediaSources = item.arrayOrNull("MediaSources")
            .orEmpty()
            .filterIsInstance<JsonObject>()
        val requestedSourceId = item.stringOrNull("MediaSourceId")
        val mediaSource = mediaSources.firstOrNull { source ->
            requestedSourceId != null && source.stringOrNull("Id") == requestedSourceId
        } ?: mediaSources.firstOrNull { source ->
            source.booleanOrNull("IsDefault") == true || source.booleanOrNull("Default") == true
        } ?: mediaSources.firstOrNull()

        val sourceStreams = mediaSource?.arrayOrNull("MediaStreams")
            .orEmpty()
            .filterIsInstance<JsonObject>()
        val itemStreams = item.arrayOrNull("MediaStreams")
            .orEmpty()
            .filterIsInstance<JsonObject>()
        val audioStreams = (sourceStreams.ifEmpty { itemStreams })
            .filter { stream -> stream.stringOrNull("Type").equals("Audio", ignoreCase = true) }
        val defaultAudioStreamIndex = mediaSource?.intOrNull("DefaultAudioStreamIndex")
        val audioStream = audioStreams.firstOrNull { stream ->
            defaultAudioStreamIndex != null && stream.intOrNull("Index") == defaultAudioStreamIndex
        } ?: audioStreams.firstOrNull()

        val rawContainer = mediaSource?.stringOrNull("Container")
            ?: item.stringOrNull("Container")
        val codec = audioStream?.stringOrNull("Codec")
        val format = resolveAudioFormat(
            codec = codec,
            contentType = mediaSource?.stringOrNull("MimeType") ?: item.stringOrNull("MimeType"),
            suffix = null,
            container = rawContainer,
        )
        val bitrateBps = audioStream?.positiveIntOrNull("BitRate")
            ?: mediaSource?.positiveIntOrNull("BitRate")
        val properties = SourceAudioProperties(
            codec = format.codec,
            container = format.container,
            bitrateKbps = bitrateBps?.div(1_000)?.takeIf { it > 0 },
            sampleRateHz = audioStream?.positiveIntOrNull("SampleRate"),
            bitDepth = audioStream?.positiveIntOrNull("BitDepth"),
            channels = audioStream?.positiveIntOrNull("Channels"),
            channelLayout = audioStream?.stringOrNull("ChannelLayout"),
            lossless = format.lossless,
        ).takeUnlessEmpty()
        return EmbyAudioPropertiesMapping(
            properties = properties,
            sourceMediaId = mediaSource?.stringOrNull("Id"),
            mimeType = mediaSource?.stringOrNull("MimeType")
                ?: item.stringOrNull("MimeType")
                ?: rawContainer.toAudioMimeTypeOrNull(),
        )
    }
}

private data class ResolvedAudioFormat(
    val codec: String?,
    val container: String?,
    val lossless: Boolean?,
)

private fun resolveAudioFormat(
    codec: String?,
    contentType: String?,
    suffix: String?,
    container: String?,
): ResolvedAudioFormat {
    val normalizedCodec = normalizeCodec(codec)
        ?: codecFromSuffixOrContentType(suffix, contentType)
    val normalizedContainer = normalizeContainer(container ?: suffix, contentType)
    return ResolvedAudioFormat(
        codec = normalizedCodec,
        container = normalizedContainer,
        lossless = codecLossless(normalizedCodec),
    )
}

private fun normalizeCodec(codec: String?): String? = when (codec?.trim()?.lowercase()) {
    null, "" -> null
    "flac" -> "FLAC"
    "alac" -> "ALAC"
    "aac", "aac_latm" -> "AAC"
    "mp3", "mpeg", "mpeg audio", "mp2", "mp1" -> "MP3"
    "m4a", "m4b", "mp4" -> "MPEG-4 Audio"
    "opus" -> "Opus"
    "vorbis" -> "Vorbis"
    "ape", "monkey's audio" -> "APE"
    "wavpack", "wv" -> "WavPack"
    "pcm", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le", "lpcm" -> "PCM"
    else -> codec.trim()
}

private fun codecFromSuffixOrContentType(suffix: String?, contentType: String?): String? {
    return when (suffix?.trim()?.lowercase()) {
        "flac" -> "FLAC"
        "mp3", "mp2", "mp1" -> "MP3"
        "aac" -> "AAC"
        "alac" -> "ALAC"
        "ape" -> "APE"
        "wv" -> "WavPack"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "m4a", "m4b", "mp4" -> "MPEG-4 Audio"
        else -> when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/aac", "audio/aacp" -> "AAC"
            "audio/opus" -> "Opus"
            else -> null
        }
    }
}

private fun normalizeContainer(container: String?, contentType: String?): String? {
    return when (container?.trim()?.lowercase()) {
        "flac" -> "FLAC"
        "mp3", "mp2", "mp1", "mpeg" -> "MPEG Audio"
        "m4a", "m4b", "mp4", "mov" -> "MP4"
        "ogg", "oga", "opus" -> "Ogg"
        "wav", "wave" -> "WAV"
        "aif", "aiff" -> "AIFF"
        "ape" -> "APE"
        "wv", "wavpack" -> "WavPack"
        null, "" -> when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MPEG Audio"
            "audio/mp4", "audio/x-m4a" -> "MP4"
            "audio/ogg", "application/ogg" -> "Ogg"
            "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
            else -> null
        }
        else -> container.trim()
    }
}

private fun codecLossless(codec: String?): Boolean? = when (codec?.lowercase()) {
    "flac", "alac", "ape", "wavpack", "pcm" -> true
    "mp3", "aac", "opus", "vorbis" -> false
    else -> null
}

private fun String?.toAudioMimeTypeOrNull(): String? = when (this?.trim()?.lowercase()) {
    "flac" -> "audio/flac"
    "mp3", "mpeg" -> "audio/mpeg"
    "m4a", "m4b", "mp4" -> "audio/mp4"
    "ogg", "oga", "opus" -> "audio/ogg"
    "wav", "wave" -> "audio/wav"
    else -> null
}

private fun SourceAudioProperties.takeUnlessEmpty(): SourceAudioProperties? = takeUnless {
    codec == null &&
        container == null &&
        bitrateKbps == null &&
        sampleRateHz == null &&
        bitDepth == null &&
        channels == null &&
        channelLayout == null &&
        lossless == null
}

private fun JsonObject.arrayOrNull(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.primitiveOrNull(name: String): JsonPrimitive? = get(name) as? JsonPrimitive
private fun JsonObject.stringOrNull(name: String): String? =
    primitiveOrNull(name)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

private fun JsonObject.intOrNull(name: String): Int? = primitiveOrNull(name)?.intOrNull
private fun JsonObject.positiveIntOrNull(name: String): Int? = intOrNull(name)?.takeIf { it > 0 }
private fun JsonObject.booleanOrNull(name: String): Boolean? = primitiveOrNull(name)?.booleanOrNull
