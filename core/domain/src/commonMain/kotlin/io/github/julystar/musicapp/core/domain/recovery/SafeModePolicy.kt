package io.github.julystar.musicapp.core.domain.recovery

import io.github.julystar.musicapp.core.domain.model.DiagnosticIncident
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentSeverity
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentType
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupAttempt
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupStage

const val REPEATED_FATAL_SHORT_WINDOW_MS = 10L * 60 * 1_000
const val REPEATED_FATAL_LONG_WINDOW_MS = 24L * 60 * 60 * 1_000
const val REPEATED_FATAL_SHORT_WINDOW_COUNT = 2
const val REPEATED_FATAL_LONG_WINDOW_COUNT = 3
const val REPEATED_UNKNOWN_STARTUP_EXIT_COUNT = 2

enum class StartupMode {
    NormalStartup,
    SafeMode,
    NormalStartupWithWarning,
    NormalStartupWithPluginsDisabled,
    NormalStartupWithAutoScanDisabled,
}

data class StartupPlan(
    val mode: StartupMode,
    val reason: String? = null,
    val primaryIncidentId: String? = null,
    val disabledComponents: Set<String> = emptySet(),
)

fun StartupPlan.allowsNormalApplicationInitialization(): Boolean =
    mode != StartupMode.SafeMode

data class IncidentOccurrence(
    val fingerprint: String,
    val incidentType: DiagnosticIncidentType,
    val severity: DiagnosticIncidentSeverity,
    val timestampEpochMs: Long,
    val startupStage: DiagnosticStartupStage?,
)

data class SafeModePolicyInput(
    val pendingIncidents: List<DiagnosticIncident>,
    val lastStartupAttempt: DiagnosticStartupAttempt?,
    val occurrences: List<IncidentOccurrence>,
    val currentTimeEpochMs: Long,
    val userForcedSafeMode: Boolean = false,
    val previousRecoveryFailedAtSameStage: Boolean = false,
)

class SafeModePolicy {
    fun decide(input: SafeModePolicyInput): StartupPlan {
        if (input.userForcedSafeMode) {
            return safeMode("Safe mode was requested by the user", null)
        }
        val relevantIncidents = input.pendingIncidents
            .filter(DiagnosticIncident::isRelevantToStartupSafety)
        val relevantFingerprints = relevantIncidents
            .asSequence()
            .mapNotNull(DiagnosticIncident::fingerprint)
            .toSet()
        val relevantInput = input.copy(
            pendingIncidents = relevantIncidents,
            occurrences = input.occurrences.filter { it.fingerprint in relevantFingerprints },
        )
        if (
            relevantInput.previousRecoveryFailedAtSameStage &&
            relevantInput.pendingIncidents.isNotEmpty()
        ) {
            return safeMode(
                "The previous recovery attempt failed at the same startup stage",
                firstId(relevantInput),
            )
        }

        val primary = relevantInput.pendingIncidents.maxByOrNull(DiagnosticIncident::lastSeenAtEpochMs)
        val databaseFailure = relevantInput.pendingIncidents.firstOrNull {
            it.type == DiagnosticIncidentType.DatabaseOpenFailure ||
                it.type == DiagnosticIncidentType.DatabaseMigrationFailure
        }
        if (databaseFailure != null) {
            return safeMode("The database could not be opened or migrated", databaseFailure.id)
        }

        val startupOrRepeatedAnr = relevantInput.pendingIncidents.firstOrNull {
            it.type == DiagnosticIncidentType.AndroidAnr &&
                (
                    it.requiresRecovery ||
                        it.startupStage?.isBeforeStable == true ||
                        it.occurrenceCount >= REPEATED_FATAL_SHORT_WINDOW_COUNT
                    )
        }
        if (startupOrRepeatedAnr != null) {
            return safeMode(
                "Android reported an ANR during startup or the same ANR repeated",
                startupOrRepeatedAnr.id,
            )
        }

        val pluginFailure = relevantInput.pendingIncidents.firstOrNull {
            it.type == DiagnosticIncidentType.PluginBootFailure ||
                it.startupStage == DiagnosticStartupStage.PluginsLoading
        }
        if (pluginFailure != null) {
            return StartupPlan(
                mode = StartupMode.NormalStartupWithPluginsDisabled,
                reason = "A third-party plugin failed during startup",
                primaryIncidentId = pluginFailure.id,
                disabledComponents = setOf("third_party_plugins"),
            )
        }

        val scanFailure = relevantInput.pendingIncidents.firstOrNull {
            it.startupStage == DiagnosticStartupStage.SourceTasksScheduling
        }
        if (scanFailure != null) {
            return StartupPlan(
                mode = StartupMode.NormalStartupWithAutoScanDisabled,
                reason = "Automatic source work failed during startup",
                primaryIncidentId = scanFailure.id,
                disabledComponents = setOf("automatic_scan", "background_sync"),
            )
        }

        val playbackRestoreFailure = relevantInput.pendingIncidents.firstOrNull {
            it.startupStage == DiagnosticStartupStage.PlaybackRestoring
        }
        if (playbackRestoreFailure != null) {
            return StartupPlan(
                mode = StartupMode.NormalStartupWithWarning,
                reason = "The previous playback queue could not be restored",
                primaryIncidentId = playbackRestoreFailure.id,
                disabledComponents = setOf("playback_restore"),
            )
        }

        val repeatedUnknownStartupExit = relevantInput.pendingIncidents.firstOrNull {
            it.type == DiagnosticIncidentType.UnknownAbnormalExit &&
                it.startupStage?.isBeforeStable == true &&
                it.occurrenceCount >= REPEATED_UNKNOWN_STARTUP_EXIT_COUNT
        }
        if (repeatedUnknownStartupExit != null) {
            return safeMode(
                "Startup ended abnormally more than once at the same stage",
                repeatedUnknownStartupExit.id,
            )
        }

        val startupFatal = relevantInput.pendingIncidents.firstOrNull { incident ->
            incident.startupStage?.isBeforeStable == true && when (incident.type) {
                DiagnosticIncidentType.KotlinUncaught,
                DiagnosticIncidentType.RustPanic,
                DiagnosticIncidentType.NativeCrash,
                DiagnosticIncidentType.StartupFailure,
                -> true
                else -> false
            }
        }
        if (startupFatal != null) {
            return safeMode("A fatal failure occurred before startup became stable", startupFatal.id)
        }

        repeatedFatal(relevantInput, REPEATED_FATAL_SHORT_WINDOW_MS, REPEATED_FATAL_SHORT_WINDOW_COUNT)
            ?.let { fingerprint ->
                return safeMode(
                    "The same fatal failure repeated within 10 minutes",
                    incidentId(relevantInput, fingerprint),
                )
            }
        repeatedFatal(relevantInput, REPEATED_FATAL_LONG_WINDOW_MS, REPEATED_FATAL_LONG_WINDOW_COUNT)
            ?.let { fingerprint ->
                return safeMode(
                    "The same fatal failure repeated within 24 hours",
                    incidentId(relevantInput, fingerprint),
                )
            }

        val stableAnr = relevantInput.pendingIncidents.firstOrNull {
            it.type == DiagnosticIncidentType.AndroidAnr &&
                it.startupStage?.isBeforeStable != true &&
                it.occurrenceCount < REPEATED_FATAL_SHORT_WINDOW_COUNT
        }
        if (stableAnr != null) {
            return warning("Android reported a single ANR after stable startup", stableAnr.id)
        }

        val warningOnly = relevantInput.pendingIncidents.firstOrNull {
            it.type in warningOnlyTypes ||
                it.type == DiagnosticIncidentType.PlaybackBackendFailure ||
                it.severity != DiagnosticIncidentSeverity.Fatal
        }
        if (warningOnly != null) {
            return warning("A previous abnormal exit was detected", warningOnly.id)
        }
        return if (primary != null) {
            warning("A previous incident requires recovery", primary.id)
        } else {
            StartupPlan(StartupMode.NormalStartup)
        }
    }

    private fun repeatedFatal(
        input: SafeModePolicyInput,
        windowMs: Long,
        threshold: Int,
    ): String? {
        val cutoff = input.currentTimeEpochMs - windowMs
        return input.occurrences
            .asSequence()
            .filter { occurrence ->
                occurrence.severity == DiagnosticIncidentSeverity.Fatal &&
                    occurrence.timestampEpochMs >= cutoff
            }
            .groupingBy(IncidentOccurrence::fingerprint)
            .eachCount()
            .entries
            .firstOrNull { it.value >= threshold }
            ?.key
    }

    private fun safeMode(reason: String, incidentId: String?) = StartupPlan(
        mode = StartupMode.SafeMode,
        reason = reason,
        primaryIncidentId = incidentId,
        disabledComponents = SAFE_MODE_DISABLED_COMPONENTS,
    )

    private fun warning(reason: String, incidentId: String?) = StartupPlan(
        mode = StartupMode.NormalStartupWithWarning,
        reason = reason,
        primaryIncidentId = incidentId,
    )

    private fun firstId(input: SafeModePolicyInput): String? = input.pendingIncidents.firstOrNull()?.id

    private fun incidentId(input: SafeModePolicyInput, fingerprint: String): String? =
        input.pendingIncidents.firstOrNull { it.fingerprint == fingerprint }?.id

    private companion object {
        val warningOnlyTypes = setOf(
            DiagnosticIncidentType.AndroidAnr,
            DiagnosticIncidentType.OutOfMemory,
            DiagnosticIncidentType.UnknownAbnormalExit,
            DiagnosticIncidentType.UiEventLoopStall,
        )
    }
}

val SAFE_MODE_DISABLED_COMPONENTS = setOf(
    "application_backend",
    "database_migration",
    "playback_restore",
    "player",
    "dsp",
    "third_party_plugins",
    "automatic_scan",
    "background_sync",
    "metadata_refresh",
    "scheduled_backup",
)

class StartupRecoveryPlanner(
    private val safeModePolicy: SafeModePolicy = SafeModePolicy(),
) {
    fun plan(input: SafeModePolicyInput): StartupPlan = safeModePolicy.decide(input)
}
