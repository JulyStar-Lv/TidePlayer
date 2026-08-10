package io.github.julystar.musicapp.feature.home.data

import io.github.julystar.musicapp.database.ListeningHistoryEntity
import io.github.julystar.musicapp.database.ListeningStatisticsDao
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.feature.home.domain.HomeStatistics
import io.github.julystar.musicapp.feature.home.domain.HomeStatisticsRepository
import io.github.julystar.musicapp.feature.home.domain.ListeningDistributionBucket
import io.github.julystar.musicapp.feature.home.domain.ListeningHistoryEntry
import io.github.julystar.musicapp.feature.home.domain.ListeningLibraryAnalysis
import io.github.julystar.musicapp.feature.home.domain.ListeningPlaybackTrack
import io.github.julystar.musicapp.feature.home.domain.ListeningStatisticsSnapshot
import io.github.julystar.musicapp.feature.home.domain.ListeningTrackStatistics
import io.github.julystar.musicapp.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class RoomHomeStatisticsRepository(
    private val listeningStatisticsDao: ListeningStatisticsDao,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    scope: CoroutineScope,
) : HomeStatisticsRepository {

    override val listeningStatistics: StateFlow<ListeningStatisticsSnapshot> =
        combine(
            listeningStatisticsDao.observeHistory(),
            trackDao.observeAll(),
        ) { history, tracks ->
            buildListeningSnapshot(history, tracks)
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(5_000),
            ListeningStatisticsSnapshot(),
        )

    override val statistics: StateFlow<HomeStatistics> =
        listeningStatistics
            .map { snapshot -> snapshot.toHomeStatistics(currentTimeMillis()) }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5_000),
                HomeStatistics(
                    totalTracksEverPlayed = 0,
                    totalListeningDurationMs = 0L,
                    tracksPlayedToday = 0,
                    mostPlayedTrackIds = emptyList(),
                ),
            )

    override suspend fun recordPlay(
        track: ListeningPlaybackTrack,
        listenedMs: Long,
        playedAtEpochMs: Long,
    ): Long {
        val entity = trackDao.get(track.trackId)
        val album = entity?.albumId?.let { metadataDao.getAlbum(it) }
        return listeningStatisticsDao.recordPlay(
            ListeningHistoryEntity(
                trackId = track.trackId,
                title = entity?.title ?: track.title,
                artist = entity?.artist
                    ?.takeIf(String::isNotBlank)
                    ?: entity?.albumArtist?.takeIf(String::isNotBlank),
                album = album?.name?.takeIf(String::isNotBlank),
                durationMs = entity?.durationMs ?: track.durationMs,
                listenedMs = listenedMs.coerceAtLeast(0L),
                playedAtEpochMs = playedAtEpochMs,
            ),
        )
    }

    override suspend fun addListenTime(historyEntryId: Long, listenedMs: Long) {
        if (historyEntryId <= 0L || listenedMs <= 0L) return
        listeningStatisticsDao.addListenTime(historyEntryId, listenedMs)
    }

    override suspend fun removeHistoryEntry(historyEntryId: Long) {
        if (historyEntryId <= 0L) return
        listeningStatisticsDao.deleteHistoryEntry(historyEntryId)
    }
}

internal fun buildListeningSnapshot(
    history: List<ListeningHistoryEntity>,
    tracks: List<TrackEntity>,
): ListeningStatisticsSnapshot {
    val historyItems = history.map(ListeningHistoryEntity::toDomain)
    val trackStatistics = historyItems
        .groupBy(ListeningHistoryEntry::trackId)
        .values
        .map { entries ->
            val latest = entries.maxBy(ListeningHistoryEntry::playedAtEpochMs)
            ListeningTrackStatistics(
                trackId = latest.trackId,
                title = latest.title,
                artist = latest.artist,
                album = latest.album,
                durationMs = latest.durationMs,
                playCount = entries.size,
                listenedMs = entries.sumOf(ListeningHistoryEntry::listenedMs),
                lastPlayedAtEpochMs = latest.playedAtEpochMs,
            )
        }
        .sortedWith(
            compareByDescending<ListeningTrackStatistics>(ListeningTrackStatistics::listenedMs)
                .thenByDescending(ListeningTrackStatistics::playCount)
                .thenByDescending(ListeningTrackStatistics::lastPlayedAtEpochMs),
        )
    return ListeningStatisticsSnapshot(
        history = historyItems,
        tracks = trackStatistics,
        libraryAnalysis = buildLibraryAnalysis(tracks),
    )
}

private fun ListeningHistoryEntity.toDomain() = ListeningHistoryEntry(
    id = id,
    trackId = trackId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    listenedMs = listenedMs.coerceAtLeast(0L),
    playedAtEpochMs = playedAtEpochMs,
)

private fun buildLibraryAnalysis(tracks: List<TrackEntity>): ListeningLibraryAnalysis =
    ListeningLibraryAnalysis(
        formatDistribution = tracks.toDistribution { track ->
            track.container
                ?.takeIf(String::isNotBlank)
                ?.uppercase()
                ?: track.codec?.takeIf(String::isNotBlank)?.uppercase()
                ?: "Unknown"
        },
        qualityDistribution = tracks.toDistribution(TrackEntity::qualityLabel),
    )

private fun List<TrackEntity>.toDistribution(
    label: (TrackEntity) -> String,
): List<ListeningDistributionBucket> = groupBy(label)
    .map { (name, tracks) ->
        ListeningDistributionBucket(label = name, trackCount = tracks.size)
    }
    .sortedWith(
        compareByDescending<ListeningDistributionBucket>(ListeningDistributionBucket::trackCount)
            .thenBy(ListeningDistributionBucket::label),
    )

private fun TrackEntity.qualityLabel(): String = when {
    lossless == true && ((sampleRate ?: 0) >= 96_000 || (bitsPerSample ?: 0) >= 24) ->
        "Hi-Res Lossless"
    lossless == true -> "Lossless"
    (bitRate ?: 0) >= 320 -> "High Quality"
    else -> "Standard"
}

private fun ListeningStatisticsSnapshot.toHomeStatistics(nowEpochMs: Long): HomeStatistics {
    val today = localDateKey(nowEpochMs)
    return HomeStatistics(
        totalTracksEverPlayed = tracks.size,
        totalListeningDurationMs = history.sumOf(ListeningHistoryEntry::listenedMs),
        tracksPlayedToday = history
            .filter { entry -> localDateKey(entry.playedAtEpochMs) == today }
            .distinctBy(ListeningHistoryEntry::trackId)
            .size,
        mostPlayedTrackIds = tracks
            .sortedWith(
                compareByDescending<ListeningTrackStatistics>(ListeningTrackStatistics::playCount)
                    .thenByDescending(ListeningTrackStatistics::listenedMs),
            )
            .take(5)
            .map(ListeningTrackStatistics::trackId),
    )
}

private fun localDateKey(epochMs: Long): String =
    Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .toString()
