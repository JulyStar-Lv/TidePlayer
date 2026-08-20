package io.github.julystar.musicapp.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EmbyProviderConfigurationTest {
    @Test
    fun codecKeepsOnlyNonSecretIdentityAndSanitizesSecondaryUrl() {
        val encoded = EmbyProviderConfigurationCodec.encode(
            EmbyProviderConfiguration(
                serverId = "server-1",
                serverName = "Living Room",
                secondaryBaseUrl = "https://backup.example/api",
            ),
        )
        val decoded = EmbyProviderConfigurationCodec.decode(encoded)
        assertEquals("server-1", decoded.serverId)
        assertEquals("Living Room", decoded.serverName)
        assertEquals("https://backup.example/api", decoded.secondaryBaseUrl)
        assertFalse(encoded.contains("password"))
        assertFalse(encoded.contains("token"))
    }

    @Test
    fun malformedOlderAndCredentialBearingValuesBecomeSafeDefaults() {
        assertEquals(EmbyProviderConfiguration(), EmbyProviderConfigurationCodec.decode("legacy"))
        assertEquals(EmbyProviderConfiguration(), EmbyProviderConfigurationCodec.decode("{bad"))
        assertEquals(EmbyProviderConfiguration(), EmbyProviderConfigurationCodec.decode("{serverId:invented}"))
        assertEquals(EmbyProviderConfiguration(), EmbyProviderConfigurationCodec.decode(
            "{\"serverId\":null,\"serverName\":null,\"secondaryBaseUrl\":null}",
        ))
        assertEquals(EmbyProviderConfiguration(), EmbyProviderConfigurationCodec.decode("{}"))
        assertEquals("server-1", EmbyProviderConfigurationCodec.decode(
            "{\"serverId\":\"server-1\",\"unknown\":\"ignored\"}",
        ).serverId)
        val sanitized = EmbyProviderConfiguration(
            serverId = " server-1 ",
            secondaryBaseUrl = "https://user:password@backup.example/?token=secret",
        ).sanitized()
        assertEquals("server-1", sanitized.serverId)
        assertNull(sanitized.secondaryBaseUrl)
        assertFalse(EmbyProviderConfigurationCodec.encode(sanitized).contains("password"))
        assertFalse(EmbyProviderConfigurationCodec.encode(sanitized).contains("secret"))
    }

    @Test
    fun unknownJsonValuesAreIgnoredAndKnownStringsAreEscaped() {
        val decoded = EmbyProviderConfigurationCodec.decode(
            """{"serverId":"s\"\\\\\n界","futureNumber":1,"futureObject":{"token":"secret"},"futureArray":[true]}""",
        )
        assertEquals("s\"\\\\\n界", decoded.serverId)

        val encoded = EmbyProviderConfigurationCodec.encode(decoded)
        assertEquals(decoded, EmbyProviderConfigurationCodec.decode(encoded))
        assertFalse(encoded.contains("futureNumber"))
        assertFalse(encoded.contains("secret"))
    }
}
