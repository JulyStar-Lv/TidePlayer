package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.domain.importing.DURATION_MATCH_TOLERANCE_MS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** The relationship between two rows in the current single-release library model. */
enum class TrackIdentityRelation {
    SameLibraryTrack,
    SameRecordingDifferentRelease,
    DifferentVersion,
    Uncertain,
    Different,
}

data class TrackIdentitySource(
    val sourceAccountId: Long? = null,
    val contentHash: String? = null,
    val audioFingerprint: String? = null,
)

data class TrackIdentitySnapshot(
    val track: TrackEntity,
    val albumName: String? = null,
    val sources: List<TrackIdentitySource> = emptyList(),
)

data class TrackIdentityMatch(
    val relation: TrackIdentityRelation,
    val method: String? = null,
    val confidence: Int = 0,
) {
    val canMerge: Boolean get() = relation == TrackIdentityRelation.SameLibraryTrack
}

/** One matching engine for both global maintenance and incremental preparation. */
object TrackIdentityMatcher {
    fun match(left: TrackIdentitySnapshot, right: TrackIdentitySnapshot): TrackIdentityMatch {
        val a = left.track
        val b = right.track
        if (a.id == b.id) return TrackIdentityMatch(TrackIdentityRelation.SameLibraryTrack, "same-id", 100)
        if (TrackVersionTokens.conflict(a.title, b.title)) {
            return TrackIdentityMatch(TrackIdentityRelation.DifferentVersion, "version-conflict")
        }
        strongRecordingConflict(a, b)?.let { return TrackIdentityMatch(TrackIdentityRelation.Different, it) }

        val releaseConflict = releaseConflict(left, right)
        if (sharedSourceValue(left, right) { it.contentHash?.normalizedId() }) {
            return evidenceResult(releaseConflict, "content-hash", 100)
        }
        if (
            sharedSourceValue(left, right) { it.audioFingerprint?.trim()?.takeIf(String::isNotEmpty) } &&
            durationCompatible(a.durationMs, b.durationMs)
        ) return evidenceResult(releaseConflict, "fingerprint-duration", 95)
        if (sameNonBlank(a.musicBrainzRecordingId, b.musicBrainzRecordingId)) {
            return evidenceResult(releaseConflict, "musicbrainz-recording", 98)
        }
        if (sameNonBlank(a.musicBrainzTrackId, b.musicBrainzTrackId)) {
            return evidenceResult(releaseConflict, "musicbrainz-track", 99)
        }
        if (sameNonBlank(a.isrc, b.isrc) && durationCompatible(a.durationMs, b.durationMs)) {
            return evidenceResult(releaseConflict, "isrc-duration", 92)
        }
        val samePluginIdentity = !a.metadataSourceId.isNullOrBlank() &&
            !a.metadataExternalId.isNullOrBlank() &&
            a.metadataSourceId == b.metadataSourceId &&
            a.metadataExternalId == b.metadataExternalId
        if (samePluginIdentity) return evidenceResult(releaseConflict, "plugin-external-id", 88)

        val strictMetadata = normalized(a.title) == normalized(b.title) &&
            normalized(a.artist) == normalized(b.artist) &&
            !a.title.isBlank() && !a.artist.isNullOrBlank() &&
            normalized(left.albumName) == normalized(right.albumName) &&
            !left.albumName.isNullOrBlank() && durationCompatible(a.durationMs, b.durationMs)
        if (strictMetadata && differentSourceAccounts(left, right)) {
            return evidenceResult(releaseConflict, "strict-metadata-duration", 70)
        }
        if (normalized(a.title) != normalized(b.title) ||
            (!a.artist.isNullOrBlank() && !b.artist.isNullOrBlank() && normalized(a.artist) != normalized(b.artist))
        ) return TrackIdentityMatch(TrackIdentityRelation.Different, "metadata-conflict")
        return TrackIdentityMatch(TrackIdentityRelation.Uncertain, releaseConflict)
    }

    fun pairwiseCompatible(group: Collection<TrackIdentitySnapshot>): Boolean {
        val values = group.toList()
        return values.indices.all { i ->
            (i + 1 until values.size).all { j -> match(values[i], values[j]).canMerge }
        }
    }

    private fun evidenceResult(releaseConflict: String?, method: String, confidence: Int): TrackIdentityMatch =
        if (releaseConflict == null) {
            TrackIdentityMatch(TrackIdentityRelation.SameLibraryTrack, method, confidence)
        } else {
            TrackIdentityMatch(TrackIdentityRelation.SameRecordingDifferentRelease, method, confidence)
        }

    private fun releaseConflict(left: TrackIdentitySnapshot, right: TrackIdentitySnapshot): String? {
        val a = left.track
        val b = right.track
        if (differentNonBlank(a.musicBrainzReleaseId, b.musicBrainzReleaseId)) return "musicbrainz-release-conflict"
        if (differentNonBlank(a.musicBrainzReleaseGroupId, b.musicBrainzReleaseGroupId)) return "release-group-conflict"
        if (differentNonBlank(a.musicBrainzTrackId, b.musicBrainzTrackId)) return "musicbrainz-track-conflict"
        if (a.albumId != null && b.albumId != null && a.albumId != b.albumId) return "album-id-conflict"
        if (differentNonBlank(left.albumName, right.albumName)) return "album-name-conflict"
        if (differentNonBlank(a.albumArtist, b.albumArtist)) return "album-artist-conflict"
        if (differentNonNull(a.discNumber, b.discNumber)) return "disc-number-conflict"
        if (differentNonNull(a.trackNumber, b.trackNumber)) return "track-number-conflict"
        if (differentNonBlank(a.date, b.date)) return "date-conflict"
        return null
    }

    private fun strongRecordingConflict(a: TrackEntity, b: TrackEntity): String? = when {
        differentNonBlank(a.musicBrainzRecordingId, b.musicBrainzRecordingId) -> "musicbrainz-recording-conflict"
        differentNonBlank(a.isrc, b.isrc) -> "isrc-conflict"
        else -> null
    }

    private fun sharedSourceValue(
        left: TrackIdentitySnapshot,
        right: TrackIdentitySnapshot,
        value: (TrackIdentitySource) -> String?,
    ): Boolean {
        val leftValues = left.sources.mapNotNull(value).toSet()
        return leftValues.isNotEmpty() && right.sources.any { value(it) in leftValues }
    }

    private fun differentSourceAccounts(left: TrackIdentitySnapshot, right: TrackIdentitySnapshot): Boolean {
        val leftAccounts = left.sources.mapNotNull(TrackIdentitySource::sourceAccountId).toSet()
        val rightAccounts = right.sources.mapNotNull(TrackIdentitySource::sourceAccountId).toSet()
        return leftAccounts.any { it !in rightAccounts } || rightAccounts.any { it !in leftAccounts }
    }

    private fun durationCompatible(a: Long?, b: Long?): Boolean =
        a != null && b != null && abs(a - b) <= DURATION_MATCH_TOLERANCE_MS

    private fun sameNonBlank(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim().equals(b.trim(), ignoreCase = true)

    private fun differentNonBlank(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && !a.trim().equals(b.trim(), ignoreCase = true)

    private fun <T> differentNonNull(a: T?, b: T?): Boolean = a != null && b != null && a != b
    private fun String.normalizedId(): String = trim().lowercase()
    private fun normalized(value: String?): String = value.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}

class TrackReleaseCompatibilityChecker(
    private val matcher: (TrackIdentitySnapshot, TrackIdentitySnapshot) -> TrackIdentityMatch = TrackIdentityMatcher::match,
) {
    fun relation(left: TrackIdentitySnapshot, right: TrackIdentitySnapshot): TrackIdentityMatch = matcher(left, right)
    fun canMerge(left: TrackIdentitySnapshot, right: TrackIdentitySnapshot): Boolean = relation(left, right).canMerge
    fun allPairwiseCompatible(group: Collection<TrackIdentitySnapshot>): Boolean =
        TrackIdentityMatcher.pairwiseCompatible(group)
}

fun interface IncrementalTrackIdentityCandidateProvider {
    suspend fun candidates(track: TrackEntity): List<TrackIdentitySnapshot>
}

object TrackIdentityMergeMutex {
    private val mutex = Mutex()
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

/** Finite, evidence-keyed candidate loading; it never calls the global listCandidates query. */
class TrackDaoIdentityCandidateProvider(
    private val queries: FiniteTrackIdentityCandidateQueries,
    private val snapshot: suspend (TrackEntity) -> TrackIdentitySnapshot,
) : IncrementalTrackIdentityCandidateProvider {
    constructor(
        trackDao: TrackDao,
        snapshot: suspend (TrackEntity) -> TrackIdentitySnapshot,
    ) : this(RoomFiniteTrackIdentityCandidateQueries(trackDao), snapshot)

    override suspend fun candidates(track: TrackEntity): List<TrackIdentitySnapshot> {
        val current = snapshot(track)
        val result = linkedMapOf<Long, TrackEntity>()
        fun add(values: List<TrackEntity>) = values.forEach { value -> result[value.id] = value }

        current.sources.mapNotNull { it.contentHash?.takeIf(String::isNotBlank) }.distinct().forEach { hash ->
            add(queries.findBySourceContentHash(hash))
        }
        track.durationMs?.let { duration ->
            current.sources.mapNotNull { it.audioFingerprint?.takeIf(String::isNotBlank) }
                .distinct().forEach { fingerprint ->
                    add(queries.findByAudioFingerprintWithinDuration(
                        fingerprint,
                        (duration - DURATION_MATCH_TOLERANCE_MS).coerceAtLeast(0),
                        duration + DURATION_MATCH_TOLERANCE_MS,
                    ))
                }
        }
        track.musicBrainzRecordingId?.takeIf(String::isNotBlank)?.let { add(queries.findByMusicBrainzRecordingId(it)) }
        track.durationMs?.let { duration ->
            track.isrc?.takeIf(String::isNotBlank)?.let { isrc ->
                add(queries.findByIsrcWithinDuration(
                    isrc,
                    (duration - DURATION_MATCH_TOLERANCE_MS).coerceAtLeast(0),
                    duration + DURATION_MATCH_TOLERANCE_MS,
                ))
            }
        }
        if (!track.metadataSourceId.isNullOrBlank() && !track.metadataExternalId.isNullOrBlank()) {
            add(queries.findByPluginExternalIdentity(track.metadataSourceId, track.metadataExternalId))
        }
        val duration = track.durationMs
        val album = current.albumName
        if (duration != null && !track.title.isBlank() && !track.artist.isNullOrBlank() && !album.isNullOrBlank()) {
            add(queries.findByStrictMetadata(
                normalized(track.title),
                normalized(track.artist),
                normalized(album),
                (duration - DURATION_MATCH_TOLERANCE_MS).coerceAtLeast(0),
                duration + DURATION_MATCH_TOLERANCE_MS,
            ))
        }
        return result.values
            .filterNot { it.id == track.id }
            .sortedBy(TrackEntity::id)
            .map { snapshot(it) }
    }

    private fun normalized(value: String?): String = value.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}

interface FiniteTrackIdentityCandidateQueries {
    suspend fun findBySourceContentHash(contentHash: String): List<TrackEntity>
    suspend fun findByAudioFingerprintWithinDuration(
        audioFingerprint: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ): List<TrackEntity>
    suspend fun findByMusicBrainzRecordingId(recordingId: String): List<TrackEntity>
    suspend fun findByIsrcWithinDuration(isrc: String, minDurationMs: Long, maxDurationMs: Long): List<TrackEntity>
    suspend fun findByPluginExternalIdentity(sourceId: String, externalId: String): List<TrackEntity>
    suspend fun findByStrictMetadata(
        titleKey: String,
        artistKey: String,
        albumKey: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ): List<TrackEntity>
}

private class RoomFiniteTrackIdentityCandidateQueries(
    private val trackDao: TrackDao,
) : FiniteTrackIdentityCandidateQueries {
    override suspend fun findBySourceContentHash(contentHash: String) = trackDao.findBySourceContentHash(contentHash)
    override suspend fun findByAudioFingerprintWithinDuration(
        audioFingerprint: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ) = trackDao.findByAudioFingerprintWithinDuration(audioFingerprint, minDurationMs, maxDurationMs)
    override suspend fun findByMusicBrainzRecordingId(recordingId: String) =
        trackDao.findByMusicBrainzRecordingId(recordingId)
    override suspend fun findByIsrcWithinDuration(isrc: String, minDurationMs: Long, maxDurationMs: Long) =
        trackDao.findByIsrcWithinDuration(isrc, minDurationMs, maxDurationMs)
    override suspend fun findByPluginExternalIdentity(sourceId: String, externalId: String) =
        trackDao.findByPluginExternalIdentity(sourceId, externalId)
    override suspend fun findByStrictMetadata(
        titleKey: String,
        artistKey: String,
        albumKey: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ) = trackDao.findByStrictMetadata(titleKey, artistKey, albumKey, minDurationMs, maxDurationMs)
}

internal fun selectCanonicalTrackId(
    snapshots: Collection<TrackIdentitySnapshot>,
    favoriteTrackIds: Set<Long>,
    currentTrackId: Long?,
): Long? {
    if (snapshots.isEmpty()) return null
    val locked = snapshots.filter { it.track.metadataLocked }
    if (currentTrackId != null && snapshots.any { it.track.id == currentTrackId } &&
        locked.any { it.track.id != currentTrackId }
    ) return null
    return snapshots.sortedWith(
        compareByDescending<TrackIdentitySnapshot> { it.track.metadataLocked }
            .thenByDescending { it.track.id == currentTrackId }
            .thenByDescending { it.track.id in favoriteTrackIds }
            .thenByDescending { metadataQualityScore(it.track) }
            .thenByDescending { stableIdentityScore(it.track) }
            .thenByDescending { it.track.lastPlayedAt ?: Long.MIN_VALUE }
            .thenBy { it.track.createdAt }
            .thenBy { it.track.id },
    ).first().track.id
}

private fun metadataQualityScore(track: TrackEntity): Int = listOf(
    track.title, track.artist, track.albumArtist, track.composer, track.date, track.isrc,
    track.musicBrainzRecordingId, track.musicBrainzTrackId, track.musicBrainzReleaseId,
).count { !it.isNullOrBlank() } + when (track.metadataSource) {
    TrackMetadataSources.File, TrackMetadataSources.Server -> 4
    TrackMetadataSources.Plugin -> 2
    TrackMetadataSources.Filename -> 0
    else -> 1
}

private fun stableIdentityScore(track: TrackEntity): Int = listOf(
    track.musicBrainzRecordingId,
    track.musicBrainzTrackId,
    track.musicBrainzReleaseId,
    track.metadataExternalId,
).count { !it.isNullOrBlank() }
