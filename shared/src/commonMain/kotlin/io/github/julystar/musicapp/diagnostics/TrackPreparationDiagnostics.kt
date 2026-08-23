package io.github.julystar.musicapp.diagnostics

import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogLevel
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsRepository

enum class TrackPreparationEvent(val eventName: String) {
    TrackPrepareScheduled("track_prepare_scheduled"),
    TrackPrepareMetadataStart("track_prepare_metadata_start"),
    TrackPrepareMetadataComplete("track_prepare_metadata_complete"),
    TrackPrepareFilenameDetected("track_prepare_filename_detected"),
    TrackPreparePluginLookupStart("track_prepare_plugin_lookup_start"),
    TrackPreparePluginCandidate("track_prepare_plugin_candidate"),
    TrackPreparePluginLowConfidence("track_prepare_plugin_low_confidence"),
    TrackPrepareMetadataCommitted("track_prepare_metadata_committed"),
    TrackIdentityReconcileStart("track_identity_reconcile_start"),
    TrackIdentityCandidateFound("track_identity_candidate_found"),
    TrackIdentityEvidence("track_identity_evidence"),
    TrackIdentityReleaseConflict("track_identity_release_conflict"),
    TrackIdentityVersionConflict("track_identity_version_conflict"),
    TrackIdentityMergePlanned("track_identity_merge_planned"),
    TrackIdentityMergeCompleted("track_identity_merge_completed"),
    TrackIdentityMergeSkipped("track_identity_merge_skipped"),
    TrackIdentityMergeFailed("track_identity_merge_failed"),
    TrackIdentityRemapped("track_identity_remapped"),
    TrackPrepareArtworkComplete("track_prepare_artwork_complete"),
    TrackPrepareLyricsComplete("track_prepare_lyrics_complete"),
    TrackPrepareAudioStart("track_prepare_audio_start"),
    TrackPrepareAudioCacheHit("track_prepare_audio_cache_hit"),
    TrackPrepareAudioComplete("track_prepare_audio_complete"),
    TrackPrepareCancelled("track_prepare_cancelled"),
    TrackPrepareFailed("track_prepare_failed"),
}

data class TrackPreparationDiagnosticFields(
    val trackId: Long? = null,
    val canonicalTrackId: Long? = null,
    val candidateTrackId: Long? = null,
    val stage: String? = null,
    val metadataSource: String? = null,
    val candidateSourceId: String? = null,
    val matchMethod: String? = null,
    val confidence: Double? = null,
    val durationDifferenceMs: Long? = null,
    val targetBytes: Long? = null,
    val cachedBytes: Long? = null,
    val remoteBytes: Long? = null,
    val elapsedMs: Long? = null,
    val reason: String? = null,
)

class TrackPreparationDiagnostics internal constructor(
    private val sink: (TrackPreparationDiagnosticRecord) -> Unit,
) {
    constructor(repository: DiagnosticsRepository) : this(
        sink = { record ->
            repository.log(
                level = record.level,
                category = record.category,
                target = record.target,
                message = record.message,
                detail = null,
                correlationId = record.correlationId,
                fields = record.fields,
            )
        },
    )

    fun record(
        event: TrackPreparationEvent,
        fields: TrackPreparationDiagnosticFields = TrackPreparationDiagnosticFields(),
        correlationId: String? = null,
    ) {
        sink(
            TrackPreparationDiagnosticRecord(
                level = event.level(),
                category = DiagnosticLogCategory.Playback,
                target = TRACK_PREPARATION_DIAGNOSTIC_TARGET,
                message = event.eventName,
                correlationId = correlationId.safeDiagnosticValue(),
                fields = fields.toSafeMap(),
            )
        )
    }
}

internal data class TrackPreparationDiagnosticRecord(
    val level: DiagnosticLogLevel,
    val category: DiagnosticLogCategory,
    val target: String,
    val message: String,
    val correlationId: String?,
    val fields: Map<String, String>,
)

private fun TrackPreparationEvent.level(): DiagnosticLogLevel = when (this) {
    TrackPreparationEvent.TrackPrepareCancelled -> DiagnosticLogLevel.Debug
    TrackPreparationEvent.TrackPreparePluginLowConfidence,
    TrackPreparationEvent.TrackIdentityReleaseConflict,
    TrackPreparationEvent.TrackIdentityVersionConflict,
    TrackPreparationEvent.TrackIdentityMergeSkipped,
    -> DiagnosticLogLevel.Warn
    TrackPreparationEvent.TrackIdentityMergeFailed,
    TrackPreparationEvent.TrackPrepareFailed,
    -> DiagnosticLogLevel.Error
    else -> DiagnosticLogLevel.Info
}

private fun TrackPreparationDiagnosticFields.toSafeMap(): Map<String, String> = buildMap {
    trackId?.let { put("trackId", it.toString()) }
    canonicalTrackId?.let { put("canonicalTrackId", it.toString()) }
    candidateTrackId?.let { put("candidateTrackId", it.toString()) }
    putSafe("stage", stage)
    putSafe("metadataSource", metadataSource)
    putSafe("candidateSourceId", candidateSourceId)
    putSafe("matchMethod", matchMethod)
    confidence
        ?.takeIf { it.isFinite() }
        ?.let { put("confidence", it.toString()) }
    durationDifferenceMs?.let { put("durationDifferenceMs", it.toString()) }
    targetBytes?.let { put("targetBytes", it.toString()) }
    cachedBytes?.let { put("cachedBytes", it.toString()) }
    remoteBytes?.let { put("remoteBytes", it.toString()) }
    elapsedMs?.let { put("elapsedMs", it.toString()) }
    putSafe("reason", reason)
}

private fun MutableMap<String, String>.putSafe(key: String, value: String?) {
    value.safeDiagnosticValue()?.let { put(key, it) }
}

private fun String?.safeDiagnosticValue(): String? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val normalized = value.lowercase()
    if (UNSAFE_DIAGNOSTIC_MARKERS.any(normalized::contains)) return null
    if (normalized.contains(": ") || normalized.contains('\n') || normalized.contains('\r')) {
        return null
    }
    return value.take(MAX_DIAGNOSTIC_VALUE_LENGTH)
}

private const val TRACK_PREPARATION_DIAGNOSTIC_TARGET = "track_preparation"
private const val MAX_DIAGNOSTIC_VALUE_LENGTH = 256
private val UNSAFE_DIAGNOSTIC_MARKERS = listOf(
    "://",
    "authorization",
    "access token",
    "access_token",
    "cookie",
    "credential",
    "bearer ",
    "token",
    "secret",
    "password",
    "passwd",
)
