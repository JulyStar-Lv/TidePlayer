package io.github.julystar.musicapp.plugin.install

import io.github.julystar.musicapp.database.PluginConfigEntity
import io.github.julystar.musicapp.database.PluginDao
import io.github.julystar.musicapp.database.PluginEntity
import io.github.julystar.musicapp.plugin.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import uniffi.app_backend.extractPluginZip

private const val MAX_CONFIG_DEPENDENCY_DEPTH = 16

class PluginInstallError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class ManifestConfigField(
    val key: String,
    val title: String,
    val summary: String? = null,
    val group: String? = null,
    val type: String = "text",
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<ManifestConfigOption> = emptyList(),
    val dependency: JsonObject? = null,
)

data class ManifestConfigOption(
    val value: String,
    val label: String,
    val summary: String? = null,
)

data class ParsedManifest(
    val id: String,
    val name: String,
    val versionCode: Long,
    val versionName: String,
    val author: String,
    val description: String,
    val apiVersion: Int,
    val minHostApiVersion: Int,
    val entryFile: String,
    val includeDirs: List<String>,
    val icon: String?,
    val capabilities: List<String>,
    val configFields: List<ManifestConfigField>,
    val raw: String,
)

data class PluginInstallFailure(
    val root: String,
    val reason: String,
)

data class PluginInstallResult(
    val installed: List<ParsedManifest>,
    val failed: List<PluginInstallFailure> = emptyList(),
)

class PluginInstaller(
    private val pluginDao: PluginDao,
    private val pluginsDir: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val MIN_SUPPORTED_API_VERSION = 1
        const val MAX_SUPPORTED_API_VERSION = 4
        const val HOST_API_VERSION = 3

        private const val MAX_ARCHIVE_FILES = 512L
        private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
        private const val MAX_ARCHIVE_DEPTH = 32L
        private const val MAX_PLUGIN_COUNT = 32

        private val PLUGIN_ID_PATTERN = Regex(
            "^[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*)+$",
        )
        private val SUPPORTED_ICON_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        private val SUPPORTED_CONFIG_TYPES = setOf(
            "text",
            "password",
            "number",
            "switch",
            "dropdown",
            "textarea",
            "markdown",
            // Legacy MelodyTrove aliases retained for already-authored local plugins.
            "boolean",
            "select",
        )
        private val SUPPORTED_CAPABILITIES = setOf(
            "searchSongs",
            "getLyrics",
            "searchCovers",
        )
    }

    suspend fun installFromZip(zipPath: Path): ParsedManifest {
        val result = installAllFromZip(zipPath)
        if (result.installed.isEmpty()) {
            val reason = result.failed.firstOrNull()?.reason ?: "no installable plugin found"
            throw PluginInstallError(reason)
        }
        return result.installed.first()
    }

    suspend fun installAllFromZip(zipPath: Path): PluginInstallResult =
        withContext(Dispatchers.Default) {
            val tempDir = pluginsDir / ".tmp-import-${currentTimeMillis()}"
            try {
                fileSystem.createDirectories(tempDir)
                extractPluginZip(
                    zipPath.toString(),
                    tempDir.toString(),
                    MAX_ARCHIVE_FILES.toULong(),
                    MAX_ARCHIVE_BYTES.toULong(),
                    MAX_ARCHIVE_DEPTH.toULong(),
                )
                installCandidates(tempDir)
            } catch (error: PluginInstallError) {
                throw error
            } catch (error: Throwable) {
                throw PluginInstallError(
                    "install failed: ${error.message ?: "unknown"}",
                    error,
                )
            } finally {
                if (fileSystem.exists(tempDir)) fileSystem.deleteRecursively(tempDir)
            }
        }

    suspend fun uninstall(pluginId: String) = withContext(Dispatchers.Default) {
        pluginDao.deleteConfigs(pluginId)
        pluginDao.deleteByPluginId(pluginId)
        val directory = pluginsDir / pluginId
        if (fileSystem.exists(directory)) fileSystem.deleteRecursively(directory)
    }

    internal fun readManifest(directory: Path): ParsedManifest {
        val path = directory / "manifest.json"
        require(fileSystem.metadataOrNull(path)?.isRegularFile == true) {
            "manifest.json not found"
        }
        val raw = fileSystem.read(path) { readUtf8() }
        val root = json.parseToJsonElement(raw).jsonObject
        val configFields = (root["configFields"] as? JsonArray)
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
                    options = field.configOptions(),
                    dependency = field["dependency"] as? JsonObject,
                )
            }
            .orEmpty()
        return ParsedManifest(
            id = root.string("id"),
            name = root.string("name"),
            versionCode = root.long("versionCode"),
            versionName = root.string("versionName"),
            author = root.stringOrNull("author").orEmpty(),
            description = root.stringOrNull("description").orEmpty(),
            apiVersion = root.int("apiVersion"),
            minHostApiVersion = root.intOrNull("minHostApiVersion") ?: 1,
            entryFile = root.stringOrNull("entry")
                ?: root.stringOrNull("entryFile")
                ?: "source.js",
            includeDirs = (root["includeDirs"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            icon = root["icon"]?.jsonPrimitive?.contentOrNull,
            capabilities = (root["capabilities"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            configFields = configFields,
            raw = raw,
        )
    }

    internal fun validateManifest(manifest: ParsedManifest) {
        require(PLUGIN_ID_PATTERN.matches(manifest.id)) {
            "plugin id must be reverse-domain format"
        }
        require(manifest.apiVersion in MIN_SUPPORTED_API_VERSION..MAX_SUPPORTED_API_VERSION) {
            "unsupported plugin protocol ${manifest.apiVersion}: supported range is " +
                "$MIN_SUPPORTED_API_VERSION..$MAX_SUPPORTED_API_VERSION"
        }
        require(manifest.minHostApiVersion in 1..HOST_API_VERSION) {
            "unsupported host API ${manifest.minHostApiVersion}: supported range is 1..$HOST_API_VERSION"
        }
        require(manifest.versionCode >= 1) { "versionCode must be >= 1" }
        require(manifest.versionName.isNotBlank()) { "versionName is required" }
        require(manifest.name.isNotBlank()) { "plugin name is required" }
        require(manifest.entryFile.endsWith(".js", ignoreCase = true)) {
            "entry must be a JavaScript file"
        }
        require(manifest.includeDirs.none(String::isBlank)) { "includeDirs contains a blank path" }
        require(manifest.includeDirs.distinct().size == manifest.includeDirs.size) {
            "includeDirs contains duplicates"
        }
        require(manifest.capabilities.none(String::isBlank)) {
            "capabilities contains a blank value"
        }
        require(manifest.capabilities.distinct().size == manifest.capabilities.size) {
            "capabilities contains duplicates"
        }
        require(manifest.capabilities.all { it in SUPPORTED_CAPABILITIES }) {
            "capabilities contains an unsupported value"
        }
        manifest.icon?.let { icon ->
            require(icon.substringAfterLast('.', "").lowercase() in SUPPORTED_ICON_EXTENSIONS) {
                "unsupported plugin icon type"
            }
        }
        require(manifest.configFields.map(ManifestConfigField::key).distinct().size == manifest.configFields.size) {
            "configFields contains duplicate keys"
        }
        manifest.configFields.forEach { field ->
            require(field.key.isNotBlank()) { "config field key is required" }
            require(field.title.isNotBlank()) { "config field title is required: ${field.key}" }
            require(field.type in SUPPORTED_CONFIG_TYPES) {
                "unsupported config field type '${field.type}' for ${field.key}"
            }
            require(field.type != "dropdown" || field.options.isNotEmpty()) {
                "dropdown config field requires options: ${field.key}"
            }
            require(field.options.map(ManifestConfigOption::value).distinct().size == field.options.size) {
                "config field options contain duplicate values: ${field.key}"
            }
            field.options.forEach { option ->
                require(option.value.isNotBlank()) {
                    "config field option value is required: ${field.key}"
                }
                require(option.label.isNotBlank()) {
                    "config field option label is required: ${field.key}"
                }
            }
            require(field.dependency?.isValidConfigDependency() != false) {
                "invalid config field dependency: ${field.key}"
            }
        }
    }

    private suspend fun installCandidates(tempDir: Path): PluginInstallResult {
        val candidates = buildCandidates(tempDir)
        val duplicateIds = candidates
            .mapNotNull { it.getOrNull()?.manifest?.id }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val failed = mutableListOf<PluginInstallFailure>()
        val valid = candidates.mapNotNull { result ->
            result.fold(
                onSuccess = { candidate ->
                    if (candidate.manifest.id in duplicateIds) {
                        failed += PluginInstallFailure(
                            candidate.relativeRoot,
                            "duplicate plugin id in archive: ${candidate.manifest.id}",
                        )
                        null
                    } else {
                        candidate
                    }
                },
                onFailure = { error ->
                    failed += PluginInstallFailure(
                        root = ".",
                        reason = error.message ?: error::class.simpleName.orEmpty(),
                    )
                    null
                },
            )
        }

        val installed = mutableListOf<ParsedManifest>()
        for (candidate in valid.sortedBy(PluginCandidate::relativeRoot)) {
            try {
                checkConflicts(candidate.manifest)
                installCandidate(candidate, valid)
                installed += candidate.manifest
            } catch (error: Throwable) {
                failed += PluginInstallFailure(
                    candidate.relativeRoot,
                    error.message ?: error::class.simpleName.orEmpty(),
                )
            }
        }
        return PluginInstallResult(installed = installed, failed = failed)
    }

    private fun buildCandidates(tempDir: Path): List<Result<PluginCandidate>> {
        val manifests = fileSystem.listRecursively(tempDir)
            .filter { path ->
                path.name == "manifest.json" && fileSystem.metadata(path).isRegularFile
            }
            .take(MAX_PLUGIN_COUNT + 1)
            .toList()
        require(manifests.isNotEmpty()) { "manifest.json not found" }
        require(manifests.size <= MAX_PLUGIN_COUNT) { "archive contains too many plugins" }
        return manifests.map { manifestPath ->
            runCatching {
                val root = manifestPath.parent ?: error("manifest has no parent directory")
                val manifest = readManifest(root)
                validateManifest(manifest)
                validatePluginLayout(root, manifest)
                PluginCandidate(
                    root = root,
                    manifest = manifest,
                    relativeRoot = root.relativePathTo(tempDir).ifBlank { "." },
                )
            }
        }
    }

    private fun validatePluginLayout(root: Path, manifest: ParsedManifest) {
        val entry = resolveUnder(root, manifest.entryFile, "entry")
        require(fileSystem.metadataOrNull(entry)?.isRegularFile == true) {
            "entry file not found: ${manifest.entryFile}"
        }
        require(entry.name.endsWith(".js", ignoreCase = true)) {
            "entry must be a JavaScript file"
        }
        manifest.includeDirs.forEach { includeDir ->
            val directory = resolveUnder(root, includeDir, "includeDir")
            require(fileSystem.metadataOrNull(directory)?.isDirectory == true) {
                "includeDir not found: $includeDir"
            }
        }
        manifest.icon?.let { icon ->
            val iconPath = resolveUnder(root, icon, "icon")
            require(fileSystem.metadataOrNull(iconPath)?.isRegularFile == true) {
                "icon not found: $icon"
            }
        }
    }

    private suspend fun checkConflicts(manifest: ParsedManifest) {
        val existing = pluginDao.findByPluginId(manifest.id) ?: return
        if (existing.versionCode > manifest.versionCode) {
            throw PluginInstallError(
                "plugin downgrade is not allowed: installed ${existing.versionCode}, requested ${manifest.versionCode}",
            )
        }
    }

    private suspend fun installCandidate(
        candidate: PluginCandidate,
        allCandidates: List<PluginCandidate>,
    ) {
        fileSystem.createDirectories(pluginsDir)
        val timestamp = currentTimeMillis()
        val stagingDir = pluginsDir / ".staging-${candidate.manifest.id}-$timestamp"
        val backupDir = pluginsDir / ".backup-${candidate.manifest.id}-$timestamp"
        val destinationDir = pluginsDir / candidate.manifest.id
        val existing = pluginDao.findByPluginId(candidate.manifest.id)
        var destinationReplaced = false
        var databaseWritten = false

        try {
            if (fileSystem.exists(stagingDir)) fileSystem.deleteRecursively(stagingDir)
            if (fileSystem.exists(backupDir)) fileSystem.deleteRecursively(backupDir)
            fileSystem.createDirectories(stagingDir)
            val excludedRoots = allCandidates
                .map { it.root.normalized() }
                .filter { root ->
                    root != candidate.root.normalized() && root.isUnderOrSame(candidate.root)
                }
            copyPluginRoot(candidate.root, stagingDir, excludedRoots)

            if (fileSystem.exists(destinationDir)) {
                fileSystem.atomicMove(destinationDir, backupDir)
            }
            fileSystem.atomicMove(stagingDir, destinationDir)
            destinationReplaced = true

            val now = currentTimeMillis()
            pluginDao.upsert(
                PluginEntity(
                    id = existing?.id ?: 0,
                    pluginId = candidate.manifest.id,
                    name = candidate.manifest.name,
                    versionCode = candidate.manifest.versionCode,
                    versionName = candidate.manifest.versionName,
                    author = candidate.manifest.author,
                    description = candidate.manifest.description,
                    apiVersion = candidate.manifest.apiVersion,
                    minHostApiVersion = candidate.manifest.minHostApiVersion,
                    entryFile = candidate.manifest.entryFile,
                    includeDirsJson = json.encodeToString(candidate.manifest.includeDirs),
                    iconPath = candidate.manifest.icon?.let { (destinationDir / it).toString() },
                    capabilitiesJson = json.encodeToString(candidate.manifest.capabilities),
                    manifestRawJson = candidate.manifest.raw,
                    installedAt = existing?.installedAt ?: now,
                    updatedAt = now,
                    enabled = existing?.enabled ?: false,
                    allowManualLookup = existing?.allowManualLookup ?: true,
                    allowAutomaticLookup = existing?.allowAutomaticLookup ?: false,
                    allowBatchLookup = existing?.allowBatchLookup ?: false,
                    lastError = null,
                    lastErrorAt = null,
                ),
            )
            databaseWritten = true
            importDefaultConfig(candidate.manifest, now)

            if (fileSystem.exists(backupDir)) {
                runCatching { fileSystem.deleteRecursively(backupDir) }
            }
        } catch (error: Throwable) {
            if (databaseWritten) {
                if (existing == null) {
                    pluginDao.deleteConfigs(candidate.manifest.id)
                    pluginDao.deleteByPluginId(candidate.manifest.id)
                } else {
                    pluginDao.upsert(existing)
                }
            }
            if (destinationReplaced && fileSystem.exists(destinationDir)) {
                fileSystem.deleteRecursively(destinationDir)
            }
            if (fileSystem.exists(backupDir)) {
                fileSystem.atomicMove(backupDir, destinationDir)
            }
            throw error
        } finally {
            if (fileSystem.exists(stagingDir)) fileSystem.deleteRecursively(stagingDir)
            if (fileSystem.exists(backupDir) && fileSystem.exists(destinationDir)) {
                runCatching { fileSystem.deleteRecursively(backupDir) }
            }
        }
    }

    private suspend fun importDefaultConfig(
        manifest: ParsedManifest,
        timestamp: Long,
    ) {
        manifest.configFields
            .filter { field ->
                field.type != "markdown" &&
                    field.defaultValue != null &&
                    field.defaultValue.isNotEmpty()
            }
            .forEach { field ->
                if (pluginDao.configValue(manifest.id, field.key) == null) {
                    pluginDao.setConfig(
                        PluginConfigEntity(
                            pluginId = manifest.id,
                            configKey = field.key,
                            configValue = field.defaultValue.orEmpty(),
                            updatedAt = timestamp,
                        ),
                    )
                }
            }
    }

    private fun copyPluginRoot(
        sourceRoot: Path,
        targetRoot: Path,
        excludedRoots: List<Path>,
    ) {
        fileSystem.listRecursively(sourceRoot).forEach { source ->
            val normalizedSource = source.normalized()
            if (excludedRoots.any { normalizedSource.isUnderOrSame(it) }) return@forEach
            val relative = normalizedSource.relativePathTo(sourceRoot)
            if (relative.isBlank()) return@forEach
            val target = targetRoot / relative
            val metadata = fileSystem.metadata(source)
            when {
                metadata.isDirectory -> fileSystem.createDirectories(target)
                metadata.isRegularFile -> {
                    target.parent?.let(fileSystem::createDirectories)
                    fileSystem.read(source) {
                        val input = this
                        fileSystem.write(target) { writeAll(input) }
                    }
                }
            }
        }
    }

    private fun resolveUnder(
        root: Path,
        relative: String,
        label: String,
    ): Path {
        require(relative.isNotBlank()) { "$label path is blank" }
        val relativePath = relative.toPath(normalize = true)
        require(!relativePath.isAbsolute) { "$label path must be relative: $relative" }
        val resolved = (root / relative).normalized()
        require(resolved.isUnderOrSame(root)) { "$label path escapes plugin root: $relative" }
        return resolved
    }

    private data class PluginCandidate(
        val root: Path,
        val manifest: ParsedManifest,
        val relativeRoot: String,
    )
}

internal fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.content ?: error("manifest missing: $key")

internal fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: error("manifest missing: $key")

internal fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: error("manifest missing: $key")

internal fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.configOptions(): List<ManifestConfigOption> =
    (this["options"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.map { option ->
            ManifestConfigOption(
                value = option["value"]?.jsonPrimitive?.content.orEmpty(),
                label = option["label"]?.jsonPrimitive?.content.orEmpty(),
                summary = option["summary"]?.jsonPrimitive?.contentOrNull,
            )
        }
        .orEmpty()

private fun JsonObject.isValidConfigDependency(depth: Int = 0): Boolean {
    if (depth > MAX_CONFIG_DEPENDENCY_DEPTH) return false
    val dependencyTypes = keys.count { it == "match" || it == "and" || it == "or" || it == "not" }
    if (dependencyTypes != 1) return false

    (this["match"] as? JsonObject)?.let { match ->
        val key = match["key"]?.jsonPrimitive?.contentOrNull
        val value = match["value"]?.jsonPrimitive?.contentOrNull
        return !key.isNullOrBlank() && value != null
    }
    (this["and"] as? JsonObject)?.let { and ->
        val conditions = and["conditions"] as? JsonArray ?: return false
        return conditions.isNotEmpty() && conditions.all { condition ->
            (condition as? JsonObject)?.isValidConfigDependency(depth + 1) == true
        }
    }
    (this["or"] as? JsonObject)?.let { or ->
        val conditions = or["conditions"] as? JsonArray ?: return false
        return conditions.isNotEmpty() && conditions.all { condition ->
            (condition as? JsonObject)?.isValidConfigDependency(depth + 1) == true
        }
    }
    (this["not"] as? JsonObject)?.let { not ->
        val condition = not["condition"] as? JsonObject ?: return false
        return condition.isValidConfigDependency(depth + 1)
    }
    return false
}

private fun Path.isUnderOrSame(root: Path): Boolean {
    val target = normalized().toString().trimEnd('/', '\\')
    val rootText = root.normalized().toString().trimEnd('/', '\\')
    return target == rootText || target.startsWith("$rootText/") || target.startsWith("$rootText\\")
}

private fun Path.relativePathTo(root: Path): String {
    val target = normalized().toString()
    val rootText = root.normalized().toString().trimEnd('/', '\\')
    require(
        target.trimEnd('/', '\\') == rootText ||
            target.startsWith("$rootText/") ||
            target.startsWith("$rootText\\"),
    ) {
        "path is not under root"
    }
    return target.removePrefix(rootText).trimStart('/', '\\')
}
