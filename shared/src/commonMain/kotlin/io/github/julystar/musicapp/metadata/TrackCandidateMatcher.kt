package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlin.math.abs

enum class TrackCandidateMatchConfidence { HIGH, MEDIUM, LOW }

data class TrackCandidateMatch(
    val candidate: MetaSongCandidate,
    val confidence: TrackCandidateMatchConfidence,
    val score: Int,
)

/** Shared scoring rules for local tracks and plugin candidates. */
object TrackCandidateMatcher {
    fun match(
        track: TrackEntity,
        candidates: Iterable<MetaSongCandidate>,
        filenameHints: FilenameMetadata? = null,
        albumHint: String? = filenameHints?.album,
    ): TrackCandidateMatch? = candidates.mapNotNull { candidate ->
        score(track, candidate, filenameHints, albumHint)?.let { (confidence, points) ->
            TrackCandidateMatch(candidate, confidence, points)
        }
    }.maxWithOrNull(compareBy<TrackCandidateMatch> { it.score }.thenBy { it.candidate.id })

    fun score(
        track: TrackEntity,
        candidate: MetaSongCandidate,
        filenameHints: FilenameMetadata? = null,
        albumHint: String? = filenameHints?.album,
    ): Pair<TrackCandidateMatchConfidence, Int>? {
        val title = track.title.key()
        val candidateTitle = candidate.title.key()
        val hintedTitle = filenameHints?.title?.key()
        if (candidateTitle.isBlank() || title.isBlank()) return null
        if (TrackVersionTokens.conflict(track.title, candidate.title)) return null
        if (strongIdConflict(track, candidate)) return null
        var points = 0
        if (candidateTitle == title) points += 45 else if (candidateTitle.contains(title) || title.contains(candidateTitle)) points += 18
        val artist = track.artist?.key().orEmpty()
        val candidateArtist = candidate.artist?.key().orEmpty()
        var artistConflict = false
        if (artist.isNotBlank() && candidateArtist.isNotBlank()) {
            if (artist == candidateArtist) points += 25 else if (artist.contains(candidateArtist) || candidateArtist.contains(artist)) points += 10
            else artistConflict = true
        }
        if (!albumHint.isNullOrBlank() && candidate.album?.key() == albumHint.key()) points += 10
        if (hintedTitle != null && hintedTitle == candidateTitle) points += 8
        track.durationMs?.let { local -> candidate.durationMs?.let { remote ->
            when {
                abs(local - remote) <= 1_000 -> points += 20
                abs(local - remote) <= 3_000 -> points += 8
                else -> return null
            }
        } }
        candidate.trackNumber?.substringBefore('/')?.toIntOrNull()?.let { number ->
            if (track.trackNumber == number) points += 5
        }
        if (
            !track.metadataSourceId.isNullOrBlank() &&
            !track.metadataExternalId.isNullOrBlank() &&
            track.metadataSourceId == candidate.sourceId &&
            track.metadataExternalId == candidate.id
        ) points += 60
        points += exactStrongIdScore(track, candidate)
        val confidence = when {
            points >= 75 && !artistConflict -> TrackCandidateMatchConfidence.HIGH
            points >= 45 -> TrackCandidateMatchConfidence.MEDIUM
            points >= 20 -> TrackCandidateMatchConfidence.LOW
            else -> return null
        }
        return confidence to points
    }

    private fun String.key(): String = trim().lowercase().replace(Regex("\\s+"), " ")

    private fun strongIdConflict(track: TrackEntity, candidate: MetaSongCandidate): Boolean =
        strongIds(track, candidate).any { (local, remote) ->
            !local.isNullOrBlank() && !remote.isNullOrBlank() && !local.equals(remote, ignoreCase = true)
        }

    private fun exactStrongIdScore(track: TrackEntity, candidate: MetaSongCandidate): Int =
        strongIds(track, candidate).sumOf { (local, remote) ->
            if (!local.isNullOrBlank() && local.equals(remote, ignoreCase = true)) 35 else 0
        }

    private fun strongIds(track: TrackEntity, candidate: MetaSongCandidate): List<Pair<String?, String?>> = listOf(
        track.musicBrainzRecordingId to candidate.fields.value("musicBrainzRecordingId", "musicbrainzRecordingId", "mbid"),
        track.isrc to candidate.fields.value("isrc"),
        track.musicBrainzTrackId to candidate.fields.value("musicBrainzTrackId", "musicbrainzTrackId"),
        track.musicBrainzReleaseId to candidate.fields.value("musicBrainzReleaseId", "musicbrainzReleaseId"),
        track.musicBrainzReleaseGroupId to candidate.fields.value("musicBrainzReleaseGroupId", "musicbrainzReleaseGroupId"),
    )

    private fun Map<String, String>.value(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
        }
}
