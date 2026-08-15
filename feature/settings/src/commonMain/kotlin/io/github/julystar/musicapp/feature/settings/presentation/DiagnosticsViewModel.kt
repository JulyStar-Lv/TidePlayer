package io.github.julystar.musicapp.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentFilter
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentState
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentSeverity
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentType
import io.github.julystar.musicapp.core.domain.model.DiagnosticExportBundleRequest
import io.github.julystar.musicapp.core.domain.model.DiagnosticFaultInjection
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogEntry
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogFilter
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogLevel
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogRetentionPolicy
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogSession
import io.github.julystar.musicapp.core.domain.model.DiagnosticRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupAttempt
import io.github.julystar.musicapp.core.domain.model.DiagnosticStorageBreakdown
import io.github.julystar.musicapp.core.domain.model.DiagnosticsExportResult
import io.github.julystar.musicapp.core.domain.repository.DiagnosticExportPresenter
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsRepository
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsService
import io.github.julystar.musicapp.core.domain.recovery.requiresUserAttention
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val status: String? = null,
    val snapshot: DiagnosticRuntimeSnapshot? = null,
    val storage: DiagnosticStorageBreakdown? = null,
    val retention: DiagnosticLogRetentionPolicy = DiagnosticLogRetentionPolicy(),
    val sessions: List<DiagnosticLogSession> = emptyList(),
    val sessionHasMore: Boolean = false,
    val selectedSessionId: String? = null,
    val logEntries: List<DiagnosticLogEntry> = emptyList(),
    val logHasMore: Boolean = false,
    val logWarnings: List<String> = emptyList(),
    val logKeyword: String = "",
    val logLevel: DiagnosticLogLevel? = null,
    val logCategory: DiagnosticLogCategory? = null,
    val logWindowMs: Long? = null,
    val incidents: List<DiagnosticIncident> = emptyList(),
    val incidentHasMore: Boolean = false,
    val incidentType: DiagnosticIncidentType? = null,
    val incidentSeverity: DiagnosticIncidentSeverity? = null,
    val incidentState: DiagnosticIncidentState? = null,
    val startupHistory: List<DiagnosticStartupAttempt> = emptyList(),
    val artifactText: String? = null,
    val faultInjectionSupported: Boolean = false,
    val lastExportPath: String? = null,
)

class DiagnosticsViewModel(
    private val repository: DiagnosticsRepository,
    private val diagnosticsService: DiagnosticsService,
    private val exportPresenter: DiagnosticExportPresenter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.Default) {
                    val sessions = repository.listLogSessions(0, PageSize)
                    val incidents = repository.listIncidents(
                        mutableState.value.incidentFilter(offset = 0),
                    )
                    val selected = mutableState.value.selectedSessionId
                        ?: sessions.sessions.firstOrNull()?.sessionId
                    val logs = readLogs(selected, offset = 0)
                    DiagnosticsUiState(
                        loading = false,
                        snapshot = repository.snapshot(),
                        storage = repository.getStorageUsage(),
                        retention = repository.getLogRetentionPolicy(),
                        sessions = sessions.sessions,
                        sessionHasMore = sessions.hasMore,
                        selectedSessionId = selected,
                        logEntries = logs.entries,
                        logHasMore = logs.hasMore,
                        logWarnings = logs.warnings,
                        logKeyword = mutableState.value.logKeyword,
                        logLevel = mutableState.value.logLevel,
                        logCategory = mutableState.value.logCategory,
                        logWindowMs = mutableState.value.logWindowMs,
                        incidents = incidents.incidents,
                        incidentHasMore = incidents.hasMore,
                        incidentType = mutableState.value.incidentType,
                        incidentSeverity = mutableState.value.incidentSeverity,
                        incidentState = mutableState.value.incidentState,
                        startupHistory = repository.startupHistory(20),
                        faultInjectionSupported = repository.debugFaultInjectionSupported(),
                        lastExportPath = mutableState.value.lastExportPath,
                    )
                }
            }.onSuccess { loaded ->
                mutableState.value = loaded
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = error.message ?: "Diagnostics could not be loaded",
                )
            }
        }
    }

    fun selectSession(sessionId: String) {
        mutableState.value = mutableState.value.copy(selectedSessionId = sessionId)
        reloadLogs()
    }

    fun setKeyword(keyword: String) {
        mutableState.value = mutableState.value.copy(logKeyword = keyword)
    }

    fun applyKeyword() = reloadLogs()

    fun setLogLevel(level: DiagnosticLogLevel?) {
        mutableState.value = mutableState.value.copy(logLevel = level)
        reloadLogs()
    }

    fun setLogCategory(category: DiagnosticLogCategory?) {
        mutableState.value = mutableState.value.copy(logCategory = category)
        reloadLogs()
    }

    fun setLogWindow(windowMs: Long?) {
        mutableState.value = mutableState.value.copy(logWindowMs = windowMs)
        reloadLogs()
    }

    fun setIncidentType(type: DiagnosticIncidentType?) {
        mutableState.value = mutableState.value.copy(incidentType = type)
        reloadIncidents()
    }

    fun setIncidentSeverity(severity: DiagnosticIncidentSeverity?) {
        mutableState.value = mutableState.value.copy(incidentSeverity = severity)
        reloadIncidents()
    }

    fun setIncidentState(state: DiagnosticIncidentState?) {
        mutableState.value = mutableState.value.copy(incidentState = state)
        reloadIncidents()
    }

    fun loadMoreSessions() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.listLogSessions(mutableState.value.sessions.size.toLong(), PageSize)
                }
            }.onSuccess { page ->
                mutableState.value = mutableState.value.copy(
                    sessions = mutableState.value.sessions + page.sessions,
                    sessionHasMore = page.hasMore,
                )
            }
        }
    }

    fun loadMoreLogs() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    readLogs(
                        sessionId = mutableState.value.selectedSessionId,
                        offset = mutableState.value.logEntries.size.toLong(),
                    )
                }
            }.onSuccess { page ->
                mutableState.value = mutableState.value.copy(
                    logEntries = mutableState.value.logEntries + page.entries,
                    logHasMore = page.hasMore,
                    logWarnings = (mutableState.value.logWarnings + page.warnings).distinct(),
                )
            }
        }
    }

    fun loadMoreIncidents() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.listIncidents(
                        mutableState.value.incidentFilter(
                            offset = mutableState.value.incidents.size.toLong(),
                        )
                    )
                }
            }.onSuccess { page ->
                mutableState.value = mutableState.value.copy(
                    incidents = mutableState.value.incidents + page.incidents,
                    incidentHasMore = page.hasMore,
                )
            }
        }
    }

    fun clearSelectedSession() {
        val sessionId = mutableState.value.selectedSessionId ?: return
        launchMutation {
            repository.clearLogSessions(listOf(sessionId))
        }
    }

    fun clearAllLogs() = launchMutation { repository.clearAllLogs() }

    fun clearExports() = launchMutation { repository.clearExports() }

    fun enforceRetention() = launchMutation { repository.enforceLogRetentionPolicy() }

    fun requestSafeModeNextStart() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.requestSafeModeNextStart()
                }
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    status = "Safe mode will be used on the next launch",
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(error = error.message)
            }
        }
    }

    fun setRetentionDays(days: Long) = launchMutation {
        repository.setLogRetentionPolicy(mutableState.value.retention.copy(retentionDays = days))
    }

    fun acknowledgeIncident(incidentId: String) = launchMutation {
        repository.setIncidentState(incidentId, DiagnosticIncidentState.Acknowledged)
    }

    fun deleteIncident(incidentId: String, allowUnresolved: Boolean) = launchMutation {
        repository.deleteIncident(incidentId, allowUnresolved)
    }

    fun deleteResolvedIncidents() = launchMutation { repository.deleteResolvedIncidents() }

    fun readArtifact(incidentId: String, artifactPath: String) {
        viewModelScope.launch {
            val shouldAcknowledge = mutableState.value.incidents
                .firstOrNull { it.id == incidentId }
                ?.requiresUserAttention() == true
            runCatching {
                withContext(Dispatchers.Default) {
                    if (shouldAcknowledge) {
                        repository.setIncidentState(
                            incidentId,
                            DiagnosticIncidentState.Acknowledged,
                        )
                    }
                    repository.readIncidentArtifact(incidentId, artifactPath)
                }
            }.onSuccess { text ->
                mutableState.value = mutableState.value.copy(
                    artifactText = text,
                    incidents = if (shouldAcknowledge) {
                        mutableState.value.incidents.map { incident ->
                            if (incident.id == incidentId) {
                                incident.copy(state = DiagnosticIncidentState.Acknowledged)
                            } else {
                                incident
                            }
                        }
                    } else {
                        mutableState.value.incidents
                    },
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(error = error.message)
            }
        }
    }

    fun clearArtifact() {
        mutableState.value = mutableState.value.copy(artifactText = null)
    }

    fun exportAndShare() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(status = "Exporting diagnostics…")
            when (
                val result = withContext(Dispatchers.Default) {
                    diagnosticsService.exportDiagnostics()
                }
            ) {
                is DiagnosticsExportResult.Failure -> {
                    mutableState.value = mutableState.value.reduceExportEvent(
                        DiagnosticsExportEvent.Failed(result.message),
                    )
                }
                is DiagnosticsExportResult.Success -> {
                    val shareResult = exportPresenter.share(result.path)
                    repository.releaseExport(result.path)
                    mutableState.value = mutableState.value.reduceExportEvent(
                        DiagnosticsExportEvent.Completed(
                            path = result.path,
                            presentationError = shareResult.exceptionOrNull()?.let {
                                it.message ?: "Unable to share diagnostics"
                            },
                        ),
                    )
                }
            }
        }
    }

    fun exportIncidentAndShare(incident: DiagnosticIncident) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(status = "Exporting incident…")
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.export(
                        DiagnosticExportBundleRequest(
                            summary = incident.copySummary(),
                            environmentJson = "{}",
                            playbackSummaryJson = "{}",
                            scanSummaryJson = "{}",
                            pluginSummaryJson = "{}",
                            sourceSummaryJson = "{}",
                            storageSummaryJson = "{}",
                            includeResolvedIncidents = true,
                            incidentIds = setOf(incident.id),
                        )
                    )
                }
            }.onSuccess { bundle ->
                val result = try {
                    exportPresenter.share(bundle.path)
                } finally {
                    repository.releaseExport(bundle.path)
                }
                mutableState.value = mutableState.value.reduceExportEvent(
                    DiagnosticsExportEvent.Completed(
                        path = bundle.path,
                        presentationError = result.exceptionOrNull()?.let {
                            it.message ?: "Unable to share incident"
                        },
                        successMessage = "Incident bundle is ready",
                    ),
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.reduceExportEvent(
                    DiagnosticsExportEvent.Failed(
                        error.message ?: "Unable to export incident",
                    ),
                )
            }
        }
    }

    fun saveLastExport() = presentLastExport("Saving diagnostics…", exportPresenter::saveAs)

    fun revealLastExport() = presentLastExport("Opening diagnostics…", exportPresenter::reveal)

    fun copyLastExportPath() = presentLastExport(
        "Copying diagnostics path…",
        exportPresenter::copyPath,
    )

    fun triggerDebugFault(fault: DiagnosticFaultInjection) {
        mutableState.value = mutableState.value.copy(status = "Triggering ${fault.name}…")
        repository.triggerDebugFault(fault)
    }

    private fun reloadLogs() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    readLogs(mutableState.value.selectedSessionId, offset = 0)
                }
            }.onSuccess { page ->
                mutableState.value = mutableState.value.copy(
                    logEntries = page.entries,
                    logHasMore = page.hasMore,
                    logWarnings = page.warnings,
                )
            }
        }
    }

    private fun reloadIncidents() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.listIncidents(mutableState.value.incidentFilter(offset = 0))
                }
            }.onSuccess { page ->
                mutableState.value = mutableState.value.copy(
                    incidents = page.incidents,
                    incidentHasMore = page.hasMore,
                )
            }
        }
    }

    private fun readLogs(sessionId: String?, offset: Long) = repository.readLogEntries(
        mutableState.value.logFilter(
            sessionId = sessionId,
            offset = offset,
            nowEpochMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
    )

    private fun launchMutation(block: () -> Any?) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { block() } }
                .onSuccess { refresh() }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(error = error.message)
                }
        }
    }

    private fun presentLastExport(
        progress: String,
        action: suspend (String) -> Result<Unit>,
    ) {
        val path = mutableState.value.lastExportPath ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(status = progress)
            val result = action(path)
            mutableState.value = mutableState.value.copy(
                status = result.fold(
                    onSuccess = { "Diagnostics file action completed" },
                    onFailure = { it.message ?: "Diagnostics file action failed" },
                )
            )
        }
    }

    private companion object {
        const val PageSize = 100L
    }
}

internal fun DiagnosticsUiState.logFilter(
    sessionId: String?,
    offset: Long,
    nowEpochMs: Long,
): DiagnosticLogFilter = DiagnosticLogFilter(
    sessionIds = sessionId?.let(::setOf).orEmpty(),
    levels = logLevel?.let(::setOf).orEmpty(),
    categories = logCategory?.let(::setOf).orEmpty(),
    keyword = logKeyword.trim().ifBlank { null },
    startEpochMs = logWindowMs?.let { nowEpochMs - it },
    offset = offset,
    limit = 100,
)

internal fun DiagnosticsUiState.incidentFilter(offset: Long): DiagnosticIncidentFilter =
    DiagnosticIncidentFilter(
        types = incidentType?.let(::setOf).orEmpty(),
        severities = incidentSeverity?.let(::setOf).orEmpty(),
        states = incidentState?.let(::setOf).orEmpty(),
        offset = offset,
        limit = 100,
    )

internal sealed interface DiagnosticsExportEvent {
    data class Completed(
        val path: String,
        val presentationError: String?,
        val successMessage: String = "Diagnostics bundle is ready",
    ) : DiagnosticsExportEvent

    data class Failed(val message: String) : DiagnosticsExportEvent
}

internal fun DiagnosticsUiState.reduceExportEvent(
    event: DiagnosticsExportEvent,
): DiagnosticsUiState = when (event) {
    is DiagnosticsExportEvent.Completed -> copy(
        lastExportPath = event.path,
        status = event.presentationError ?: event.successMessage,
    )
    is DiagnosticsExportEvent.Failed -> copy(status = event.message)
}

private fun DiagnosticIncident.copySummary(): String = buildString {
    appendLine("incidentId=$id")
    appendLine("type=$type severity=$severity state=$state")
    appendLine("detectedAtEpochMs=$detectedAtEpochMs")
    appendLine("startupStage=${startupStage ?: "UNKNOWN"}")
    appendLine("occurrenceCount=$occurrenceCount")
    appendLine("summary=$summary")
    detail?.let { append("detail=$it") }
}
