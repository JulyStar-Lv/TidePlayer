package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.platform.currentTimeMillis
import kotlinx.coroutines.flow.first

enum class TrackIdentityChangeReason {
    MetadataChanged,
    SourceIdentityChanged,
    Maintenance,
}

sealed interface TrackIdentityResult {
    data class Unchanged(val trackId: Long) : TrackIdentityResult
    data class Merged(
        val canonicalTrackId: Long,
        val mergedTrackIds: List<Long>,
        val matchMethod: String,
        val confidence: Int,
    ) : TrackIdentityResult
    data class PotentialDuplicate(
        val trackId: Long,
        val candidateTrackIds: List<Long>,
    ) : TrackIdentityResult
}

interface TrackIdentityReconciler {
    suspend fun reconcile(changedTrackId: Long, reason: TrackIdentityChangeReason): TrackIdentityResult
}

data class TrackIdentityMergeRequest(
    val targetTrackId: Long,
    val sourceTrackIds: List<Long>,
    val matchMethod: String,
    val matchConfidence: Int,
    val lastPlayedAt: Long?,
)

/** Idempotent DataStore precommit followed by one guarded Room transaction. */
class SafeTrackIdentityMergeExecutor(
    private val precommitRemap: suspend (Map<Long, Long>) -> Unit,
    private val roomMerge: suspend (TrackIdentityMergeRequest, Long) -> Boolean,
) {
    constructor(database: AppDatabase, preferencesRepository: AppPreferencesRepository) : this(
        precommitRemap = preferencesRepository::remapTrackIds,
        roomMerge = { request, now ->
            database.trackMergeDao().mergeTracks(
                targetTrackId = request.targetTrackId,
                sourceTrackIds = request.sourceTrackIds,
                matchMethod = request.matchMethod,
                matchConfidence = request.matchConfidence,
                lastPlayedAt = request.lastPlayedAt,
                now = now,
            )
        },
    )

    suspend fun execute(request: TrackIdentityMergeRequest): Boolean = TrackIdentityMergeMutex.withLock {
        executeLocked(request)
    }

    internal suspend fun executeLocked(request: TrackIdentityMergeRequest): Boolean {
        if (request.sourceTrackIds.isEmpty()) return false
        precommitRemap(request.sourceTrackIds.associateWith { request.targetTrackId })
        return roomMerge(request, currentTimeMillis())
    }
}

class RoomTrackIdentityReconciler(
    private val database: AppDatabase,
    private val preferencesRepository: AppPreferencesRepository,
    candidateProvider: IncrementalTrackIdentityCandidateProvider? = null,
    private val executor: SafeTrackIdentityMergeExecutor =
        SafeTrackIdentityMergeExecutor(database, preferencesRepository),
) : TrackIdentityReconciler {
    private val provider = candidateProvider ?: TrackDaoIdentityCandidateProvider(database.trackDao()) { track ->
        database.trackMergeDao().identitySnapshot(track.id) ?: TrackIdentitySnapshot(track)
    }

    override suspend fun reconcile(
        changedTrackId: Long,
        reason: TrackIdentityChangeReason,
    ): TrackIdentityResult = TrackIdentityMergeMutex.withLock {
        val changed = database.trackMergeDao().identitySnapshot(changedTrackId)
            ?: return@withLock TrackIdentityResult.Unchanged(changedTrackId)
        val candidates = provider.candidates(changed.track).sortedBy { it.track.id }
        val group = mutableListOf(changed)
        val groupMatches = mutableListOf<TrackIdentityMatch>()
        val potential = mutableSetOf<Long>()
        candidates.forEach { candidate ->
            val relation = TrackIdentityMatcher.match(changed, candidate)
            if (relation.canMerge && TrackIdentityMatcher.pairwiseCompatible(group + candidate)) {
                group += candidate
                groupMatches += relation
            } else if (
                relation.relation == TrackIdentityRelation.SameRecordingDifferentRelease ||
                relation.relation == TrackIdentityRelation.Uncertain || relation.canMerge
            ) {
                potential += candidate.track.id
            }
        }
        if (group.size == 1) {
            return@withLock if (potential.isEmpty()) {
                TrackIdentityResult.Unchanged(changedTrackId)
            } else {
                TrackIdentityResult.PotentialDuplicate(changedTrackId, potential.sorted())
            }
        }

        val favoriteIds = preferencesRepository.favoriteTrackIds.first()
        val currentTrackId = preferencesRepository.playbackSession.first()?.trackId
        val canonicalId = selectCanonicalTrackId(group, favoriteIds, currentTrackId)
            ?: return@withLock TrackIdentityResult.PotentialDuplicate(
                changedTrackId,
                group.map { it.track.id }.filterNot { it == changedTrackId }.sorted(),
            )
        val strongest = groupMatches.maxWithOrNull(
            compareBy<TrackIdentityMatch> { it.confidence }.thenBy { it.method.orEmpty() },
        ) ?: return@withLock TrackIdentityResult.Unchanged(changedTrackId)
        val sources = group.map { it.track.id }.filterNot { it == canonicalId }.sorted()
        val request = TrackIdentityMergeRequest(
            targetTrackId = canonicalId,
            sourceTrackIds = sources,
            matchMethod = strongest.method ?: "identity",
            matchConfidence = strongest.confidence,
            lastPlayedAt = group.mapNotNull { it.track.lastPlayedAt }.maxOrNull(),
        )
        if (executor.executeLocked(request)) {
            TrackIdentityResult.Merged(
                canonicalTrackId = canonicalId,
                mergedTrackIds = sources,
                matchMethod = request.matchMethod,
                confidence = request.matchConfidence,
            )
        } else {
            val survivingId = if (database.trackDao().get(changedTrackId) == null) canonicalId else changedTrackId
            TrackIdentityResult.Unchanged(survivingId)
        }
    }
}
