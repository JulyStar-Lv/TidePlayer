package io.github.julystar.musicapp.core.data.settings

import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.BackupSchedule
import io.github.julystar.musicapp.core.domain.model.SettingsBackupResult
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSelection
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.repository.SettingsBackupService
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.migration.AppIdentifiers
import io.github.julystar.musicapp.migration.LegacyIds
import io.github.julystar.musicapp.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import uniffi.app_backend.ctUploadWebdavBackup

class JsonSettingsBackupService(
    private val settingsRepository: SettingsRepository,
    appDocumentDirectory: String,
    private val scope: CoroutineScope,
    private val storageRepository: StorageRepository,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : SettingsBackupService {
    private val backupDirectory: Path = appDocumentDirectory.toPath() / "backups"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        scope.launch { runScheduledBackups() }
    }

    override suspend fun createBackup(): SettingsBackupResult = runCatching {
        fileSystem.createDirectories(backupDirectory)
        val settings = settingsRepository.settings.first()
        val envelope = SettingsBackupEnvelope(
            createdAtEpochMs = currentTimeMillis(),
            selection = settings.backup.selection,
            settings = settings,
        )
        val destination =
            backupDirectory / "${AppIdentifiers.BRAND_NAME}-settings-${envelope.createdAtEpochMs}.json"
        val content = json.encodeToString(envelope)
        fileSystem.write(destination) { writeUtf8(content) }
        settings.backup.webDavAccountId?.let { storageId ->
            uploadToWebDav(
                storageId = storageId,
                remoteDirectory = settings.backup.remoteDirectory,
                fileName = destination.name,
                content = content,
            )
        }
        SettingsBackupResult.Success(destination.toString())
    }.getOrElse { error ->
        SettingsBackupResult.Failure(error.message?.takeIf(String::isNotBlank) ?: error.toString())
    }

    override suspend fun restoreLatestBackup(): SettingsBackupResult = runCatching {
        val latest = latestBackupPath() ?: error("No settings backup is available")
        val envelope = fileSystem.read(latest) {
            json.decodeFromString<SettingsBackupEnvelope>(readUtf8())
        }
        val current = settingsRepository.settings.first()
        settingsRepository.replaceSettings(
            current.mergeBackup(envelope.settings, envelope.selection),
        )
        SettingsBackupResult.Success(latest.toString())
    }.getOrElse { error ->
        SettingsBackupResult.Failure(error.message?.takeIf(String::isNotBlank) ?: error.toString())
    }

    private suspend fun runScheduledBackups() {
        while (scope.isActive) {
            val schedule = settingsRepository.settings.first().backup.schedule
            val intervalMs = when (schedule) {
                BackupSchedule.Off -> null
                BackupSchedule.Daily -> DAY_MS
                BackupSchedule.Weekly -> WEEK_MS
            }
            if (intervalMs != null) {
                val lastBackupAt = latestBackupPath()
                    ?.let(fileSystem::metadataOrNull)
                    ?.lastModifiedAtMillis
                    ?: 0L
                if (currentTimeMillis() - lastBackupAt >= intervalMs) createBackup()
            }
            delay(SCHEDULER_POLL_MS)
        }
    }

    private fun latestBackupPath(): Path? = fileSystem.listOrNull(backupDirectory)
        ?.filter { path ->
            val name = path.name
            name.endsWith(".json") && (
                name.startsWith("${AppIdentifiers.BRAND_NAME}-settings-") ||
                    LegacyIds.BRAND_NAMES.any { legacyBrand ->
                        name.startsWith("$legacyBrand-settings-")
                    } ||
                    name.startsWith("settings-")
                )
        }
        ?.maxByOrNull(Path::name)

    private suspend fun uploadToWebDav(
        storageId: Long,
        remoteDirectory: String,
        fileName: String,
        content: String,
    ) {
        val editor = storageRepository.loadEditorState(storageId)
            ?: error("The selected WebDAV account no longer exists")
        val accountId = storageSourceAccountId(storageId)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
        val rootPath = storageRepository.findStorageAccountByAccountId(accountId)?.rootPath.orEmpty()
        val directory = listOf(rootPath, remoteDirectory)
            .flatMap { path -> path.split('/') }
            .filter(String::isNotBlank)
            .joinToString(separator = "/", prefix = "/")
        ctUploadWebdavBackup(
            address = editor.draft.address,
            username = credential?.username.orEmpty(),
            password = credential?.secret.orEmpty(),
            isAnonymous = credential?.isAnonymous ?: true,
            directory = directory,
            fileName = fileName,
            content = content,
        )
    }
}

@Serializable
private data class SettingsBackupEnvelope(
    val application: String = AppIdentifiers.BRAND_NAME,
    val packageId: String = AppIdentifiers.PACKAGE_ID,
    val formatVersion: Int = 1,
    val createdAtEpochMs: Long,
    val selection: SettingsBackupSelection,
    val settings: AppSettings,
)

private fun AppSettings.mergeBackup(
    backup: AppSettings,
    selection: SettingsBackupSelection,
): AppSettings {
    var merged = this
    if (selection.appearance) {
        merged = merged.copy(
            themeMode = backup.themeMode,
            artworkThemeEnabled = backup.artworkThemeEnabled,
            manualThemeSeedArgb = backup.manualThemeSeedArgb,
            customThemeSeedArgbValues = backup.customThemeSeedArgbValues,
            languageMode = backup.languageMode,
        )
    }
    if (selection.playback) {
        merged = merged.copy(
            audioFocusMode = backup.audioFocusMode,
            pauseOnDisconnect = backup.pauseOnDisconnect,
            gaplessPlaybackEnabled = backup.gaplessPlaybackEnabled,
            retryPlaybackOnFailure = backup.retryPlaybackOnFailure,
            resumePlaybackAfterNetworkRecovery = backup.resumePlaybackAfterNetworkRecovery,
            keepScreenOnInPlayer = backup.keepScreenOnInPlayer,
            playbackAdvanced = backup.playbackAdvanced,
            playerInteraction = backup.playerInteraction,
            audioEffects = backup.audioEffects,
        )
    }
    if (selection.lyrics) {
        merged = merged.copy(lyrics = backup.lyrics, lyricOutput = backup.lyricOutput)
    }
    if (selection.libraryAndMetadata) {
        merged = merged.copy(
            metadataParsing = backup.metadataParsing,
            downloadFinalization = backup.downloadFinalization,
            autoScanMode = backup.autoScanMode,
            scanSubdirectories = backup.scanSubdirectories,
            webDavMetadataScanMode = backup.webDavMetadataScanMode,
            minimumAudioDurationMs = backup.minimumAudioDurationMs,
            missingFilePolicy = backup.missingFilePolicy,
        )
    }
    if (selection.networkAndCache) {
        merged = merged.copy(
            allowMeteredNetworkUsage = backup.allowMeteredNetworkUsage,
            networkRetryCount = backup.networkRetryCount,
            connectionTimeoutSeconds = backup.connectionTimeoutSeconds,
            audioPreloadBytes = backup.audioPreloadBytes,
            listenAndCacheEnabled = backup.listenAndCacheEnabled,
            audioCacheLimitBytes = backup.audioCacheLimitBytes,
            imageCacheLimitBytes = backup.imageCacheLimitBytes,
        )
    }
    return merged.copy(backup = backup.backup)
}

private const val DAY_MS = 24L * 60L * 60L * 1_000L
private const val WEEK_MS = 7L * DAY_MS
private const val SCHEDULER_POLL_MS = 60L * 60L * 1_000L
