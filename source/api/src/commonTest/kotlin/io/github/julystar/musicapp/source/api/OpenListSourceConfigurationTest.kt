package io.github.julystar.musicapp.source.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenListSourceConfigurationTest {
    @Test
    fun configurationRedactsPassword() {
        val configuration = OpenListSourceConfiguration(
            alias = "OpenList",
            address = "https://openlist.example",
            username = "alice",
            password = "secret",
            isGuest = false,
            otpCode = "otp-secret",
        )

        assertFalse("secret" in configuration.toString())
        assertTrue("<redacted>" in configuration.toString())
        assertFalse("otp-secret" in configuration.toString())
    }

    @Test
    fun providerConfigurationIsSafeAndToleratesMalformedInput() {
        assertEquals(OpenListProviderConfiguration(), OpenListProviderConfigurationCodec.decode(null))
        assertEquals(OpenListProviderConfiguration(), OpenListProviderConfigurationCodec.decode("not-json"))
        assertEquals(OpenListProviderConfiguration(), OpenListProviderConfigurationCodec.decode("{\"requiresOtp\":null}"))
        assertEquals(
            OpenListProviderConfiguration(true),
            OpenListProviderConfigurationCodec.decode("{\"requiresOtp\":true,\"future\":{\"token\":\"secret\"}}"),
        )
        val encoded = OpenListProviderConfigurationCodec.encode(OpenListProviderConfiguration(true))
        assertTrue(encoded.contains("requiresOtp"))
        assertFalse(encoded.contains("password"))
        assertFalse(encoded.contains("token"))
    }

    @Test
    fun remoteServerKindRemainsExactlyThreeMembers() {
        assertEquals(
            setOf(RemoteServerKind.Navidrome, RemoteServerKind.OpenSubsonic, RemoteServerKind.Emby),
            enumValues<RemoteServerKind>().toSet(),
        )
    }
}
