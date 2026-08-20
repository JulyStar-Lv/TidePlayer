package io.github.julystar.musicapp.source.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class OpenListProviderConfiguration(
    val requiresOtp: Boolean = false,
)

object OpenListProviderConfigurationCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(value: String?): OpenListProviderConfiguration {
        if (value.isNullOrBlank()) return OpenListProviderConfiguration()
        return runCatching {
            val root = json.parseToJsonElement(value).jsonObject
            OpenListProviderConfiguration(
                requiresOtp = root["requiresOtp"]
                    ?.jsonPrimitive
                    ?.booleanOrNull
                    ?: false,
            )
        }.getOrDefault(OpenListProviderConfiguration())
    }

    fun encode(value: OpenListProviderConfiguration): String = buildJsonObject {
        put("requiresOtp", value.requiresOtp)
    }.toString()
}
