package io.github.julystar.musicapp.source.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class OpenSubsonicProviderConfiguration(
    val streamMaxBitRate: Int = 0,
    val downloadMaxBitRate: Int = 0,
    val coverArtSize: Int = DEFAULT_COVER_ART_SIZE,
    val remoteWriteEnabled: Boolean = false,
    val secondaryBaseUrl: String? = null,
    val openSubsonicCapabilities: OpenSubsonicCapabilitySnapshot? = null,
) {
    fun sanitized(): OpenSubsonicProviderConfiguration = copy(
        streamMaxBitRate = streamMaxBitRate.coerceAtLeast(0),
        downloadMaxBitRate = downloadMaxBitRate.coerceAtLeast(0),
        coverArtSize = coverArtSize.takeIf { it in ALLOWED_COVER_ART_SIZES } ?: DEFAULT_COVER_ART_SIZE,
        secondaryBaseUrl = sanitizeRemoteServerBaseUrl(secondaryBaseUrl),
    )

    companion object {
        const val DEFAULT_COVER_ART_SIZE = 512
        val ALLOWED_COVER_ART_SIZES = setOf(256, 512, 768, 1024)
        val Defaults = OpenSubsonicProviderConfiguration()
    }
}

object OpenSubsonicProviderConfigurationCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(value: String?): OpenSubsonicProviderConfiguration = runCatching {
        val root = json.parseToJsonElement(value.orEmpty()).jsonObject
        OpenSubsonicProviderConfiguration(
            streamMaxBitRate = root.int("streamMaxBitRate") ?: 0,
            downloadMaxBitRate = root.int("downloadMaxBitRate") ?: 0,
            coverArtSize = root.int("coverArtSize") ?: OpenSubsonicProviderConfiguration.DEFAULT_COVER_ART_SIZE,
            remoteWriteEnabled = root.boolean("remoteWriteEnabled") ?: false,
            secondaryBaseUrl = root.string("secondaryBaseUrl"),
            openSubsonicCapabilities = root.capabilitySnapshot(),
        ).sanitized()
    }.getOrDefault(OpenSubsonicProviderConfiguration.Defaults)

    fun encode(value: OpenSubsonicProviderConfiguration): String {
        val safe = value.sanitized()
        return buildJsonObject {
            put("streamMaxBitRate", safe.streamMaxBitRate)
            put("downloadMaxBitRate", safe.downloadMaxBitRate)
            put("coverArtSize", safe.coverArtSize)
            put("remoteWriteEnabled", safe.remoteWriteEnabled)
            put("secondaryBaseUrl", safe.secondaryBaseUrl)
            safe.openSubsonicCapabilities?.let { snapshot ->
                put("openSubsonicCapabilities", buildJsonObject {
                    put("checkedAtEpochMs", snapshot.checkedAtEpochMs)
                    put("extensions", buildJsonArray {
                        snapshot.extensions.forEach { extension ->
                            add(buildJsonObject {
                                put("name", extension.name)
                                put("versions", buildJsonArray {
                                    extension.versions.forEach { add(JsonPrimitive(it)) }
                                })
                            })
                        }
                    })
                })
            }
        }.toString()
    }
}

private fun JsonObject.capabilitySnapshot(): OpenSubsonicCapabilitySnapshot? {
    val capabilities = this["openSubsonicCapabilities"] as? JsonObject ?: return null
    val checkedAtEpochMs = capabilities.long("checkedAtEpochMs") ?: return null
    val extensions = (capabilities["extensions"] as? JsonArray).orEmpty().mapNotNull { element ->
        val extension = element as? JsonObject ?: return@mapNotNull null
        val name = extension.string("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        OpenSubsonicExtension(
            name = name,
            versions = (extension["versions"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.intOrNull },
        )
    }
    return OpenSubsonicCapabilitySnapshot(extensions, checkedAtEpochMs)
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull

private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
