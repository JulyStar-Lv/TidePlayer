package io.github.julystar.musicapp.core.data.settings

import io.github.julystar.musicapp.core.domain.model.DiagnosticsExportResult
import io.github.julystar.musicapp.core.domain.model.DiagnosticsReport
import io.github.julystar.musicapp.core.domain.model.DiagnosticExportBundleRequest
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsRepository
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsService
import io.github.julystar.musicapp.core.domain.repository.StorageUsageRepository
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceErrorDao
import io.github.julystar.musicapp.database.APP_DATABASE_VERSION
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.diagnostics.SafeModeRecoveryStore
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.platform.getAppBuildInfo
import io.github.julystar.musicapp.platform.getAppGitCommitSha
import io.github.julystar.musicapp.platform.getAppVersion
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

class FileDiagnosticsService(
    private val sourceAccountDao: SourceAccountDao,
    private val sourceErrorDao: SourceErrorDao,
    private val trackDao: TrackDao,
    private val librarySyncController: LibrarySyncController,
    private val playbackController: PlaybackController,
    private val storageUsageRepository: StorageUsageRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
) : DiagnosticsService {

    override suspend fun collectDiagnostics(): DiagnosticsReport {
        val recentTasks = librarySyncController.recentTasks.first()
        val recentErrors = sourceErrorDao.listRecent(DIAGNOSTIC_ERROR_LIMIT)
            .map { error -> error.message.redactSensitiveData() }
        val playerState = playbackController.state.value
        return DiagnosticsReport(
            generatedAtEpochMs = currentTimeMillis(),
            appVersion = getAppVersion(),
            buildInfo = getAppBuildInfo(),
            gitCommitSha = getAppGitCommitSha(),
            platformInfo = getAppBuildInfo(),
            databaseVersion = APP_DATABASE_VERSION,
            sourceCount = sourceAccountDao.listAll().size,
            trackCount = trackDao.count(),
            recentScanSummary = recentTasks.firstOrNull()?.let { task ->
                "${task.status}: total=${task.scannedCount}, imported=${task.importedCount}, failed=${task.failedCount}"
            },
            playerStateSummary = "${playerState.status}, queue=${playbackController.queue.value.items.size}",
            storageUsage = storageUsageRepository.loadUsage(),
            recentErrors = recentErrors,
        )
    }

    override suspend fun exportDiagnostics(): DiagnosticsExportResult {
        return runCatching {
            val report = collectDiagnostics()
            val databaseCheck = Json.parseToJsonElement(
                SafeModeRecoveryStore.databaseCheckSummaryJson(),
            )
            val result = diagnosticsRepository.export(
                DiagnosticExportBundleRequest(
                    summary = report.toText(),
                    environmentJson = buildJsonObject {
                        put("generatedAtEpochMs", report.generatedAtEpochMs)
                        put("appVersion", report.appVersion)
                        put("buildInfo", report.buildInfo.redactSensitiveData())
                        put("gitCommitSha", report.gitCommitSha)
                        put("platform", report.platformInfo.redactSensitiveData())
                        put("databaseVersion", report.databaseVersion)
                        put("databaseCheck", databaseCheck)
                    }.toString(),
                    playbackSummaryJson = buildJsonObject {
                        put("summary", report.playerStateSummary.redactSensitiveData())
                    }.toString(),
                    scanSummaryJson = buildJsonObject {
                        put("summary", report.recentScanSummary?.redactSensitiveData() ?: "none")
                        put("recentErrorCount", report.recentErrors.size)
                    }.toString(),
                    pluginSummaryJson = buildJsonObject {
                        put("included", false)
                        put("reason", "Plugin configuration and secrets are excluded")
                    }.toString(),
                    sourceSummaryJson = buildJsonObject {
                        put("sourceCount", report.sourceCount)
                        put("trackCount", report.trackCount)
                    }.toString(),
                    storageSummaryJson = buildJsonObject {
                        put("audioCacheBytes", report.storageUsage.audioBytes ?: -1)
                        put("imageCacheBytes", report.storageUsage.imageBytes ?: -1)
                        put("downloadBytes", report.storageUsage.downloadBytes ?: -1)
                        put("databaseBytes", report.storageUsage.databaseBytes ?: -1)
                        put("diagnosticBytes", report.storageUsage.logBytes ?: -1)
                        put("totalBytes", report.storageUsage.totalBytes ?: -1)
                    }.toString(),
                )
            )
            DiagnosticsExportResult.Success(result.path)
        }.getOrElse { error ->
            DiagnosticsExportResult.Failure(error.message ?: "Unknown diagnostics export error")
        }
    }
}

private fun DiagnosticsReport.toText(): String = buildString {
    appendLine("Tide Player diagnostics")
    appendLine("generatedAtEpochMs=$generatedAtEpochMs")
    appendLine("appVersion=$appVersion")
    appendLine("buildInfo=${buildInfo.redactSensitiveData()}")
    appendLine("gitCommitSha=$gitCommitSha")
    appendLine("platform=${platformInfo.redactSensitiveData()}")
    appendLine("databaseVersion=$databaseVersion")
    appendLine("sourceCount=$sourceCount")
    appendLine("trackCount=$trackCount")
    appendLine("recentScan=${recentScanSummary?.redactSensitiveData() ?: "none"}")
    appendLine("player=$playerStateSummary")
    appendLine("audioCacheBytes=${storageUsage.audioBytes ?: -1}")
    appendLine("imageCacheBytes=${storageUsage.imageBytes ?: -1}")
    appendLine("downloadBytes=${storageUsage.downloadBytes ?: -1}")
    appendLine("databaseBytes=${storageUsage.databaseBytes ?: -1}")
    appendLine("logBytes=${storageUsage.logBytes ?: -1}")
    appendLine("totalBytes=${storageUsage.totalBytes ?: -1}")
    appendLine("recentErrors:")
    if (recentErrors.isEmpty()) appendLine("- none")
    else recentErrors.forEach { error -> appendLine("- ${error.redactSensitiveData()}") }
}

internal fun String.redactSensitiveData(): String {
    return replace(URL_CREDENTIAL_REGEX, "$1***:***@")
        .replace(SENSITIVE_QUERY_REGEX, "$1=***")
        .replace(AUTHORIZATION_REGEX, "$1 ***")
        .replace(COOKIE_REGEX, "$1: ***")
        .replace(URL_QUERY_REGEX, "$1?<REDACTED_QUERY>")
}

private val URL_CREDENTIAL_REGEX = Regex("(https?://)[^/@\\s:]+:[^/@\\s]+@", RegexOption.IGNORE_CASE)
private val SENSITIVE_QUERY_REGEX = Regex(
    "(?i)(token|access_token|refresh_token|password|passwd|secret|api_key|apikey|code|" +
        "webdav[_-]?password|smb[_-]?password|plugin[_-]?(?:config[_-]?)?secret)" +
        "\\s*[:=]\\s*([^&\\s]+)"
)
private val AUTHORIZATION_REGEX = Regex(
    "(?i)(authorization\\s*:\\s*(?:bearer|basic)?|bearer|basic)\\s+[^\\s,]+"
)
private val COOKIE_REGEX = Regex("(?i)(set-cookie|cookie)\\s*:\\s*[^\\r\\n]+")
private val URL_QUERY_REGEX = Regex("(?i)(https?://[^\\s?#]+)\\?[^\\s#]+")
private const val DIAGNOSTIC_ERROR_LIMIT = 20
