package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

val LocalPreloadedHomeViewModel = staticCompositionLocalOf<HomeViewModel?> { null }

@Composable
fun HomeRoot(
    scaffoldPadding: PaddingValues,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSourceSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToListening: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onOpenSleepTimer: () -> Unit,
    viewModel: HomeViewModel? = null,
) {
    val activeViewModel = viewModel ?: LocalPreloadedHomeViewModel.current ?: koinViewModel()
    val state by activeViewModel.state.collectAsState()
    val playbackController = koinInject<PlaybackController>()
    val playbackState by playbackController.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(activeViewModel) {
        activeViewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToAlbum -> onNavigateToAlbum(event.albumId)
                is HomeEvent.NavigateToPlaylist -> onNavigateToPlaylist(event.playlistId)
                HomeEvent.NavigateToDownloads -> onNavigateToDownloads()
                HomeEvent.NavigateToLibrary -> onNavigateToLibrary()
                HomeEvent.NavigateToSourceSettings -> onNavigateToSourceSettings()
                HomeEvent.NavigateToSearch -> onNavigateToSearch()
                HomeEvent.NavigateToListening -> onNavigateToListening()
                HomeEvent.OpenSleepTimer -> onOpenSleepTimer()
            }
        }
    }

    HomeDesignScreen(
        scaffoldPadding = scaffoldPadding,
        state = state,
        currentMiniPlayerTitle = playbackState.currentItem?.title?.takeIf { it.isNotBlank() },
        onAction = { action ->
            when (action) {
                HomeAction.PlayDailyPicks -> {
                    val dailyPicks = state.dailyPickTracks
                    if (dailyPicks.isNotEmpty()) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = dailyPicks.map { it.toPlayableItem() },
                                startIndex = 0,
                            )
                        }
                    }
                }
                is HomeAction.PlayTrack -> {
                    val startIndex = state.recentTracks.indexOfFirst { it.id == action.trackId }
                    if (startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = state.recentTracks.map { it.toPlayableItem() },
                                startIndex = startIndex,
                            )
                        }
                    }
                }
                is HomeAction.PlayLibraryTrack -> {
                    val startIndex = state.dailyPickTracks.indexOfFirst { it.id == action.trackId }
                    if (startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = state.dailyPickTracks.map { it.toPlayableItem() },
                                startIndex = startIndex,
                            )
                        }
                    }
                }
                is HomeAction.PlayListeningTrack -> {
                    val ranking = when (action.ranking) {
                        HomeListeningRanking.Duration -> state.listeningPreview?.durationRanking
                        HomeListeningRanking.PlayCount -> state.listeningPreview?.playCountRanking
                    }.orEmpty()
                    val startIndex = ranking.indexOfFirst { it.track.id == action.trackId }
                    if (startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = ranking.map { it.track.toPlayableItem() },
                                startIndex = startIndex,
                            )
                        }
                    }
                }
                else -> activeViewModel.onAction(action)
            }
        },
    )
}

private fun HomeRecentTrack.toPlayableItem(): PlayableItem = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = subtitle.ifBlank { null },
    durationMs = durationMs,
    libraryTrackId = id,
    libraryPlaylistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
)
