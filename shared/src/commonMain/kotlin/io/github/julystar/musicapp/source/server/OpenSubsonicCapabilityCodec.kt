package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.OpenSubsonicCapabilitySnapshot
import io.github.julystar.musicapp.source.api.OpenSubsonicProviderConfigurationCodec

internal object OpenSubsonicCapabilityCodec {
    fun encode(
        snapshot: OpenSubsonicCapabilitySnapshot,
        remoteWriteEnabled: Boolean = false,
    ): String = OpenSubsonicProviderConfigurationCodec.encode(
        OpenSubsonicProviderConfigurationCodec.decode(null).copy(
            remoteWriteEnabled = remoteWriteEnabled,
            openSubsonicCapabilities = snapshot,
        ),
    )

    fun remoteWriteEnabled(value: String?): Boolean =
        OpenSubsonicProviderConfigurationCodec.decode(value).remoteWriteEnabled

    fun withRemoteWriteEnabled(value: String?, enabled: Boolean): String =
        OpenSubsonicProviderConfigurationCodec.encode(
            OpenSubsonicProviderConfigurationCodec.decode(value).copy(remoteWriteEnabled = enabled),
        )

    fun decode(value: String?): OpenSubsonicCapabilitySnapshot? =
        OpenSubsonicProviderConfigurationCodec.decode(value).openSubsonicCapabilities
}
