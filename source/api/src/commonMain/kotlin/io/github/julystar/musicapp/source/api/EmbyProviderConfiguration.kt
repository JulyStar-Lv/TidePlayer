package io.github.julystar.musicapp.source.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class EmbyProviderConfiguration(
    val serverId: String? = null,
    val serverName: String? = null,
    val secondaryBaseUrl: String? = null,
) {
    fun sanitized(): EmbyProviderConfiguration = copy(
        serverId = serverId?.trim()?.takeIf(String::isNotBlank),
        serverName = serverName?.trim()?.takeIf(String::isNotBlank),
        secondaryBaseUrl = sanitizeRemoteServerBaseUrl(secondaryBaseUrl),
    )
}

object EmbyProviderConfigurationCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun decode(value: String?): EmbyProviderConfiguration = runCatching {
        val fields = json.parseToJsonElement(value.orEmpty()).jsonObject
        EmbyProviderConfiguration(
            serverId = fields.stringValue("serverId"),
            serverName = fields.stringValue("serverName"),
            secondaryBaseUrl = fields.stringValue("secondaryBaseUrl"),
        ).sanitized()
    }.getOrDefault(EmbyProviderConfiguration())

    fun encode(value: EmbyProviderConfiguration): String {
        val safe = value.sanitized()
        return buildJsonObject {
            put("serverId", safe.serverId)
            put("serverName", safe.serverName)
            put("secondaryBaseUrl", safe.secondaryBaseUrl)
        }.toString()
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
