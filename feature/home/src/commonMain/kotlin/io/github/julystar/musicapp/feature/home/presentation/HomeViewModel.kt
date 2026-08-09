package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.LibraryRepository
import io.github.julystar.musicapp.core.domain.repository.PlaylistRepository
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.feature.home.domain.HistoryPlayItem
import io.github.julystar.musicapp.feature.home.domain.HomeHistoryRepository
import io.github.julystar.musicapp.feature.home.domain.HomeStatisticsRepository
import io.github.julystar.musicapp.feature.home.domain.ListeningHistoryEntry
import io.github.julystar.musicapp.feature.home.domain.ListeningStatisticsSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock

private const val DAILY_PICK_TRACK_COUNT = 50

class HomeViewModel(
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    historyRepository: HomeHistoryRepository,
    statisticsRepository: HomeStatisticsRepository,
    favoritesRepository: FavoritesRepository,
) : ViewModel() {
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)

    val state = combine(
        combine(
            libraryRepository.tracks,
            libraryRepository.albums,
            libraryRepository.artists,
            playlistRepository.playlistSummaries,
            favoritesRepository.favoriteTrackIds,
        ) { tracks, albums, artists, playlists, favoriteTrackIds ->
            HomeLibraryContent(tracks, albums, artists, playlists, favoriteTrackIds)
        },
        combine(
            historyRepository.recentPlays,
            statisticsRepository.statistics,
            statisticsRepository.listeningStatistics,
        ) { history, stats, listeningStatistics ->
            HomeActivityContent(history, stats, listeningStatistics)
        },
        libraryRepository.initialLoadComplete,
    ) { libraryData, activity, initialLoadComplete ->
        val (tracks, albums, artists, playlists, favoriteTrackIds) = libraryData
        HomeState(
            isLoading = !initialLoadComplete,
            featuredAlbums = albums.map { it.toHomeAlbum() }.toPersistentList(),
            recentlyAddedAlbums = albums.map { it.toHomeAlbum() }.toPersistentList(),
            artists = artists.map { it.toHomeArtist() }.toPersistentList(),
            pinnedPlaylists = playlists.map { it.toHomePlaylist() }.toPersistentList(),
            dailyPickTracks = selectDailyPickTracks(tracks, currentLocalDate())
                .map { it.toHomeTrack(it.id in favoriteTrackIds) }
                .toPersistentList(),
            recentTracks = activity.recentPlays
                .map { it.toHomeRecentTrack(it.trackId in favoriteTrackIds) }
                .toPersistentList(),
            statistics = activity.statistics,
            listeningPreview = buildHomeListeningPreview(
                snapshot = activity.listeningStatistics,
                libraryTracks = tracks,
                favoriteTrackIds = favoriteTrackIds,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = HomeState(),
    )
    val events = _events.receiveAsFlow()

    fun onAction(action: HomeAction) {
        val event = when (action) {
            is HomeAction.PlayTrack -> return
            is HomeAction.PlayLibraryTrack -> return
            is HomeAction.PlayListeningTrack -> return
            HomeAction.PlayDailyPicks -> return
            is HomeAction.NavigateToAlbum -> HomeEvent.NavigateToAlbum(action.albumId)
            is HomeAction.NavigateToPlaylist -> HomeEvent.NavigateToPlaylist(action.playlistId)
            HomeAction.NavigateToDownloads -> HomeEvent.NavigateToDownloads
            HomeAction.NavigateToLibrary -> HomeEvent.NavigateToLibrary
            HomeAction.NavigateToSourceSettings -> HomeEvent.NavigateToSourceSettings
            HomeAction.NavigateToSearch -> HomeEvent.NavigateToSearch
            HomeAction.NavigateToListening -> HomeEvent.NavigateToListening
            HomeAction.OpenSleepTimer -> HomeEvent.OpenSleepTimer
        }
        _events.trySend(event)
    }
}

private data class HomeActivityContent(
    val recentPlays: List<HistoryPlayItem>,
    val statistics: io.github.julystar.musicapp.feature.home.domain.HomeStatistics,
    val listeningStatistics: ListeningStatisticsSnapshot,
)

private data class HomeLibraryContent(
    val tracks: List<LibraryTrackItem>,
    val albums: List<LibraryAlbumItem>,
    val artists: List<LibraryArtistItem>,
    val playlists: List<PlaylistSummary>,
    val favoriteTrackIds: Set<Long>,
)

internal fun selectDailyPickTracks(
    tracks: List<LibraryTrackItem>,
    date: LocalDate,
): List<LibraryTrackItem> = tracks
    .sortedBy(LibraryTrackItem::id)
    .shuffled(Random(date.toString().hashCode()))
    .take(DAILY_PICK_TRACK_COUNT)

private fun currentLocalDate(): LocalDate = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date

internal fun HistoryPlayItem.toHomeRecentTrack(liked: Boolean): HomeRecentTrack = HomeRecentTrack(
    id = trackId,
    mediaId = mediaId,
    durationMs = durationMs,
    title = title,
    subtitle = artist.orEmpty(),
    artwork = Artwork.LibraryTrack(trackId, allowPluginLookup = true),
    artworkIndex = artworkIndex,
    color = homeGradient(trackId).first(),
    liked = liked,
)

internal fun LibraryTrackItem.toHomeTrack(liked: Boolean): HomeRecentTrack = HomeRecentTrack(
    id = id,
    mediaId = mediaId,
    durationMs = durationMs,
    title = title,
    subtitle = artist.orEmpty(),
    artwork = Artwork.LibraryTrack(id, allowPluginLookup = true),
    artworkIndex = indexFor(id),
    color = homeGradient(id).first(),
    liked = liked,
)

internal fun buildHomeListeningPreview(
    snapshot: ListeningStatisticsSnapshot,
    libraryTracks: List<LibraryTrackItem>,
    favoriteTrackIds: Set<Long>,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): HomeListeningPreview? {
    val currentMonth = kotlin.time.Instant.fromEpochMilliseconds(nowEpochMs)
        .toLocalDateTime(timeZone)
    val libraryById = libraryTracks.associateBy(LibraryTrackItem::id)
    val monthlyRankings = snapshot.history
        .filter { entry ->
            val playedAt = kotlin.time.Instant.fromEpochMilliseconds(entry.playedAtEpochMs)
                .toLocalDateTime(timeZone)
            playedAt.year == currentMonth.year && playedAt.month == currentMonth.month
        }
        .groupBy(ListeningHistoryEntry::trackId)
        .map { (trackId, entries) ->
            val libraryTrack = libraryById[trackId]
            val latestEntry = entries.maxBy(ListeningHistoryEntry::playedAtEpochMs)
            HomeListeningRankedTrack(
                track = libraryTrack?.toHomeTrack(trackId in favoriteTrackIds)
                    ?: latestEntry.toHomeListeningTrack(trackId in favoriteTrackIds),
                playCount = entries.size,
                listenedMs = entries.sumOf(ListeningHistoryEntry::listenedMs),
            )
        }

    if (monthlyRankings.isEmpty()) return null
    return HomeListeningPreview(
        durationRanking = monthlyRankings
            .filter { it.listenedMs > 0L }
            .sortedByDescending(HomeListeningRankedTrack::listenedMs)
            .take(3)
            .toPersistentList(),
        playCountRanking = monthlyRankings
            .sortedWith(
                compareByDescending<HomeListeningRankedTrack>(HomeListeningRankedTrack::playCount)
                    .thenByDescending(HomeListeningRankedTrack::listenedMs),
            )
            .take(3)
            .toPersistentList(),
    )
}

private fun ListeningHistoryEntry.toHomeListeningTrack(liked: Boolean): HomeRecentTrack = HomeRecentTrack(
    id = trackId,
    mediaId = null,
    durationMs = durationMs,
    title = title,
    subtitle = artist.orEmpty(),
    artwork = Artwork.LibraryTrack(trackId, allowPluginLookup = true),
    artworkIndex = indexFor(trackId),
    color = homeGradient(trackId).first(),
    liked = liked,
)

internal fun LibraryAlbumItem.toHomeAlbum(): HomeFeaturedAlbum = HomeFeaturedAlbum(
    id = id,
    title = name,
    subtitle = artist.orEmpty(),
    artwork = Artwork.LibraryAlbum(id),
    artworkIndex = indexFor(id),
    colors = homeGradient(id),
)

private fun LibraryArtistItem.toHomeArtist(): HomeArtist = HomeArtist(
    name = name,
    followers = "",
    initials = name
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .joinToString(separator = "") { it.first().uppercase() }
        .ifBlank { "?" },
    artworkIndex = indexFor(id),
    colors = homeGradient(id),
)

private fun PlaylistSummary.toHomePlaylist(): HomePlaylist = HomePlaylist(
    id = id,
    title = title,
    description = "",
    meta = "",
    trackCount = musicCount,
    durationMs = durationMs,
    artworkIndex = indexFor(id),
    colors = homeGradient(id),
)

private fun indexFor(id: Long): Int = ((id % 8L + 8L) % 8L).toInt() + 1

private fun homeGradient(id: Long): ImmutableList<Color> {
    val colors = listOf(
        DesignPalette.BrandPink,
        DesignPalette.Secondary,
        DesignPalette.SupportBlue,
        DesignPalette.SupportGreen,
        DesignPalette.SupportOrange,
        DesignPalette.SupportYellow,
    )
    val startIndex = ((id % colors.size + colors.size) % colors.size).toInt()
    return persistentListOf(colors[startIndex], colors[(startIndex + 1) % colors.size])
}
