package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.database.PluginConfigEntity
import io.github.julystar.musicapp.database.PluginDao
import io.github.julystar.musicapp.database.PluginEntity
import io.github.julystar.musicapp.plugin.currentTimeMillis
import io.github.julystar.musicapp.plugin.install.ManifestConfigField
import io.github.julystar.musicapp.plugin.install.ManifestConfigOption
import io.github.julystar.musicapp.plugin.install.ParsedManifest
import io.github.julystar.musicapp.plugin.runtime.InstalledPlugin
import io.github.julystar.musicapp.plugin.runtime.PluginConfigProvider
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path

data class PluginSummary(
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val capabilities: List<String>,
    val enabled: Boolean,
    val allowManualLookup: Boolean,
    val allowAutomaticLookup: Boolean,
    val allowBatchLookup: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val entryFile: String,
    val includeDirs: List<String>,
    val iconPath: String?,
    val configFields: List<ManifestConfigField>,
    val lastError: String?,
    val lastErrorAt: Long?,
    val apiVersion: Int = 1,
    val minHostApiVersion: Int = 1,
)

class PluginRepository(
    private val pluginDao: PluginDao,
    private val pluginsDir: Path,
) : PluginConfigProvider {
    private val json = Json { ignoreUnknownKeys = true }

    fun allPlugins(): Flow<List<PluginSummary>> = pluginDao.all().map { plugins ->
        plugins.map { plugin -> plugin.toSummary() }
    }

    suspend fun allSnapshot(): List<PluginSummary> =
        pluginDao.allSnapshot().map { plugin -> plugin.toSummary() }

    suspend fun getPlugin(pluginId: String): PluginSummary? =
        pluginDao.findByPluginId(pluginId)?.toSummary()

    override suspend fun config(pluginId: String): Map<String, String> {
        val runtimeKeys = pluginDao.findByPluginId(pluginId)
            ?.toSummary()
            ?.configFields
            ?.filterNot { it.type == "markdown" }
            ?.mapTo(mutableSetOf(), ManifestConfigField::key)
            .orEmpty()
        return pluginDao.configsFor(pluginId)
            .asSequence()
            .filter { it.configKey in runtimeKeys }
            .associate { it.configKey to it.configValue }
    }

    suspend fun setConfig(
        pluginId: String,
        key: String,
        value: String?,
    ) {
        if (value == null) {
            pluginDao.deleteConfig(pluginId, key)
        } else {
            pluginDao.setConfig(
                PluginConfigEntity(
                    pluginId = pluginId,
                    configKey = key,
                    configValue = value,
                    updatedAt = currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun importPluginDefaults(manifest: ParsedManifest) {
        manifest.configFields
            .filter { field ->
                field.type != "markdown" &&
                    field.defaultValue != null &&
                    field.defaultValue.isNotEmpty()
            }
            .forEach { field ->
                if (pluginDao.configValue(manifest.id, field.key) == null) {
                    setConfig(manifest.id, field.key, field.defaultValue)
                }
            }
    }

    suspend fun setEnabled(
        pluginId: String,
        enabled: Boolean,
    ) {
        pluginDao.setEnabled(pluginId, enabled)
    }

    suspend fun setLookupPermissions(
        pluginId: String,
        allowManual: Boolean,
        allowAutomatic: Boolean,
        allowBatch: Boolean,
    ) {
        pluginDao.setLookupPermissions(
            pluginId = pluginId,
            allowManual = allowManual,
            allowAutomatic = allowAutomatic,
            allowBatch = allowBatch,
        )
    }

    suspend fun recordError(
        pluginId: String,
        error: Throwable,
    ) {
        val message = error.message
            ?.take(2_000)
            ?.ifBlank { error::class.simpleName }
            ?: error::class.simpleName
            ?: "Plugin execution failed"
        pluginDao.setLastError(pluginId, message, currentTimeMillis())
    }

    suspend fun clearError(pluginId: String) {
        pluginDao.clearLastError(pluginId)
    }

    fun PluginSummary.toInstalledPlugin(): InstalledPlugin = InstalledPlugin(
        descriptor = PluginRuntimeDescriptor(
            pluginId = id,
            pluginName = name,
            pluginVersionCode = versionCode,
            pluginUpdatedAt = updatedAt,
            entryFile = entryFile,
            includeDirs = includeDirs,
            directory = (pluginsDir / id).toString(),
            apiVersion = apiVersion,
            minHostApiVersion = minHostApiVersion,
        ),
        capabilities = capabilities.ifEmpty { listOf("searchSongs") }.toSet(),
        enabled = enabled,
        allowManualLookup = allowManualLookup,
        allowAutomaticLookup = allowAutomaticLookup,
        allowBatchLookup = allowBatchLookup,
    )

    private fun PluginEntity.toSummary(): PluginSummary = PluginSummary(
        id = pluginId,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        author = author,
        description = description,
        capabilities = decodeStringList(capabilitiesJson),
        enabled = enabled,
        allowManualLookup = allowManualLookup,
        allowAutomaticLookup = allowAutomaticLookup,
        allowBatchLookup = allowBatchLookup,
        installedAt = installedAt,
        updatedAt = updatedAt,
        entryFile = entryFile,
        includeDirs = decodeStringList(includeDirsJson),
        iconPath = iconPath,
        configFields = decodeConfigFields(manifestRawJson),
        lastError = lastError,
        lastErrorAt = lastErrorAt,
        apiVersion = apiVersion,
        minHostApiVersion = minHostApiVersion,
    )

    private fun decodeStringList(raw: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(raw)
    }.getOrDefault(emptyList())

    private fun decodeConfigFields(manifestRawJson: String): List<ManifestConfigField> = runCatching {
        val manifest = json.parseToJsonElement(manifestRawJson).jsonObject
        (manifest["configFields"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.map { field ->
                ManifestConfigField(
                    key = field["key"]?.jsonPrimitive?.content.orEmpty(),
                    title = field["title"]?.jsonPrimitive?.content.orEmpty(),
                    summary = field["summary"]?.jsonPrimitive?.contentOrNull,
                    group = field["group"]?.jsonPrimitive?.contentOrNull,
                    type = field["type"]?.jsonPrimitive?.content ?: "text",
                    required = field["required"]?.jsonPrimitive?.booleanOrNull == true,
                    defaultValue = field["defaultValue"]?.jsonPrimitive?.contentOrNull,
                    options = (field["options"] as? JsonArray)
                        ?.mapNotNull { it as? JsonObject }
                        ?.map { option ->
                            ManifestConfigOption(
                                value = option["value"]?.jsonPrimitive?.content.orEmpty(),
                                label = option["label"]?.jsonPrimitive?.content.orEmpty(),
                                summary = option["summary"]?.jsonPrimitive?.contentOrNull,
                            )
                        }
                        .orEmpty(),
                    dependency = field["dependency"] as? JsonObject,
                )
            }
            .orEmpty()
    }.getOrDefault(emptyList())
}
