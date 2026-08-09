package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentState
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentSeverity
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentType
import io.github.julystar.musicapp.core.domain.model.DiagnosticFaultInjection
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogEntry
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogLevel
import io.github.julystar.musicapp.core.presentation.components.DesignCardSurface
import io.github.julystar.musicapp.core.presentation.components.DesignChip
import io.github.julystar.musicapp.core.presentation.components.DesignDialog
import io.github.julystar.musicapp.core.presentation.components.DesignPreferenceRow
import io.github.julystar.musicapp.core.presentation.components.DesignSearchBar
import io.github.julystar.musicapp.core.presentation.components.DesignSectionHeader
import io.github.julystar.musicapp.core.presentation.components.DesignSettingsGroup
import io.github.julystar.musicapp.core.presentation.components.DesignStatusBadge
import io.github.julystar.musicapp.core.presentation.components.DesignStatusTone
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import musicapp.feature.settings.generated.resources.Res
import musicapp.feature.settings.generated.resources.diagnostics_acknowledge
import musicapp.feature.settings.generated.resources.diagnostics_all
import musicapp.feature.settings.generated.resources.diagnostics_artifact
import musicapp.feature.settings.generated.resources.diagnostics_attention
import musicapp.feature.settings.generated.resources.diagnostics_card_summary
import musicapp.feature.settings.generated.resources.diagnostics_category
import musicapp.feature.settings.generated.resources.diagnostics_clear_all
import musicapp.feature.settings.generated.resources.diagnostics_clear_exports
import musicapp.feature.settings.generated.resources.diagnostics_clear_selected
import musicapp.feature.settings.generated.resources.diagnostics_confirm_delete
import musicapp.feature.settings.generated.resources.diagnostics_confirm_delete_message
import musicapp.feature.settings.generated.resources.diagnostics_copy_log
import musicapp.feature.settings.generated.resources.diagnostics_copy_path
import musicapp.feature.settings.generated.resources.diagnostics_copy_incident
import musicapp.feature.settings.generated.resources.diagnostics_current
import musicapp.feature.settings.generated.resources.diagnostics_current_attempt
import musicapp.feature.settings.generated.resources.diagnostics_delete
import musicapp.feature.settings.generated.resources.diagnostics_delete_resolved
import musicapp.feature.settings.generated.resources.diagnostics_disabled_components
import musicapp.feature.settings.generated.resources.diagnostics_detail
import musicapp.feature.settings.generated.resources.diagnostics_export
import musicapp.feature.settings.generated.resources.diagnostics_export_incident
import musicapp.feature.settings.generated.resources.diagnostics_enforce_retention
import musicapp.feature.settings.generated.resources.diagnostics_fault_injection
import musicapp.feature.settings.generated.resources.diagnostics_fault_warning
import musicapp.feature.settings.generated.resources.diagnostics_fields
import musicapp.feature.settings.generated.resources.diagnostics_history
import musicapp.feature.settings.generated.resources.diagnostics_incident_empty
import musicapp.feature.settings.generated.resources.diagnostics_incidents
import musicapp.feature.settings.generated.resources.diagnostics_incident_type
import musicapp.feature.settings.generated.resources.diagnostics_interrupted
import musicapp.feature.settings.generated.resources.diagnostics_level
import musicapp.feature.settings.generated.resources.diagnostics_load_more
import musicapp.feature.settings.generated.resources.diagnostics_log_details
import musicapp.feature.settings.generated.resources.diagnostics_log_empty
import musicapp.feature.settings.generated.resources.diagnostics_log_hint
import musicapp.feature.settings.generated.resources.diagnostics_logs
import musicapp.feature.settings.generated.resources.diagnostics_message
import musicapp.feature.settings.generated.resources.diagnostics_ready
import musicapp.feature.settings.generated.resources.diagnostics_refresh
import musicapp.feature.settings.generated.resources.diagnostics_refreshing
import musicapp.feature.settings.generated.resources.diagnostics_recovery_required
import musicapp.feature.settings.generated.resources.diagnostics_reveal
import musicapp.feature.settings.generated.resources.diagnostics_request_safe_mode
import musicapp.feature.settings.generated.resources.diagnostics_retention
import musicapp.feature.settings.generated.resources.diagnostics_sessions
import musicapp.feature.settings.generated.resources.diagnostics_selected
import musicapp.feature.settings.generated.resources.diagnostics_save_as
import musicapp.feature.settings.generated.resources.diagnostics_severity
import musicapp.feature.settings.generated.resources.diagnostics_state
import musicapp.feature.settings.generated.resources.diagnostics_startup
import musicapp.feature.settings.generated.resources.diagnostics_stable
import musicapp.feature.settings.generated.resources.diagnostics_storage
import musicapp.feature.settings.generated.resources.diagnostics_target
import musicapp.feature.settings.generated.resources.diagnostics_time_range
import musicapp.feature.settings.generated.resources.diagnostics_timestamp
import musicapp.feature.settings.generated.resources.diagnostics_title
import musicapp.feature.settings.generated.resources.diagnostics_tools
import musicapp.feature.settings.generated.resources.diagnostics_warning
import musicapp.feature.settings.generated.resources.diagnostics_correlation_id
import musicapp.feature.settings.generated.resources.settings_cancel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@Suppress("DEPRECATION")
fun DiagnosticsScreen(
    onBack: (() -> Unit)?,
    viewModel: DiagnosticsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var confirmation by remember { mutableStateOf<DiagnosticsConfirmation?>(null) }
    var selectedLog by remember { mutableStateOf<DiagnosticLogEntry?>(null) }

    SettingsPageLayout(
        title = stringResource(Res.string.diagnostics_title),
        onBack = onBack,
    ) {
        DiagnosticsOverviewCard(
            state = state,
            onRefresh = viewModel::refresh,
            onExport = viewModel::exportAndShare,
        )
        DiagnosticsActions(
            state = state,
            onSave = viewModel::saveLastExport,
            onReveal = viewModel::revealLastExport,
            onCopyPath = viewModel::copyLastExportPath,
            onClearExports = { confirmation = DiagnosticsConfirmation.ClearExports },
            onEnforceRetention = { confirmation = DiagnosticsConfirmation.EnforceRetention },
            onRequestSafeMode = viewModel::requestSafeModeNextStart,
        )
        DiagnosticsStartupSection(state)
        DiagnosticsLogSection(
            state = state,
            onKeywordChange = viewModel::setKeyword,
            onSearch = viewModel::applyKeyword,
            onSession = viewModel::selectSession,
            onLevel = viewModel::setLogLevel,
            onCategory = viewModel::setLogCategory,
            onWindow = viewModel::setLogWindow,
            onOpenLog = { selectedLog = it },
            onLoadMoreSessions = viewModel::loadMoreSessions,
            onLoadMoreLogs = viewModel::loadMoreLogs,
            onClearSelected = { confirmation = DiagnosticsConfirmation.ClearSelected },
            onClearAll = { confirmation = DiagnosticsConfirmation.ClearAll },
            onRetention = viewModel::setRetentionDays,
        )
        DiagnosticsIncidentSection(
            state = state,
            onAcknowledge = viewModel::acknowledgeIncident,
            onType = viewModel::setIncidentType,
            onSeverity = viewModel::setIncidentSeverity,
            onState = viewModel::setIncidentState,
            onCopy = { clipboard.setText(AnnotatedString(it.copySummary())) },
            onExport = viewModel::exportIncidentAndShare,
            onReadArtifact = viewModel::readArtifact,
            onDelete = { incident ->
                confirmation = DiagnosticsConfirmation.DeleteIncident(incident)
            },
            onDeleteResolved = {
                confirmation = DiagnosticsConfirmation.DeleteResolved
            },
            onLoadMore = viewModel::loadMoreIncidents,
        )
        if (state.faultInjectionSupported) {
            SettingsSection(title = stringResource(Res.string.diagnostics_fault_injection)) {
                DiagnosticFaultInjection.entries.forEach { fault ->
                    DesignPreferenceRow(
                        title = fault.name,
                        summary = stringResource(Res.string.diagnostics_fault_warning),
                        onClick = {
                            confirmation = DiagnosticsConfirmation.DebugFault(fault)
                        },
                    )
                }
            }
        }
    }

    val pendingConfirmation = confirmation
    DesignDialog(
        show = pendingConfirmation != null,
        onDismiss = { confirmation = null },
    ) {
        Text(
            text = if (pendingConfirmation is DiagnosticsConfirmation.DebugFault) {
                stringResource(Res.string.diagnostics_fault_injection)
            } else {
                stringResource(Res.string.diagnostics_confirm_delete)
            },
            style = MiuixTheme.textStyles.title3,
        )
        Text(
            text = if (pendingConfirmation is DiagnosticsConfirmation.DebugFault) {
                stringResource(Res.string.diagnostics_fault_warning)
            } else {
                stringResource(Res.string.diagnostics_confirm_delete_message)
            },
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ActionButton(
                text = stringResource(Res.string.settings_cancel),
                onClick = { confirmation = null },
            )
            ActionButton(
                text = stringResource(Res.string.diagnostics_delete),
                variant = DesignTextButtonVariant.Error,
                onClick = {
                    when (pendingConfirmation) {
                        DiagnosticsConfirmation.ClearSelected -> viewModel.clearSelectedSession()
                        DiagnosticsConfirmation.ClearAll -> viewModel.clearAllLogs()
                        DiagnosticsConfirmation.ClearExports -> viewModel.clearExports()
                        DiagnosticsConfirmation.EnforceRetention -> viewModel.enforceRetention()
                        DiagnosticsConfirmation.DeleteResolved -> viewModel.deleteResolvedIncidents()
                        is DiagnosticsConfirmation.DeleteIncident -> {
                            val incident = pendingConfirmation.incident
                            viewModel.deleteIncident(
                                incidentId = incident.id,
                                allowUnresolved = incident.state !in resolvedStates,
                            )
                        }
                        is DiagnosticsConfirmation.DebugFault ->
                            viewModel.triggerDebugFault(pendingConfirmation.fault)
                        null -> Unit
                    }
                    confirmation = null
                },
            )
        }
    }

    DesignDialog(
        show = state.artifactText != null,
        onDismiss = viewModel::clearArtifact,
    ) {
        Text(
            text = state.artifactText.orEmpty(),
            style = MiuixTheme.textStyles.body2,
        )
    }

    val logEntry = selectedLog
    DesignDialog(
        show = logEntry != null,
        onDismiss = { selectedLog = null },
    ) {
        if (logEntry != null) {
            DiagnosticLogDetail(
                entry = logEntry,
                onDismiss = { selectedLog = null },
                onCopy = {
                    clipboard.setText(AnnotatedString(logEntry.copyText()))
                    selectedLog = null
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticsOverviewCard(
    state: DiagnosticsUiState,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
) {
    val hasCriticalIncident = state.incidents.any {
        it.state !in resolvedStates &&
            it.severity in setOf(DiagnosticIncidentSeverity.Error, DiagnosticIncidentSeverity.Fatal)
    }
    val statusLabel = when {
        state.loading -> stringResource(Res.string.diagnostics_refreshing)
        state.error != null || hasCriticalIncident -> stringResource(Res.string.diagnostics_attention)
        else -> stringResource(Res.string.diagnostics_ready)
    }
    val statusTone = when {
        state.error != null -> DesignStatusTone.Error
        hasCriticalIncident -> DesignStatusTone.Warning
        state.loading -> DesignStatusTone.Info
        else -> DesignStatusTone.Success
    }

    DesignCardSurface(
        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f),
        borderColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
        contentPadding = PaddingValues(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.diagnostics_title),
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.error ?: state.status
                            ?: stringResource(Res.string.diagnostics_card_summary),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                DesignStatusBadge(label = statusLabel, tone = statusTone)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiagnosticMetric(
                    label = stringResource(Res.string.diagnostics_logs),
                    value = state.logEntries.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    label = stringResource(Res.string.diagnostics_incidents),
                    value = state.incidents.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                DiagnosticMetric(
                    label = stringResource(Res.string.diagnostics_storage),
                    value = formatBytes(state.storage?.totalBytes),
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = stringResource(Res.string.diagnostics_refresh),
                    variant = DesignTextButtonVariant.Default,
                    onClick = onRefresh,
                )
                ActionButton(
                    text = stringResource(Res.string.diagnostics_export),
                    onClick = onExport,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiagnosticsActions(
    state: DiagnosticsUiState,
    onSave: () -> Unit,
    onReveal: () -> Unit,
    onCopyPath: () -> Unit,
    onClearExports: () -> Unit,
    onEnforceRetention: () -> Unit,
    onRequestSafeMode: () -> Unit,
) {
    DesignSettingsGroup(title = stringResource(Res.string.diagnostics_tools)) {
        if (state.lastExportPath != null) {
            DesignPreferenceRow(
                title = stringResource(Res.string.diagnostics_save_as),
                summary = state.lastExportPath,
                onClick = onSave,
            )
            DesignPreferenceRow(
                title = stringResource(Res.string.diagnostics_reveal),
                onClick = onReveal,
            )
            DesignPreferenceRow(
                title = stringResource(Res.string.diagnostics_copy_path),
                onClick = onCopyPath,
                showDivider = false,
            )
        }
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_clear_exports),
            summary = state.storage?.let { formatBytes(it.exportBytes) },
            onClick = onClearExports,
        )
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_enforce_retention),
            summary = "${state.retention.retentionDays} d · " +
                "${formatBytes(state.retention.maxTotalBytes)}",
            onClick = onEnforceRetention,
            showDivider = false,
        )
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_request_safe_mode),
            onClick = onRequestSafeMode,
            showDivider = false,
        )
    }
}

@Composable
private fun DiagnosticsStartupSection(state: DiagnosticsUiState) {
    val snapshot = state.snapshot
    SettingsSection(title = stringResource(Res.string.diagnostics_startup)) {
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_current_attempt),
            summary = snapshot?.startupAttempt?.let {
                "${it.attemptId} · ${it.lastStage} · safeMode=${it.safeMode}"
            } ?: "—",
        )
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_disabled_components),
            summary = snapshot?.startupAttempt?.disabledComponents?.joinToString().orEmpty()
                .ifBlank { "—" },
        )
        DesignPreferenceRow(
            title = stringResource(Res.string.diagnostics_storage),
            summary = state.storage?.let {
                "logs=${formatBytes(it.logBytes)}, incidents=${formatBytes(it.incidentBytes)}, " +
                    "startup=${formatBytes(it.startupBytes)}, exports=${formatBytes(it.exportBytes)}"
            } ?: "—",
        )
        snapshot?.previousStartupAttempt?.let { previous ->
            DesignPreferenceRow(
                title = stringResource(Res.string.diagnostics_history),
                summary = "${previous.lastStage} · ${previous.attemptId}",
                trailing = {
                    DesignStatusBadge(
                        label = stringResource(
                            if (previous.stable) {
                                Res.string.diagnostics_stable
                            } else {
                                Res.string.diagnostics_interrupted
                            },
                        ),
                        tone = if (previous.stable) {
                            DesignStatusTone.Success
                        } else {
                            DesignStatusTone.Warning
                        },
                    )
                },
            )
        }
        state.startupHistory.take(10).forEachIndexed { index, attempt ->
            DesignPreferenceRow(
                title = "${attempt.lastStage} · ${attempt.attemptId}",
                summary = "safeMode=${attempt.safeMode} · graceful=${attempt.gracefulShutdown}",
                trailing = {
                    DesignStatusBadge(
                        label = stringResource(
                            if (attempt.stable) {
                                Res.string.diagnostics_stable
                            } else {
                                Res.string.diagnostics_interrupted
                            },
                        ),
                        tone = if (attempt.stable) {
                            DesignStatusTone.Success
                        } else {
                            DesignStatusTone.Warning
                        },
                    )
                },
                showDivider = index != state.startupHistory.take(10).lastIndex,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticsLogSection(
    state: DiagnosticsUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSession: (String) -> Unit,
    onLevel: (DiagnosticLogLevel?) -> Unit,
    onCategory: (DiagnosticLogCategory?) -> Unit,
    onWindow: (Long?) -> Unit,
    onOpenLog: (DiagnosticLogEntry) -> Unit,
    onLoadMoreSessions: () -> Unit,
    onLoadMoreLogs: () -> Unit,
    onClearSelected: () -> Unit,
    onClearAll: () -> Unit,
    onRetention: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DesignSectionHeader(
            title = stringResource(Res.string.diagnostics_logs),
            metadata = state.logEntries.size.toString(),
        )
        DesignCardSurface(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DesignSearchBar(
                    value = state.logKeyword,
                    onValueChange = onKeywordChange,
                    placeholder = stringResource(Res.string.diagnostics_logs),
                    onSearch = onSearch,
                    onClear = {
                        onKeywordChange("")
                        onSearch()
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_level),
                    options = listOf(
                        DiagnosticFilterOption(
                            label = stringResource(Res.string.diagnostics_all),
                            selected = state.logLevel == null,
                            onClick = { onLevel(null) },
                        ),
                    ) + DiagnosticLogLevel.entries.map { level ->
                        DiagnosticFilterOption(
                            label = level.name,
                            selected = state.logLevel == level,
                            onClick = { onLevel(level) },
                        )
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_category),
                    options = listOf(
                        DiagnosticFilterOption(
                            label = stringResource(Res.string.diagnostics_all),
                            selected = state.logCategory == null,
                            onClick = { onCategory(null) },
                        ),
                    ) + DiagnosticLogCategory.entries.map { category ->
                        DiagnosticFilterOption(
                            label = category.name,
                            selected = state.logCategory == category,
                            onClick = { onCategory(category) },
                        )
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_time_range),
                    options = listOf<Long?>(null, OneHourMs, OneDayMs).map { window ->
                        DiagnosticFilterOption(
                            label = when (window) {
                                null -> stringResource(Res.string.diagnostics_all)
                                OneHourMs -> "1h"
                                else -> "24h"
                            },
                            selected = state.logWindowMs == window,
                            onClick = { onWindow(window) },
                        )
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_retention),
                    options = listOf(1L, 7L, 30L).map { days ->
                        DiagnosticFilterOption(
                            label = "$days d",
                            selected = state.retention.retentionDays == days,
                            onClick = { onRetention(days) },
                        )
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = stringResource(Res.string.diagnostics_clear_selected),
                        variant = DesignTextButtonVariant.Default,
                        onClick = onClearSelected,
                    )
                    ActionButton(
                        text = stringResource(Res.string.diagnostics_clear_all),
                        variant = DesignTextButtonVariant.Error,
                        onClick = onClearAll,
                    )
                }
            }
        }
        DesignSettingsGroup(title = stringResource(Res.string.diagnostics_sessions)) {
            state.sessions.forEachIndexed { index, session ->
                val selected = state.selectedSessionId == session.sessionId
                DesignPreferenceRow(
                    title = session.sessionId,
                    titleColor = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    summary = "${session.platform} · ${session.appVersion} · " +
                        formatBytes(session.logBytes),
                    onClick = { onSession(session.sessionId) },
                    trailing = if (session.current || selected) {
                        {
                            DesignStatusBadge(
                                label = stringResource(
                                    if (session.current) {
                                        Res.string.diagnostics_current
                                    } else {
                                        Res.string.diagnostics_selected
                                    },
                                ),
                                tone = if (session.current) {
                                    DesignStatusTone.Success
                                } else {
                                    DesignStatusTone.Accent
                                },
                            )
                        }
                    } else {
                        null
                    },
                    showDivider = index != state.sessions.lastIndex || state.sessionHasMore,
                )
            }
            if (state.sessionHasMore) {
                DesignPreferenceRow(
                    title = stringResource(Res.string.diagnostics_load_more),
                    onClick = onLoadMoreSessions,
                    showDivider = false,
                )
            }
        }
        state.logWarnings.forEach { warning ->
            DiagnosticNoticeCard(warning)
        }
        if (state.logEntries.isEmpty()) {
            DiagnosticEmptyCard(stringResource(Res.string.diagnostics_log_empty))
        } else {
            state.logEntries.forEach { entry ->
                DiagnosticLogCard(entry = entry, onClick = { onOpenLog(entry) })
            }
        }
        if (state.logHasMore) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActionButton(
                    text = stringResource(Res.string.diagnostics_load_more),
                    variant = DesignTextButtonVariant.Default,
                    onClick = onLoadMoreLogs,
                )
            }
        }
        Text(
            text = stringResource(Res.string.diagnostics_log_hint),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private data class DiagnosticFilterOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun DiagnosticFilterRow(
    label: String,
    options: List<DiagnosticFilterOption>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                DesignChip(
                    label = option.label,
                    selected = option.selected,
                    onClick = option.onClick,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticLogCard(
    entry: DiagnosticLogEntry,
    onClick: () -> Unit,
) {
    DesignCardSurface(
        contentPadding = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                DesignStatusBadge(
                    label = entry.level.name.uppercase(),
                    tone = entry.level.statusTone,
                )
                Spacer(modifier = Modifier.width(8.dp))
                DesignStatusBadge(label = entry.category.name)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDiagnosticTimestamp(entry.timestampEpochMs, timeOnly = true),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = entry.message,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.target,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticNoticeCard(message: String) {
    DesignCardSurface(
        backgroundColor = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        borderColor = MiuixTheme.colorScheme.tertiaryContainer,
        elevation = 0.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignStatusBadge(
                label = stringResource(Res.string.diagnostics_warning),
                tone = DesignStatusTone.Warning,
            )
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DiagnosticEmptyCard(message: String) {
    DesignCardSurface(elevation = 0.dp) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticsIncidentSection(
    state: DiagnosticsUiState,
    onAcknowledge: (String) -> Unit,
    onType: (DiagnosticIncidentType?) -> Unit,
    onSeverity: (DiagnosticIncidentSeverity?) -> Unit,
    onState: (DiagnosticIncidentState?) -> Unit,
    onCopy: (DiagnosticIncident) -> Unit,
    onExport: (DiagnosticIncident) -> Unit,
    onReadArtifact: (String, String) -> Unit,
    onDelete: (DiagnosticIncident) -> Unit,
    onDeleteResolved: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DesignSectionHeader(
            title = stringResource(Res.string.diagnostics_incidents),
            metadata = state.incidents.size.toString(),
        )
        DesignCardSurface(contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_incident_type),
                    options = listOf(
                        DiagnosticFilterOption(
                            label = stringResource(Res.string.diagnostics_all),
                            selected = state.incidentType == null,
                            onClick = { onType(null) },
                        ),
                    ) + DiagnosticIncidentType.entries.map { type ->
                        DiagnosticFilterOption(
                            label = type.name,
                            selected = state.incidentType == type,
                            onClick = { onType(type) },
                        )
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_severity),
                    options = listOf(
                        DiagnosticFilterOption(
                            label = stringResource(Res.string.diagnostics_all),
                            selected = state.incidentSeverity == null,
                            onClick = { onSeverity(null) },
                        ),
                    ) + DiagnosticIncidentSeverity.entries.map { severity ->
                        DiagnosticFilterOption(
                            label = severity.name,
                            selected = state.incidentSeverity == severity,
                            onClick = { onSeverity(severity) },
                        )
                    },
                )
                DiagnosticFilterRow(
                    label = stringResource(Res.string.diagnostics_state),
                    options = listOf(
                        DiagnosticFilterOption(
                            label = stringResource(Res.string.diagnostics_all),
                            selected = state.incidentState == null,
                            onClick = { onState(null) },
                        ),
                    ) + DiagnosticIncidentState.entries.map { incidentState ->
                        DiagnosticFilterOption(
                            label = incidentState.name,
                            selected = state.incidentState == incidentState,
                            onClick = { onState(incidentState) },
                        )
                    },
                )
            }
        }
        if (state.incidents.isEmpty()) {
            DiagnosticEmptyCard(stringResource(Res.string.diagnostics_incident_empty))
        } else {
            state.incidents.forEach { incident ->
                DiagnosticIncidentCard(
                    incident = incident,
                    onAcknowledge = { onAcknowledge(incident.id) },
                    onCopy = { onCopy(incident) },
                    onExport = { onExport(incident) },
                    onReadArtifact = incident.artifactPaths.firstOrNull()?.let { path ->
                        { onReadArtifact(incident.id, path) }
                    },
                    onDelete = { onDelete(incident) },
                )
            }
        }
        if (state.incidentHasMore) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActionButton(
                    text = stringResource(Res.string.diagnostics_load_more),
                    variant = DesignTextButtonVariant.Default,
                    onClick = onLoadMore,
                )
            }
        }
        DesignTextButton(
            text = stringResource(Res.string.diagnostics_delete_resolved),
            modifier = Modifier.fillMaxWidth(),
            variant = DesignTextButtonVariant.Error,
            size = DesignTextButtonSize.Small,
            onClick = onDeleteResolved,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DiagnosticIncidentCard(
    incident: DiagnosticIncident,
    onAcknowledge: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onReadArtifact: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    DesignCardSurface(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                DesignStatusBadge(
                    label = incident.severity.name.uppercase(),
                    tone = incident.severity.statusTone,
                )
                Spacer(modifier = Modifier.width(8.dp))
                DesignStatusBadge(
                    label = incident.state.name,
                    tone = if (incident.state in resolvedStates) {
                        DesignStatusTone.Success
                    } else {
                        DesignStatusTone.Neutral
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "×${incident.occurrenceCount}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = incident.type.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = incident.summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDiagnosticTimestamp(incident.detectedAtEpochMs),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (incident.requiresRecovery) {
                    DesignStatusBadge(
                        label = stringResource(Res.string.diagnostics_recovery_required),
                        tone = DesignStatusTone.Warning,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = stringResource(Res.string.diagnostics_acknowledge),
                    variant = DesignTextButtonVariant.Default,
                    onClick = onAcknowledge,
                )
                ActionButton(
                    text = stringResource(Res.string.diagnostics_copy_incident),
                    variant = DesignTextButtonVariant.Default,
                    onClick = onCopy,
                )
                ActionButton(
                    text = stringResource(Res.string.diagnostics_export_incident),
                    onClick = onExport,
                )
                if (onReadArtifact != null) {
                    ActionButton(
                        text = stringResource(Res.string.diagnostics_artifact),
                        variant = DesignTextButtonVariant.Default,
                        onClick = onReadArtifact,
                    )
                }
                ActionButton(
                    text = stringResource(Res.string.diagnostics_delete),
                    variant = DesignTextButtonVariant.Error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    variant: DesignTextButtonVariant = DesignTextButtonVariant.Primary,
) {
    DesignTextButton(
        text = text,
        variant = variant,
        size = DesignTextButtonSize.Small,
        onClick = onClick,
    )
}

@Composable
private fun DiagnosticLogDetail(
    entry: DiagnosticLogEntry,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.diagnostics_log_details),
        style = MiuixTheme.textStyles.title3,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DesignStatusBadge(
            label = entry.level.name.uppercase(),
            tone = entry.level.statusTone,
        )
        DesignStatusBadge(label = entry.category.name)
    }
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DiagnosticDetailField(
                label = stringResource(Res.string.diagnostics_timestamp),
                value = formatDiagnosticTimestamp(entry.timestampEpochMs),
            )
            DiagnosticDetailField(
                label = stringResource(Res.string.diagnostics_target),
                value = entry.target,
            )
            DiagnosticDetailField(
                label = stringResource(Res.string.diagnostics_message),
                value = entry.message,
            )
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                DiagnosticDetailField(
                    label = stringResource(Res.string.diagnostics_detail),
                    value = detail,
                    monospaced = true,
                )
            }
            entry.correlationId?.takeIf { it.isNotBlank() }?.let { correlationId ->
                DiagnosticDetailField(
                    label = stringResource(Res.string.diagnostics_correlation_id),
                    value = correlationId,
                )
            }
            if (entry.fields.isNotEmpty()) {
                DiagnosticDetailField(
                    label = stringResource(Res.string.diagnostics_fields),
                    value = entry.fields.entries.joinToString("\n") { (key, value) ->
                        "$key=$value"
                    },
                    monospaced = true,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        ActionButton(
            text = stringResource(Res.string.settings_cancel),
            variant = DesignTextButtonVariant.Default,
            onClick = onDismiss,
        )
        ActionButton(
            text = stringResource(Res.string.diagnostics_copy_log),
            onClick = onCopy,
        )
    }
}

@Composable
private fun DiagnosticDetailField(
    label: String,
    value: String,
    monospaced: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.Default,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

private val DiagnosticLogLevel.statusTone: DesignStatusTone
    get() = when (this) {
        DiagnosticLogLevel.Trace,
        DiagnosticLogLevel.Debug,
        -> DesignStatusTone.Neutral
        DiagnosticLogLevel.Info -> DesignStatusTone.Info
        DiagnosticLogLevel.Warn -> DesignStatusTone.Warning
        DiagnosticLogLevel.Error,
        DiagnosticLogLevel.Fatal,
        -> DesignStatusTone.Error
    }

private val DiagnosticIncidentSeverity.statusTone: DesignStatusTone
    get() = when (this) {
        DiagnosticIncidentSeverity.Info -> DesignStatusTone.Info
        DiagnosticIncidentSeverity.Warning -> DesignStatusTone.Warning
        DiagnosticIncidentSeverity.Error,
        DiagnosticIncidentSeverity.Fatal,
        -> DesignStatusTone.Error
    }

private fun formatDiagnosticTimestamp(epochMs: Long, timeOnly: Boolean = false): String =
    runCatching {
        val value = Instant.fromEpochMilliseconds(epochMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val time = "${value.hour.twoDigits()}:${value.minute.twoDigits()}:" +
            value.second.twoDigits()
        if (timeOnly) {
            time
        } else {
            "${value.year}-${(value.month.ordinal + 1).twoDigits()}-${value.day.twoDigits()} $time"
        }
    }.getOrDefault(epochMs.toString())

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private fun DiagnosticLogEntry.copyText(): String = buildString {
    appendLine("timestamp=$timestampEpochMs")
    appendLine("level=$level category=$category")
    appendLine("target=$target")
    appendLine("message=$message")
    detail?.let { appendLine("detail=$it") }
    correlationId?.let { appendLine("correlationId=$it") }
    append("fields=$fields")
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

private sealed interface DiagnosticsConfirmation {
    data object ClearSelected : DiagnosticsConfirmation
    data object ClearAll : DiagnosticsConfirmation
    data object ClearExports : DiagnosticsConfirmation
    data object EnforceRetention : DiagnosticsConfirmation
    data object DeleteResolved : DiagnosticsConfirmation
    data class DeleteIncident(val incident: DiagnosticIncident) : DiagnosticsConfirmation
    data class DebugFault(val fault: DiagnosticFaultInjection) : DiagnosticsConfirmation
}

private val resolvedStates = setOf(
    DiagnosticIncidentState.Resolved,
    DiagnosticIncidentState.Ignored,
)

private const val OneHourMs = 60L * 60 * 1_000
private const val OneDayMs = 24L * OneHourMs
