package io.github.julystar.musicapp.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenSubsonicProviderConfigurationTest {
    @Test
    fun emptyOldAndInvalidJsonUseSafeDefaults() {
        assertEquals(OpenSubsonicProviderConfiguration.Defaults, OpenSubsonicProviderConfigurationCodec.decode(null))
        assertEquals(OpenSubsonicProviderConfiguration.Defaults, OpenSubsonicProviderConfigurationCodec.decode(""))
        assertEquals(OpenSubsonicProviderConfiguration.Defaults, OpenSubsonicProviderConfigurationCodec.decode("legacy"))
        assertEquals(OpenSubsonicProviderConfiguration.Defaults, OpenSubsonicProviderConfigurationCodec.decode("{bad"))
        assertEquals(
            OpenSubsonicProviderConfiguration.Defaults.copy(remoteWriteEnabled = true),
            OpenSubsonicProviderConfigurationCodec.decode("{\"remoteWriteEnabled\":true}"),
        )
    }

    @Test
    fun advancedFieldsAndCapabilitySnapshotRoundTripTogether() {
        val configuration = OpenSubsonicProviderConfiguration(
            streamMaxBitRate = 192,
            downloadMaxBitRate = 320,
            coverArtSize = 1024,
            remoteWriteEnabled = true,
            secondaryBaseUrl = "https://secondary.example/subsonic",
            openSubsonicCapabilities = OpenSubsonicCapabilitySnapshot(
                extensions = listOf(OpenSubsonicExtension("songLyrics", listOf(1, 2))),
                checkedAtEpochMs = 42,
            ),
        )
        val encoded = OpenSubsonicProviderConfigurationCodec.encode(configuration)
        assertEquals(configuration, OpenSubsonicProviderConfigurationCodec.decode(encoded))
        assertFalse(encoded.contains("password"))
        assertFalse(encoded.contains("token"))
    }

    @Test
    fun invalidAdvancedValuesAndUnsafeSecondaryAreSanitizedWithoutDroppingCapabilities() {
        val decoded = OpenSubsonicProviderConfigurationCodec.decode(
            """{"streamMaxBitRate":-1,"downloadMaxBitRate":-2,"coverArtSize":1,"secondaryBaseUrl":"https://user:password@example.test/?token=secret","openSubsonicCapabilities":{"checkedAtEpochMs":9,"extensions":[]}}""",
        )
        assertEquals(0, decoded.streamMaxBitRate)
        assertEquals(0, decoded.downloadMaxBitRate)
        assertEquals(512, decoded.coverArtSize)
        assertNull(decoded.secondaryBaseUrl)
        assertEquals(9, decoded.openSubsonicCapabilities?.checkedAtEpochMs)
    }
}
