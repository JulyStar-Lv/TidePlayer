package io.github.julystar.musicapp.source.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteAudioPropertiesMappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun subsonicMapsOpenSubsonicFlacPropertiesInKbps() {
        val song = json.parseToJsonElement(
            """
            {
              "contentType": "audio/flac",
              "suffix": "flac",
              "bitRate": 2784,
              "bitDepth": 24,
              "samplingRate": 96000,
              "channelCount": 2
            }
            """.trimIndent()
        ).jsonObject

        val properties = SubsonicAudioPropertiesMapper.map(song)

        assertEquals("FLAC", properties?.codec)
        assertEquals("FLAC", properties?.container)
        assertEquals(2_784, properties?.bitrateKbps)
        assertEquals(24, properties?.bitDepth)
        assertEquals(96_000, properties?.sampleRateHz)
        assertEquals(2, properties?.channels)
        assertTrue(properties?.lossless == true)
    }

    @Test
    fun subsonicAcceptsLegacySongWithOnlyBasicFields() {
        val song = json.parseToJsonElement(
            """
            {
              "contentType": "audio/mpeg",
              "suffix": "mp3",
              "bitRate": 320
            }
            """.trimIndent()
        ).jsonObject

        val properties = SubsonicAudioPropertiesMapper.map(song)

        assertEquals("MP3", properties?.codec)
        assertEquals(320, properties?.bitrateKbps)
        assertNull(properties?.sampleRateHz)
        assertNull(properties?.bitDepth)
        assertNull(properties?.channels)
        assertFalse(properties?.lossless ?: true)
    }

    @Test
    fun subsonicDoesNotInventAacOrAlacForM4a() {
        val song = json.parseToJsonElement(
            """{"codec":"m4a","contentType":"audio/mp4","suffix":"m4a"}"""
        ).jsonObject

        val properties = SubsonicAudioPropertiesMapper.map(song)

        assertEquals("MPEG-4 Audio", properties?.codec)
        assertEquals("MP4", properties?.container)
        assertNull(properties?.lossless)
    }

    @Test
    fun subsonicTranscodeAdvertisementDoesNotReplaceSourceProperties() {
        val song = json.parseToJsonElement(
            """
            {
              "contentType": "audio/flac",
              "suffix": "flac",
              "bitRate": 2784,
              "transcodedContentType": "audio/mpeg",
              "transcodedSuffix": "mp3"
            }
            """.trimIndent()
        ).jsonObject

        val properties = SubsonicAudioPropertiesMapper.map(song)

        assertEquals("FLAC", properties?.codec)
        assertEquals("FLAC", properties?.container)
        assertEquals(2_784, properties?.bitrateKbps)
        assertTrue(properties?.lossless == true)
    }

    @Test
    fun malformedOptionalSubsonicFieldsDoNotDiscardTrackProperties() {
        val song = json.parseToJsonElement(
            """
            {
              "suffix": "flac",
              "bitRate": "unknown",
              "bitDepth": {},
              "samplingRate": -1
            }
            """.trimIndent()
        ).jsonObject

        val properties = SubsonicAudioPropertiesMapper.map(song)

        assertEquals("FLAC", properties?.codec)
        assertNull(properties?.bitrateKbps)
        assertNull(properties?.bitDepth)
        assertNull(properties?.sampleRateHz)
    }

    @Test
    fun embyMapsPreferredMediaSourceAndConvertsBitrateBpsToKbps() {
        val item = json.parseToJsonElement(
            """
            {
              "MediaSourceId": "preferred",
              "MediaSources": [
                {
                  "Id": "other",
                  "Container": "mp3",
                  "MediaStreams": [{"Index": 0, "Type": "Audio", "Codec": "mp3"}]
                },
                {
                  "Id": "preferred",
                  "Container": "flac",
                  "DefaultAudioStreamIndex": 1,
                  "MediaStreams": [
                    {"Index": 0, "Type": "Subtitle", "Codec": "srt"},
                    {
                      "Index": 1,
                      "Type": "Audio",
                      "Codec": "flac",
                      "BitRate": 2784000,
                      "SampleRate": 96000,
                      "BitDepth": 24,
                      "Channels": 2,
                      "ChannelLayout": "stereo"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val mapping = EmbyAudioPropertiesMapper.map(item)

        assertEquals("preferred", mapping.sourceMediaId)
        assertEquals("FLAC", mapping.properties?.codec)
        assertEquals("FLAC", mapping.properties?.container)
        assertEquals(2_784, mapping.properties?.bitrateKbps)
        assertEquals(96_000, mapping.properties?.sampleRateHz)
        assertEquals(24, mapping.properties?.bitDepth)
        assertEquals(2, mapping.properties?.channels)
        assertEquals("stereo", mapping.properties?.channelLayout)
        assertTrue(mapping.properties?.lossless == true)
    }

    @Test
    fun embyKeepsCodecAndContainerSeparate() {
        val item = json.parseToJsonElement(
            """
            {
              "MediaSources": [{
                "Id": "source",
                "Container": "m4a",
                "MediaStreams": [{
                  "Type": "Audio",
                  "Codec": "alac",
                  "BitRate": 1350000
                }]
              }]
            }
            """.trimIndent()
        ).jsonObject

        val properties = EmbyAudioPropertiesMapper.map(item).properties

        assertEquals("ALAC", properties?.codec)
        assertEquals("MP4", properties?.container)
        assertEquals(1_350, properties?.bitrateKbps)
        assertTrue(properties?.lossless == true)
    }
}
