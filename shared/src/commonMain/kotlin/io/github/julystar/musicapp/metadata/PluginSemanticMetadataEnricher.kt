package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.plugin.management.MetadataLookupUseCase
import io.github.julystar.musicapp.plugin.management.PluginRepository
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery

/**
 * Looks up semantic metadata in deliberately cheap rounds. The returned object is the original
 * plugin candidate, including source/context fields, so callers can use it for a second lookup.
 */
class PluginSemanticMetadataEnricher(
    private val lookup: MetadataLookupUseCase,
    private val pluginRepository: PluginRepository,
    private val matcher: TrackCandidateMatcherFacade = TrackCandidateMatcherFacade,
) {
    suspend fun enrich(
        track: io.github.julystar.musicapp.database.TrackEntity,
        filenameHints: FilenameMetadata? = null,
    ): PluginSemanticMetadataResult? {
        val allowedSourceIds = pluginRepository.allSnapshot()
            .asSequence()
            .filter { plugin ->
                val capabilities = plugin.capabilities.ifEmpty { listOf("searchSongs") }
                plugin.enabled && plugin.allowAutomaticLookup && "searchSongs" in capabilities
            }
            .map { it.id }
            .toSet()
        if (allowedSourceIds.isEmpty()) return null

        val normalizedFilename = filenameHints?.normalized ?: track.title
        val rawFilename = filenameHints?.raw ?: track.title
        val rounds = listOf(
            MetaSongQuery(track.title, track.artist, durationMs = track.durationMs, pageSize = 5),
            MetaSongQuery(
                title = track.title,
                artist = filenameHints?.artist ?: track.artist,
                durationMs = track.durationMs,
                keyword = listOfNotNull(filenameHints?.artist ?: track.artist, track.title).joinToString(" "),
                pageSize = 5,
            ),
            MetaSongQuery(track.title, durationMs = track.durationMs, pageSize = 5),
            MetaSongQuery(
                title = normalizedFilename,
                durationMs = track.durationMs,
                keyword = rawFilename,
                pageSize = 5,
            ),
        ).distinctBy { listOf(it.title, it.artist, it.album, it.durationMs, it.keyword) }

        var best: TrackCandidateMatch? = null
        for (query in rounds) {
            val result = lookup.searchSongs(
                query = query,
                mode = PluginLookupMode.AUTOMATIC,
                sourceIds = allowedSourceIds,
            )
            val match = matcher.match(track, result.items, filenameHints)
            if (match != null) {
                if (match.confidence == TrackCandidateMatchConfidence.HIGH) {
                    return PluginSemanticMetadataResult(match, rounds.indexOf(query) + 1)
                }
                best = best?.takeIf { it.score >= match.score } ?: match
            }
        }
        return best?.let { PluginSemanticMetadataResult(it, rounds.size) }
    }
}

data class PluginSemanticMetadataResult(
    val match: TrackCandidateMatch,
    val rounds: Int,
) {
    val canApplyAutomatically: Boolean
        get() = match.confidence == TrackCandidateMatchConfidence.HIGH
}

fun interface TrackCandidateMatcherFacade {
    fun match(
        track: io.github.julystar.musicapp.database.TrackEntity,
        candidates: Iterable<MetaSongCandidate>,
        filenameHints: FilenameMetadata?,
    ): TrackCandidateMatch?

    companion object : TrackCandidateMatcherFacade {
        override fun match(
            track: io.github.julystar.musicapp.database.TrackEntity,
            candidates: Iterable<MetaSongCandidate>,
            filenameHints: FilenameMetadata?,
        ): TrackCandidateMatch? = TrackCandidateMatcher.match(track, candidates, filenameHints)
    }
}
