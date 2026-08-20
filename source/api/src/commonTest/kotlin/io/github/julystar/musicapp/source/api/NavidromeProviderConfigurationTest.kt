package io.github.julystar.musicapp.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavidromeProviderConfigurationTest {
    @Test
    fun emptyAndLegacyValuesUseSafeDefaults() {
        assertEquals(NavidromeProviderConfiguration.Defaults, NavidromeProviderConfigurationCodec.decode(null))
        assertEquals(NavidromeProviderConfiguration.Defaults, NavidromeProviderConfigurationCodec.decode("legacy-config"))
    }

    @Test
    fun valuesRoundTripAndCredentialBearingUrlsAreRejected() {
        val config = NavidromeProviderConfiguration(
            streamMaxBitRate = 192,
            downloadMaxBitRate = 320,
            coverArtSize = 1024,
            remoteWriteEnabled = true,
            secondaryBaseUrl = "https://example.test/navidrome,mirror",
        )
        val encoded = NavidromeProviderConfigurationCodec.encode(config)
        assertTrue("password" !in encoded)
        assertEquals(config, NavidromeProviderConfigurationCodec.decode(encoded))
        assertNull(
            NavidromeProviderConfigurationCodec.decode(
                "{\"secondaryBaseUrl\":\"https://user:password@example.test\"}"
            ).secondaryBaseUrl
        )
        val unsafeEncoded = NavidromeProviderConfigurationCodec.encode(
            config.copy(secondaryBaseUrl = "https://user:password@example.test/?token=secret")
        )
        assertTrue("password" !in unsafeEncoded && "token" !in unsafeEncoded)
        assertTrue(unsafeEncoded.contains("\"secondaryBaseUrl\":null"))
    }

    @Test
    fun escapedSecondaryUrlCharactersRoundTripWithoutSplittingFields() {
        val value = NavidromeProviderConfiguration(
            secondaryBaseUrl = "https://example.test/a\\b\"c,mirror",
        )
        val encoded = NavidromeProviderConfigurationCodec.encode(value)
        assertEquals(value, NavidromeProviderConfigurationCodec.decode(encoded))
    }

    @Test
    fun invalidValuesAreCoercedAndWritesRemainExplicit() {
        val config = NavidromeProviderConfigurationCodec.decode(
            "{\"streamMaxBitRate\":-1,\"downloadMaxBitRate\":-2,\"coverArtSize\":123,\"remoteWriteEnabled\":true}"
        )
        assertEquals(0, config.streamMaxBitRate)
        assertEquals(0, config.downloadMaxBitRate)
        assertEquals(512, config.coverArtSize)
        assertTrue(config.remoteWriteEnabled)
        assertFalse(NavidromeProviderConfigurationCodec.decode("{}").remoteWriteEnabled)
        assertNull(NavidromeProviderConfigurationCodec.decode("{\"secondaryBaseUrl\":\"https://example.test/?token=secret\"}").secondaryBaseUrl)
        assertNull(NavidromeProviderConfigurationCodec.decode("{\"secondaryBaseUrl\":\"https://example.test/#secret\"}").secondaryBaseUrl)
        assertEquals(NavidromeProviderConfiguration.Defaults, NavidromeProviderConfigurationCodec.decode("{broken"))
    }
}
