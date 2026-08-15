package io.github.julystar.musicapp.core.domain.recovery

import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentState

enum class RecoveryAttentionReason(val logValue: String) {
    ActionRequired("action_required"),
    RecoveryNotRequired("recovery_not_required"),
    AlreadyAcknowledged("already_acknowledged"),
    IncidentExported("incident_exported"),
    RecoveryAttempted("recovery_attempted"),
    IncidentResolved("incident_resolved"),
    IncidentIgnored("incident_ignored"),
}

fun DiagnosticIncident.requiresUserAttention(): Boolean =
    requiresRecovery && state in RECOVERY_USER_ATTENTION_STATES

fun DiagnosticIncident.recoveryAttentionReason(): RecoveryAttentionReason = when {
    state == DiagnosticIncidentState.Acknowledged -> RecoveryAttentionReason.AlreadyAcknowledged
    state == DiagnosticIncidentState.Exported -> RecoveryAttentionReason.IncidentExported
    state == DiagnosticIncidentState.RecoveryAttempted -> RecoveryAttentionReason.RecoveryAttempted
    state == DiagnosticIncidentState.Resolved -> RecoveryAttentionReason.IncidentResolved
    state == DiagnosticIncidentState.Ignored -> RecoveryAttentionReason.IncidentIgnored
    !requiresRecovery -> RecoveryAttentionReason.RecoveryNotRequired
    else -> RecoveryAttentionReason.ActionRequired
}

/**
 * Historical incidents remain visible in Diagnostics but do not participate in startup planning.
 * Exporting consumes this incident as startup evidence without clearing a separate recovery need.
 */
fun DiagnosticIncident.isRelevantToStartupSafety(): Boolean = when (state) {
    DiagnosticIncidentState.Detected,
    DiagnosticIncidentState.PendingReview,
    -> true
    DiagnosticIncidentState.Acknowledged,
    DiagnosticIncidentState.RecoveryAttempted,
    -> requiresRecovery
    DiagnosticIncidentState.Exported,
    DiagnosticIncidentState.Resolved,
    DiagnosticIncidentState.Ignored,
    -> false
}

val RECOVERY_USER_ATTENTION_STATES = setOf(
    DiagnosticIncidentState.Detected,
    DiagnosticIncidentState.PendingReview,
)
