package io.github.julystar.musicapp.core.data.settings

import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackDeduplicationCandidate
import io.github.julystar.musicapp.database.TrackDeduplicationSource
import io.github.julystar.musicapp.domain.importing.DURATION_MATCH_TOLERANCE_MS
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_EXACT
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_FINGERPRINT
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_ISRC
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_STRICT_METADATA
import io.github.julystar.musicapp.domain.importing.TrackMatchMethods
import io.github.julystar.musicapp.domain.importing.normalizedTrackMatchKey
import io.github.julystar.musicapp.metadata.TrackIdentityMatcher
import io.github.julystar.musicapp.metadata.TrackIdentitySnapshot
import io.github.julystar.musicapp.metadata.TrackIdentitySource
import io.github.julystar.musicapp.metadata.SafeTrackIdentityMergeExecutor
import io.github.julystar.musicapp.metadata.TrackIdentityMergeRequest
import io.github.julystar.musicapp.metadata.selectCanonicalTrackId
import io.github.julystar.musicapp.metadata.TrackVersionTokens
import kotlinx.coroutines.flow.first

internal fun interface AutomaticTrackMerger {
    suspend fun merge()
}

internal class TrackDuplicateMerger(
    private val database: AppDatabase,
    private val preferencesRepository: AppPreferencesRepository,
) : AutomaticTrackMerger {
    private val executor = SafeTrackIdentityMergeExecutor(database, preferencesRepository)

    override suspend fun merge() {
        val candidates = database.trackMergeDao().listCandidates()
        if (candidates.size < 2) return

        val favoriteTrackIds = preferencesRepository.favoriteTrackIds.first()
        val currentTrackId = preferencesRepository.playbackSession.first()?.trackId
        val plans = buildTrackMergePlans(
            candidates = candidates,
            sources = database.trackMergeDao().listSources(),
            favoriteTrackIds = favoriteTrackIds,
            currentTrackId = currentTrackId,
        )
        if (plans.isEmpty()) return

        plans.forEach { plan ->
            executor.execute(TrackIdentityMergeRequest(
                targetTrackId = plan.targetTrackId,
                sourceTrackIds = plan.sourceTrackIds,
                matchMethod = plan.matchMethod,
                matchConfidence = plan.matchConfidence,
                lastPlayedAt = plan.lastPlayedAt,
            ))
        }
    }
}

internal data class TrackMergePlan(
    val targetTrackId: Long,
    val sourceTrackIds: List<Long>,
    val matchMethod: String,
    val matchConfidence: Int,
    val lastPlayedAt: Long?,
)

internal fun buildTrackMergePlans(
    candidates: List<TrackDeduplicationCandidate>,
    sources: List<TrackDeduplicationSource>,
    favoriteTrackIds: Set<Long> = emptySet(),
    currentTrackId: Long? = null,
): List<TrackMergePlan> {
    if (candidates.size < 2) return emptyList()
    val candidatesById = candidates.associateBy { candidate -> candidate.track.id }
    val sourceAccountsByTrack = sources.groupBy(TrackDeduplicationSource::trackId)
        .mapValues { (_, values) -> values.mapTo(mutableSetOf(), TrackDeduplicationSource::sourceAccountId) }
    val snapshotsById = candidates.associate { candidate ->
        candidate.track.id to TrackIdentitySnapshot(
            track = candidate.track,
            albumName = candidate.albumName,
            sources = sources.filter { it.trackId == candidate.track.id }.map { source ->
                TrackIdentitySource(source.sourceAccountId, source.contentHash, source.audioFingerprint)
            },
        )
    }
    val union = TrackIdUnion(candidatesById.keys)
    val evidence = mutableListOf<TrackMatchEvidence>()

    fun connect(
        trackIds: Collection<Long>,
        method: String,
        confidence: Int,
        priority: Int,
        allowSameSourceAccount: Boolean = false,
    ) {
        val ids = trackIds.distinct().filter(candidatesById::containsKey)
        if (ids.size < 2) return
        val sourceAccounts = ids.flatMap { trackId -> sourceAccountsByTrack[trackId].orEmpty() }.toSet()
        if (!allowSameSourceAccount && sourceAccounts.size < 2) return
        val snapshots = ids.map(snapshotsById::getValue)
        if (!TrackIdentityMatcher.pairwiseCompatible(snapshots)) return
        ids.drop(1).forEach { trackId -> union.union(ids.first(), trackId) }
        evidence += TrackMatchEvidence(ids.toSet(), method, confidence, priority)
    }

    sources.mapNotNull { source ->
        source.contentHash?.trim()?.takeIf(String::isNotEmpty)?.let { hash -> hash to source.trackId }
    }.groupBy({ it.first }, { it.second }).values.forEach { trackIds ->
        connect(
            trackIds,
            TrackMatchMethods.ContentHash,
            MATCH_CONFIDENCE_EXACT,
            priority = 6,
            allowSameSourceAccount = true,
        )
    }

    sources.mapNotNull { source ->
        source.audioFingerprint?.trim()?.takeIf(String::isNotEmpty)?.let { fingerprint ->
            fingerprint to source.trackId
        }
    }.groupBy({ it.first }, { it.second }).values.forEach { trackIds ->
        durationClusters(trackIds, candidatesById).forEach { cluster ->
            connect(
                cluster,
                TrackMatchMethods.AudioFingerprint,
                MATCH_CONFIDENCE_FINGERPRINT,
                priority = 5,
                allowSameSourceAccount = true,
            )
        }
    }

    candidates.mapNotNull { candidate ->
        candidate.track.musicBrainzRecordingId?.trim()?.lowercase()
            ?.takeIf(String::isNotEmpty)
            ?.let { recordingId -> recordingId to candidate.track.id }
    }.groupBy({ it.first }, { it.second }).values.forEach { trackIds ->
        connect(trackIds, TrackMatchMethods.MusicBrainzRecordingId, MATCH_CONFIDENCE_EXACT, priority = 4, allowSameSourceAccount = true)
    }

    candidates.mapNotNull { candidate ->
        candidate.track.isrc?.trim()?.uppercase()
            ?.takeIf(String::isNotEmpty)
            ?.let { isrc -> isrc to candidate.track.id }
    }.groupBy({ it.first }, { it.second }).values.forEach { trackIds ->
        durationClusters(trackIds, candidatesById).forEach { cluster ->
            connect(cluster, TrackMatchMethods.IsrcDuration, MATCH_CONFIDENCE_ISRC, priority = 3)
        }
    }

    candidates.mapNotNull { candidate ->
        val sourceId = candidate.track.metadataSourceId?.trim()?.takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val externalId = candidate.track.metadataExternalId?.trim()?.takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        (sourceId to externalId) to candidate.track.id
    }.groupBy({ it.first }, { it.second }).values.forEach { trackIds ->
        connect(
            trackIds,
            TrackMatchMethods.PluginExternalIdentity,
            confidence = 88,
            priority = 3,
            allowSameSourceAccount = true,
        )
    }

    candidates.mapNotNull { candidate ->
        if (TrackVersionTokens.hasAny(candidate.track.title)) return@mapNotNull null
        val key = StrictMetadataKey(
            title = candidate.track.title.normalizedTrackMatchKey(),
            artist = candidate.track.artist.normalizedTrackMatchKey(),
            album = candidate.albumName.normalizedTrackMatchKey(),
        )
        key.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() && it.album.isNotBlank() }
            ?.let { it to candidate }
    }.groupBy({ it.first }, { it.second }).values.forEach { matches ->
        durationClusters(matches.map { candidate -> candidate.track.id }, candidatesById)
            .forEach { cluster ->
                connect(
                    cluster,
                    TrackMatchMethods.StrictMetadata,
                    MATCH_CONFIDENCE_STRICT_METADATA,
                    priority = 2,
                )
            }
    }

    return candidatesById.keys.groupBy(union::root).values
        .filter { group ->
            group.size > 1 && TrackIdentityMatcher.pairwiseCompatible(group.map(snapshotsById::getValue))
        }
        .mapNotNull { trackIds ->
            val groupCandidates = trackIds.mapNotNull(candidatesById::get)
            val targetId = selectCanonicalTrackId(
                trackIds.map(snapshotsById::getValue),
                favoriteTrackIds,
                currentTrackId,
            ) ?: return@mapNotNull null
            val strongestEvidence = evidence
                .filter { item -> item.trackIds.count(trackIds::contains) >= 2 }
                .maxWithOrNull(compareBy<TrackMatchEvidence> { it.confidence }.thenBy { it.priority })
                ?: error("Merged track group has no matching evidence")
            TrackMergePlan(
                targetTrackId = targetId,
                sourceTrackIds = trackIds.filterNot { trackId -> trackId == targetId }.sorted(),
                matchMethod = strongestEvidence.method,
                matchConfidence = strongestEvidence.confidence,
                lastPlayedAt = groupCandidates.mapNotNull { it.track.lastPlayedAt }.maxOrNull(),
            )
        }.sortedBy(TrackMergePlan::targetTrackId)
}

private fun durationClusters(
    trackIds: Collection<Long>,
    candidatesById: Map<Long, TrackDeduplicationCandidate>,
): List<List<Long>> {
    val sorted = trackIds.distinct().mapNotNull { trackId ->
        candidatesById[trackId]?.track?.durationMs?.let { durationMs -> trackId to durationMs }
    }.sortedBy { (_, durationMs) -> durationMs }
    if (sorted.size < 2) return emptyList()

    val result = mutableListOf<List<Long>>()
    var cluster = mutableListOf(sorted.first())
    sorted.drop(1).forEach { item ->
        if (item.second - cluster.first().second <= DURATION_MATCH_TOLERANCE_MS) {
            cluster += item
        } else {
            if (cluster.size > 1) result += cluster.map(Pair<Long, Long>::first)
            cluster = mutableListOf(item)
        }
    }
    if (cluster.size > 1) result += cluster.map(Pair<Long, Long>::first)
    return result
}

private data class StrictMetadataKey(
    val title: String,
    val artist: String,
    val album: String,
)

private data class TrackMatchEvidence(
    val trackIds: Set<Long>,
    val method: String,
    val confidence: Int,
    val priority: Int,
)

private class TrackIdUnion(trackIds: Collection<Long>) {
    private val parents = trackIds.associateWith { trackId -> trackId }.toMutableMap()

    fun root(trackId: Long): Long {
        val parent = parents.getValue(trackId)
        if (parent == trackId) return trackId
        return root(parent).also { root -> parents[trackId] = root }
    }

    fun union(first: Long, second: Long) {
        val firstRoot = root(first)
        val secondRoot = root(second)
        if (firstRoot != secondRoot) parents[secondRoot] = firstRoot
    }
}
