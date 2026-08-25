package io.github.julystar.musicapp.diagnostics

import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentFilter
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentState
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogLevel
import io.github.julystar.musicapp.core.domain.model.DiagnosticRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupStage
import io.github.julystar.musicapp.core.domain.recovery.IncidentOccurrence
import io.github.julystar.musicapp.core.domain.recovery.RECOVERY_USER_ATTENTION_STATES
import io.github.julystar.musicapp.core.domain.recovery.SafeModePolicyInput
import io.github.julystar.musicapp.core.domain.recovery.StartupMode
import io.github.julystar.musicapp.core.domain.recovery.StartupPlan
import io.github.julystar.musicapp.core.domain.recovery.StartupRecoveryPlanner
import io.github.julystar.musicapp.core.domain.recovery.isRelevantToStartupSafety
import io.github.julystar.musicapp.core.domain.recovery.recoveryAttentionReason
import io.github.julystar.musicapp.core.domain.recovery.requiresUserAttention
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.platform.getAppBuildInfo
import io.github.julystar.musicapp.platform.getAppCacheDir
import io.github.julystar.musicapp.platform.getAppDataDirectory
import io.github.julystar.musicapp.platform.getAppGitCommitSha
import io.github.julystar.musicapp.platform.getAppVersion
import io.github.julystar.musicapp.platform.getPlatformName
import io.github.julystar.musicapp.platform.getProcessName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.app_backend.DiagnosticsRuntimeInit
import uniffi.app_backend.initializeDiagnosticsRuntime

data class DiagnosticsBootstrapState(
    val snapshot: DiagnosticRuntimeSnapshot,
    val startupPlan: StartupPlan,
    val pendingIncidents: List<DiagnosticIncident>,
) {
    val safeMode: Boolean
        get() = startupPlan.mode == StartupMode.SafeMode

    fun recoveryIncidentIds(): List<String> = pendingIncidents
        .filter { incident ->
            incident.requiresRecovery || incident.id == startupPlan.primaryIncidentId
        }
        .map { it.id }

    fun beginAutomaticDegradedRecovery(): List<String> {
        if (safeMode || startupPlan.disabledComponents.isEmpty()) return emptyList()
        RustDiagnosticsRepository.beginRecovery(startupPlan.disabledComponents)
        return recoveryIncidentIds().onEach { incidentId ->
            RustDiagnosticsRepository.markRecoveryAttempted(
                incidentId,
                startupPlan.disabledComponents,
            )
        }
    }

    suspend fun consumeRecoveryAttention(): String? = withContext(Dispatchers.Default) {
        recoveryAttentionConsumptionMutex.withLock {
            val incident = RustDiagnosticsRepository.listIncidents(
                DiagnosticIncidentFilter(
                    states = RECOVERY_USER_ATTENTION_STATES,
                    requiresRecovery = true,
                    limit = 500,
                )
            ).incidents.maxByOrNull(DiagnosticIncident::lastSeenAtEpochMs)
                ?: return@withLock null
            if (!incident.requiresUserAttention()) return@withLock null

            val acknowledged = RustDiagnosticsRepository.setIncidentState(
                incident.id,
                DiagnosticIncidentState.Acknowledged,
            )
            RustDiagnosticsRepository.log(
                level = DiagnosticLogLevel.Info,
                category = DiagnosticLogCategory.Startup,
                target = "RecoveryAttention",
                message = "Recovery attention consumed",
                fields = recoveryAttentionFields(acknowledged),
            )
            incident.id
        }
    }
}

/**
 * Process-global startup state. Platform entry points call this before creating
 * the full Koin graph, database, backend, player, workers, or Compose content.
 */
object DiagnosticsBootstrap {
    private val planner = StartupRecoveryPlanner()
    private var current: DiagnosticsBootstrapState? = null
    private var userForcedSafeMode: Boolean = false
    private var lastAttentionEvaluation: String? = null

    val state: DiagnosticsBootstrapState
        get() = checkNotNull(current) { "DiagnosticsBootstrap has not been initialized" }

    fun initialize(
        userForcedSafeMode: Boolean = false,
        lastUserRequestedExitAtEpochMs: Long? = null,
    ): DiagnosticsBootstrapState {
        current?.let { return it }
        this.userForcedSafeMode = userForcedSafeMode
        initializeDiagnosticsRuntime(
            DiagnosticsRuntimeInit(
                appDocumentDir = getAppDataDirectory(),
                appCacheDir = getAppCacheDir(),
                platform = getPlatformName(),
                appVersion = getAppVersion(),
                buildInfo = getAppBuildInfo(),
                gitCommitSha = getAppGitCommitSha(),
                processName = getProcessName(),
                userForcedSafeMode = userForcedSafeMode,
                lastUserRequestedExitAtEpochMs = lastUserRequestedExitAtEpochMs,
            )
        )
        return replan()
    }

    /**
     * Called after platform-specific historical exits are imported, before full DI.
     */
    fun finishPlatformExitCollection(): DiagnosticsBootstrapState {
        RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.PlatformExitsCollected)
        return replan()
    }

    private fun replan(): DiagnosticsBootstrapState {
        val snapshot = RustDiagnosticsRepository.snapshot()
        val incidents = RustDiagnosticsRepository.listIncidents(
            DiagnosticIncidentFilter(
                states = startupCandidateStates,
                limit = 500,
            )
        ).incidents
        val pending = snapshot.pendingRecovery
        val pendingSafetyIncident = pending?.incidentId?.let { incidentId ->
            incidents.firstOrNull { incident -> incident.id == incidentId }
                ?.takeIf(DiagnosticIncident::isRelevantToStartupSafety)
        }
        val previous = snapshot.previousStartupAttempt
        val plan = planner.plan(
            SafeModePolicyInput(
                pendingIncidents = incidents,
                lastStartupAttempt = previous,
                occurrences = incidents.flatMap { incident ->
                    val fingerprint = incident.fingerprint ?: return@flatMap emptyList()
                    val timestamps = incident.occurrenceTimestampsEpochMs.ifEmpty {
                        listOf(incident.lastSeenAtEpochMs)
                    }
                    timestamps.map { timestamp ->
                        IncidentOccurrence(
                            fingerprint = fingerprint,
                            incidentType = incident.type,
                            severity = incident.severity,
                            timestampEpochMs = timestamp,
                            startupStage = incident.startupStage,
                        )
                    }
                },
                currentTimeEpochMs = currentTimeMillis(),
                userForcedSafeMode = userForcedSafeMode ||
                    snapshot.startupAttempt.safeMode ||
                    snapshot.safeModeSuggested ||
                    snapshot.startupAttempt.safeModeReason == "User requested safe mode",
                previousRecoveryFailedAtSameStage =
                    pendingSafetyIncident != null &&
                        (
                            pending.failedRecoveryAttempts > 0 ||
                                (
                                    previous?.recoveryAttempted == true &&
                                        !previous.stable &&
                                        pending.startupStage == previous.lastStage
                                    )
                            ),
            )
        )
        RustDiagnosticsRepository.setStartupMode(
            safeMode = plan.mode == StartupMode.SafeMode,
            reason = plan.reason.takeIf { plan.mode == StartupMode.SafeMode },
            disabledComponents = plan.disabledComponents,
        )
        incidents.maxByOrNull(DiagnosticIncident::lastSeenAtEpochMs)?.let(::logAttentionEvaluation)
        return DiagnosticsBootstrapState(
            snapshot = RustDiagnosticsRepository.snapshot(),
            startupPlan = plan,
            pendingIncidents = incidents,
        ).also { current = it }
    }

    private val startupCandidateStates = setOf(
        DiagnosticIncidentState.Detected,
        DiagnosticIncidentState.PendingReview,
        DiagnosticIncidentState.Acknowledged,
        DiagnosticIncidentState.Exported,
        DiagnosticIncidentState.RecoveryAttempted,
    )

    private fun logAttentionEvaluation(incident: DiagnosticIncident) {
        val reason = incident.recoveryAttentionReason()
        val signature = "${incident.id}|${incident.state}|${incident.requiresRecovery}|$reason"
        if (signature == lastAttentionEvaluation) return
        lastAttentionEvaluation = signature
        RustDiagnosticsRepository.log(
            level = DiagnosticLogLevel.Info,
            category = DiagnosticLogCategory.Startup,
            target = "RecoveryAttention",
            message = "Recovery attention evaluated",
            fields = recoveryAttentionFields(incident),
        )
    }
}

private fun recoveryAttentionFields(incident: DiagnosticIncident): Map<String, String> = mapOf(
    "incidentId" to incident.id,
    "incidentState" to incident.state.name,
    "requiresRecovery" to incident.requiresRecovery.toString(),
    "acknowledged" to (incident.state !in RECOVERY_USER_ATTENTION_STATES).toString(),
    "shouldNotify" to incident.requiresUserAttention().toString(),
    "reason" to incident.recoveryAttentionReason().logValue,
)

private val recoveryAttentionConsumptionMutex = Mutex()
