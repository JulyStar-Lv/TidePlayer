package io.github.julystar.musicapp.core.domain.recovery

import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentSeverity
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentState
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentType
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncidentRecoverySemanticsTest {
    @Test
    fun exportedIncidentWithoutRecoveryIsHistoricalOnly() {
        val incident = incident(
            state = DiagnosticIncidentState.Exported,
            requiresRecovery = false,
        )

        assertFalse(incident.requiresUserAttention())
        assertFalse(incident.isRelevantToStartupSafety())
        assertEquals(
            RecoveryAttentionReason.IncidentExported,
            incident.recoveryAttentionReason(),
        )
    }

    @Test
    fun exportedIncidentDoesNotBecomeSafeModeEvidence() {
        val incident = incident(
            state = DiagnosticIncidentState.Exported,
            requiresRecovery = true,
        )

        assertFalse(incident.requiresUserAttention())
        assertFalse(incident.isRelevantToStartupSafety())
        assertEquals(
            RecoveryAttentionReason.IncidentExported,
            incident.recoveryAttentionReason(),
        )
    }

    @Test
    fun incidentWithoutRecoveryDoesNotRequestUserAttention() {
        val incident = incident(
            state = DiagnosticIncidentState.PendingReview,
            requiresRecovery = false,
        )

        assertFalse(incident.requiresUserAttention())
        assertEquals(
            RecoveryAttentionReason.RecoveryNotRequired,
            incident.recoveryAttentionReason(),
        )
    }

    @Test
    fun actionableRecoveryRequestsUserAttention() {
        val incident = incident(
            state = DiagnosticIncidentState.PendingReview,
            requiresRecovery = true,
        )

        assertTrue(incident.requiresUserAttention())
        assertEquals(
            RecoveryAttentionReason.ActionRequired,
            incident.recoveryAttentionReason(),
        )
    }

    @Test
    fun acknowledgedRecoveryRemainsAvailableWithoutAnotherNotification() {
        val incident = incident(
            state = DiagnosticIncidentState.Acknowledged,
            requiresRecovery = true,
        )

        assertFalse(incident.requiresUserAttention())
        assertTrue(incident.isRelevantToStartupSafety())
        assertEquals(
            RecoveryAttentionReason.AlreadyAcknowledged,
            incident.recoveryAttentionReason(),
        )
    }

    @Test
    fun acknowledgedIncidentWithoutRecoveryIsHistoricalOnly() {
        val incident = incident(
            state = DiagnosticIncidentState.Acknowledged,
            requiresRecovery = false,
        )

        assertFalse(incident.requiresUserAttention())
        assertFalse(incident.isRelevantToStartupSafety())
    }

    @Test
    fun recoveryAttemptDoesNotProduceASecondNotification() {
        val incident = incident(
            state = DiagnosticIncidentState.RecoveryAttempted,
            requiresRecovery = true,
        )

        assertFalse(incident.requiresUserAttention())
        assertTrue(incident.isRelevantToStartupSafety())
    }

    private fun incident(
        state: DiagnosticIncidentState,
        requiresRecovery: Boolean,
    ) = DiagnosticIncident(
        id = "incident",
        type = DiagnosticIncidentType.UnknownAbnormalExit,
        severity = DiagnosticIncidentSeverity.Warning,
        state = state,
        detectedAtEpochMs = 1,
        lastSeenAtEpochMs = 1,
        processName = "test",
        sessionId = "session",
        startupAttemptId = "attempt",
        startupStage = DiagnosticStartupStage.FirstFrameRendered,
        fingerprint = "fingerprint",
        summary = "summary",
        detail = null,
        artifactPaths = emptyList(),
        relatedLogSessionIds = emptyList(),
        occurrenceCount = 1,
        occurrenceTimestampsEpochMs = listOf(1),
        requiresRecovery = requiresRecovery,
    )
}
