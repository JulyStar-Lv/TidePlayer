package io.github.julystar.musicapp.diagnostics

import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TrackPreparationDiagnosticsTest {
    @Test
    fun recordsEveryTrackPreparationEventName() {
        val records = mutableListOf<TrackPreparationDiagnosticRecord>()
        val diagnostics = TrackPreparationDiagnostics(records::add)

        TrackPreparationEvent.entries.forEach { event -> diagnostics.record(event) }

        assertEquals(EXPECTED_EVENT_NAMES, records.map { it.message })
        records.forEach { record ->
            assertEquals(DiagnosticLogCategory.Playback, record.category)
            assertEquals("track_preparation", record.target)
        }
    }

    @Test
    fun convertsOnlyTypedFieldsToSafeStrings() {
        val records = mutableListOf<TrackPreparationDiagnosticRecord>()
        val diagnostics = TrackPreparationDiagnostics(records::add)

        diagnostics.record(
            event = TrackPreparationEvent.TrackPrepareAudioComplete,
            correlationId = "preparation-42",
            fields = TrackPreparationDiagnosticFields(
                trackId = 1L,
                canonicalTrackId = 2L,
                candidateTrackId = 3L,
                stage = "audio",
                metadataSource = "PLUGIN",
                candidateSourceId = "source-1",
                matchMethod = "recording_id",
                confidence = 0.98,
                durationDifferenceMs = 125L,
                targetBytes = 16_777_216L,
                cachedBytes = 8_388_608L,
                remoteBytes = 2_097_152L,
                elapsedMs = 450L,
                reason = "completed",
            ),
        )

        val record = records.single()
        assertEquals("preparation-42", record.correlationId)
        assertEquals(
            setOf(
                "trackId",
                "canonicalTrackId",
                "candidateTrackId",
                "stage",
                "metadataSource",
                "candidateSourceId",
                "matchMethod",
                "confidence",
                "durationDifferenceMs",
                "targetBytes",
                "cachedBytes",
                "remoteBytes",
                "elapsedMs",
                "reason",
            ),
            record.fields.keys,
        )
        assertEquals("0.98", record.fields["confidence"])
        assertEquals("1", record.fields["trackId"])
        assertEquals("2", record.fields["canonicalTrackId"])
        assertEquals("3", record.fields["candidateTrackId"])
        assertEquals("16777216", record.fields["targetBytes"])
        assertEquals("450", record.fields["elapsedMs"])
    }

    @Test
    fun omitsUrlsCredentialsHeadersAndNonFiniteConfidence() {
        val records = mutableListOf<TrackPreparationDiagnosticRecord>()
        val diagnostics = TrackPreparationDiagnostics(records::add)

        diagnostics.record(
            event = TrackPreparationEvent.TrackPrepareFailed,
            correlationId = "https://example.test/preparation/1",
            fields = TrackPreparationDiagnosticFields(
                trackId = 42L,
                metadataSource = "Authorization: Bearer access-token",
                candidateSourceId = "https://example.test/song?token=secret",
                matchMethod = "Cookie: session=value",
                confidence = Double.NaN,
                reason = "Credential header: plugin secret",
            ),
        )

        val record = records.single()
        assertNull(record.correlationId)
        assertEquals(mapOf("trackId" to "42"), record.fields)
        assertFalse(record.fields.values.any { it.contains("example.test") })
    }

    private companion object {
        val EXPECTED_EVENT_NAMES = listOf(
            "track_prepare_scheduled",
            "track_prepare_metadata_start",
            "track_prepare_metadata_complete",
            "track_prepare_filename_detected",
            "track_prepare_plugin_lookup_start",
            "track_prepare_plugin_candidate",
            "track_prepare_plugin_low_confidence",
            "track_prepare_metadata_committed",
            "track_identity_reconcile_start",
            "track_identity_candidate_found",
            "track_identity_evidence",
            "track_identity_release_conflict",
            "track_identity_version_conflict",
            "track_identity_merge_planned",
            "track_identity_merge_completed",
            "track_identity_merge_skipped",
            "track_identity_merge_failed",
            "track_identity_remapped",
            "track_prepare_artwork_complete",
            "track_prepare_lyrics_complete",
            "track_prepare_audio_start",
            "track_prepare_audio_cache_hit",
            "track_prepare_audio_complete",
            "track_prepare_cancelled",
            "track_prepare_failed",
        )
    }
}
