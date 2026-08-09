package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.presentation.components.QualityBadgeType
import io.github.julystar.musicapp.feature.home.domain.HomeStatistics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val featuredAlbums: ImmutableList<HomeFeaturedAlbum> = persistentListOf(),
    val recentlyAddedAlbums: ImmutableList<HomeFeaturedAlbum> = persistentListOf(),
    val artists: ImmutableList<HomeArtist> = persistentListOf(),
    val pinnedPlaylists: ImmutableList<HomePlaylist> = persistentListOf(),
    val dailyPickTracks: ImmutableList<HomeRecentTrack> = persistentListOf(),
    val recentTracks: ImmutableList<HomeRecentTrack> = persistentListOf(),
    val statistics: HomeStatistics? = null,
    val listeningPreview: HomeListeningPreview? = null,
)

internal val HomeState.shouldShowEmptyState: Boolean
    get() = !isLoading && dailyPickTracks.isEmpty()

internal val HomeState.shouldShowEmptyStateOnly: Boolean
    get() = shouldShowEmptyState

@Immutable
data class HomeFeaturedAlbum(
    val id: Long,
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val artworkIndex: Int,
    val colors: ImmutableList<Color>,
)

@Immutable
data class HomeArtist(
    val name: String,
    val followers: String,
    val initials: String,
    val artworkIndex: Int,
    val colors: ImmutableList<Color>,
)

@Immutable
data class HomePlaylist(
    val id: Long,
    val title: String,
    val description: String,
    val meta: String,
    val trackCount: Long = 0L,
    val durationMs: Long = 0L,
    val artworkIndex: Int,
    val colors: ImmutableList<Color>,
)

@Immutable
data class HomeRecentTrack(
    val id: Long,
    val mediaId: MediaId?,
    val durationMs: Long?,
    val title: String,
    val subtitle: String,
    val artwork: Artwork,
    val artworkIndex: Int,
    val color: Color,
    val qualityBadge: QualityBadgeType? = null,
    val liked: Boolean = false,
)

@Immutable
data class HomeListeningPreview(
    val durationRanking: ImmutableList<HomeListeningRankedTrack> = persistentListOf(),
    val playCountRanking: ImmutableList<HomeListeningRankedTrack> = persistentListOf(),
)

@Immutable
data class HomeListeningRankedTrack(
    val track: HomeRecentTrack,
    val playCount: Int,
    val listenedMs: Long,
)

enum class HomeListeningRanking {
    Duration,
    PlayCount,
}

sealed interface HomeAction {
    data class PlayTrack(val trackId: Long) : HomeAction
    data class PlayLibraryTrack(val trackId: Long) : HomeAction
    data class PlayListeningTrack(
        val trackId: Long,
        val ranking: HomeListeningRanking,
    ) : HomeAction
    data object PlayDailyPicks : HomeAction
    data class NavigateToAlbum(val albumId: Long) : HomeAction
    data class NavigateToPlaylist(val playlistId: Long) : HomeAction
    data object NavigateToDownloads : HomeAction
    data object NavigateToLibrary : HomeAction
    data object NavigateToSourceSettings : HomeAction
    data object NavigateToSearch : HomeAction
    data object NavigateToListening : HomeAction
    data object OpenSleepTimer : HomeAction
}

sealed interface HomeEvent {
    data class NavigateToAlbum(val albumId: Long) : HomeEvent
    data class NavigateToPlaylist(val playlistId: Long) : HomeEvent
    data object NavigateToDownloads : HomeEvent
    data object NavigateToLibrary : HomeEvent
    data object NavigateToSourceSettings : HomeEvent
    data object NavigateToSearch : HomeEvent
    data object NavigateToListening : HomeEvent
    data object OpenSleepTimer : HomeEvent
}
