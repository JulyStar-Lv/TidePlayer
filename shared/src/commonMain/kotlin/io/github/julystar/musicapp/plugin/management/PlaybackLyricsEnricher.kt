package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.core.data.shouldLookupPreferredExternalLyrics
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

class PlaybackLyricsEnricher(
    private val lookup: MetadataLookupUseCase,
    private val metadataDao: MetadataDao,
    private val trackDao: TrackDao,
    private val settingsRepository: SettingsRepository,
    private val pluginRepository: PluginRepository,
    private val timeoutMs: Long = DEFAULT_PLAYBACK_LYRICS_LOOKUP_TIMEOUT_MS,
) {
    private val stateMutex = Mutex()
    private val inFlight = mutableSetOf<LyricsAttemptKey>()
    private val attempted = mutableSetOf<LyricsAttemptKey>()

    /**
     * Lets a changed lyric-source preference retry a lookup that previously completed without
     * finding a preferred external lyric.
     */
    suspend fun resetAttempt(trackId: Long) {
        stateMutex.withLock {
            attempted.removeAll { it.trackId == trackId }
        }
    }

    suspend fun enrich(
        trackId: Long,
        matchedCandidate: MetaSongCandidate? = null,
        canonicalTrackId: Long? = null,
    ): Boolean {
        val effectiveTrackId = canonicalTrackId ?: trackId
        val settings = settingsRepository.settings.first().lyrics
        val candidates = metadataDao.getLyricsCandidates(effectiveTrackId)
        if (!candidates.shouldLookupPreferredExternalLyrics(settings)) return false
        val attemptKey = LyricsAttemptKey(
            trackId = effectiveTrackId,
            sourceId = matchedCandidate?.sourceId,
            externalId = matchedCandidate?.id,
            settingsKey = buildString {
                append(settings.sourceMode.name)
                append(':')
                append(settings.sourcePriority.joinToString(",") { it.name })
            },
        )

        val acquired = stateMutex.withLock {
            if (attemptKey in inFlight || attemptKey in attempted) {
                false
            } else {
                inFlight += attemptKey
                true
            }
        }
        if (!acquired) return false

        var completed = false
        return try {
            val updated = withTimeoutOrNull(timeoutMs.coerceAtLeast(1)) {
                lookupAndPersist(effectiveTrackId, matchedCandidate)
            } ?: false
            completed = true
            updated
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            completed = true
            false
        } finally {
            stateMutex.withLock {
                inFlight -= attemptKey
                if (completed) attempted += attemptKey
            }
        }
    }

    private suspend fun lookupAndPersist(trackId: Long, matchedCandidate: MetaSongCandidate?): Boolean {
        val track = trackDao.get(trackId) ?: return false
        val artist = metadataDao.artistNamesForTrack(trackId)
            .joinToString(" / ")
            .ifBlank { track.artist.orEmpty() }
            .takeIf(String::isNotBlank)
        val album = track.albumId?.let { albumId -> metadataDao.getAlbum(albumId)?.name }
        val query = MetaSongQuery(
            title = track.title,
            artist = artist,
            album = album,
            durationMs = track.durationMs,
            pageSize = PLAYBACK_LYRICS_RESULTS_PER_SOURCE,
        )
        val plugins = pluginRepository.allSnapshot()
        val lyricsSourceIds = plugins
            .filter { plugin ->
                val capabilities = plugin.capabilities.ifEmpty { listOf("searchSongs") }
                plugin.enabled &&
                    plugin.allowAutomaticLookup &&
                    "getLyrics" in capabilities
            }
            .mapTo(mutableSetOf(), PluginSummary::id)
        val searchSourceIds = plugins
            .filter { plugin -> plugin.id in lyricsSourceIds && "searchSongs" in plugin.capabilities }
            .mapTo(mutableSetOf(), PluginSummary::id)
        val candidate = resolveLyricsSongCandidate(matchedCandidate, lyricsSourceIds) {
            searchSourceIds.takeIf(Set<String>::isNotEmpty)?.let {
                findBestLyricsCandidate(query) { searchQuery ->
                    lookup.searchSongs(
                        query = searchQuery,
                        mode = PluginLookupMode.AUTOMATIC,
                        sourceIds = searchSourceIds,
                    ).items
                }
            }
        }
        val matchedLyrics = candidate?.let { song ->
            lookup.getLyricsCandidates(
                candidate = song,
                mode = PluginLookupMode.AUTOMATIC,
            ).value.orEmpty().bestMatch(query)?.lyrics
        }
        val independentSourceIds = plugins
            .filter { plugin ->
                val capabilities = plugin.capabilities.ifEmpty { listOf("searchSongs") }
                plugin.enabled &&
                    plugin.allowAutomaticLookup &&
                    plugin.apiVersion >= 4 &&
                    "getLyrics" in capabilities &&
                    "searchSongs" !in capabilities
            }
            .mapTo(mutableSetOf(), PluginSummary::id)
        val lyrics = matchedLyrics ?: lookup.searchIndependentLyricsCandidates(
            query = query,
            mode = PluginLookupMode.AUTOMATIC,
            sourceIds = independentSourceIds,
        ).items.bestMatch(query)?.lyrics ?: return false
        val entity = lyrics.toEntity(trackId, currentTimeMillis()) ?: return false
        metadataDao.upsertLyrics(listOf(entity))
        return true
    }
}

internal data class LyricsAttemptKey(
    val trackId: Long,
    val sourceId: String?,
    val externalId: String?,
    val settingsKey: String? = null,
)

internal suspend fun resolveLyricsSongCandidate(
    matchedCandidate: MetaSongCandidate?,
    allowedLyricsSourceIds: Set<String>,
    search: suspend () -> MetaSongCandidate?,
): MetaSongCandidate? = if (matchedCandidate != null) {
    matchedCandidate.takeIf { it.sourceId.isNullOrBlank() || it.sourceId in allowedLyricsSourceIds }
} else {
    search()
}

private fun List<MetaLyricsCandidate>.bestMatch(query: MetaSongQuery): MetaLyricsCandidate? =
    mapNotNull { candidate ->
        MetaSongCandidate(
            id = candidate.id,
            title = candidate.title,
            artist = candidate.artist,
            album = candidate.album,
            date = candidate.date,
            sourceId = candidate.sourceId,
        ).matchScore(query)?.let { score -> candidate to score }
    }.maxByOrNull { (_, score) -> score }?.first

internal suspend fun findBestLyricsCandidate(
    query: MetaSongQuery,
    search: suspend (MetaSongQuery) -> List<MetaSongCandidate>,
): MetaSongCandidate? {
    fun bestMatch(candidates: List<MetaSongCandidate>): MetaSongCandidate? = candidates
        .mapNotNull { value ->
            value.matchScore(query)?.let { score -> value to score }
        }
        .maxByOrNull { (_, score) -> score }
        ?.first

    return bestMatch(search(query))
        ?: bestMatch(search(query.copy(keyword = query.title)))
}

internal fun MetaSongCandidate.matchScore(query: MetaSongQuery): Int? {
    if (title.matchTitleKey() != query.title.matchTitleKey()) return null

    var score = 100
    val expectedArtist = query.artist?.matchKey().orEmpty()
    val candidateArtist = artist?.matchKey().orEmpty()
    if (expectedArtist.isNotEmpty() && candidateArtist.isNotEmpty()) {
        if (
            expectedArtist != candidateArtist &&
            expectedArtist !in candidateArtist &&
            candidateArtist !in expectedArtist
        ) {
            return null
        }
        score += if (expectedArtist == candidateArtist) 30 else 15
    }

    val expectedAlbum = query.album?.matchKey().orEmpty()
    val candidateAlbum = album?.matchKey().orEmpty()
    if (expectedAlbum.isNotEmpty() && expectedAlbum == candidateAlbum) score += 10

    val expectedDuration = query.durationMs
    val candidateDuration = durationMs
    if (expectedDuration != null && candidateDuration != null) {
        val difference = abs(expectedDuration - candidateDuration)
        if (difference > MAX_PLAYBACK_LYRICS_DURATION_DIFFERENCE_MS) return null
        score += when {
            difference <= 1_000 -> 30
            difference <= 2_000 -> 15
            else -> 5
        }
    }
    return score
}

private fun String.matchKey(): String =
    lowercase().filter(Char::isLetterOrDigit)

private fun String.matchTitleKey(): String =
    replace(FEATURED_ARTIST_SUFFIX, "").matchKey()

private val FEATURED_ARTIST_SUFFIX = Regex(
    """\s*[\(\[]\s*(?:feat\.?|ft\.?)\s+.+?[\)\]]\s*$""",
    RegexOption.IGNORE_CASE,
)

private const val PLAYBACK_LYRICS_RESULTS_PER_SOURCE = 3
private const val MAX_PLAYBACK_LYRICS_DURATION_DIFFERENCE_MS = 3_000L
private const val DEFAULT_PLAYBACK_LYRICS_LOOKUP_TIMEOUT_MS = 15_000L
