package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaylistRoot(
    scaffoldPadding: PaddingValues,
    onNavigateBack: () -> Unit,
    onNavigateToImport: () -> Unit,
    playlistViewModel: PlaylistVM = koinViewModel(),
    editPlaylistViewModel: EditPlaylistVM = koinViewModel(),
) {
    val playbackController = koinInject<PlaybackController>()
    val favoritesRepository = koinInject<FavoritesRepository>()
    val toastRepository = koinInject<ToastRepository>()
    val coroutineScope = rememberCoroutineScope()
    val state by playlistViewModel.state.collectAsState()
    val playerState by playbackController.state.collectAsState()
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())

    LaunchedEffect(playlistViewModel) {
        playlistViewModel.events.collect { event ->
            when (event) {
                is PlaylistEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    PlaylistScreen(
        state = state,
        currentPlayingTrackId = playerState.currentItem?.libraryTrackId,
        favoriteTrackIds = favoriteTrackIds,
        scaffoldPadding = scaffoldPadding,
        onToggleFavorite = { trackId ->
            coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
        },
        onAction = { action ->
            when (action) {
                PlaylistAction.NavigateBack -> onNavigateBack()
                PlaylistAction.EditPlaylist -> editPlaylistViewModel.openModal()
                PlaylistAction.ImportTracks -> {
                    playlistViewModel.onAction(action)
                    onNavigateToImport()
                }
                PlaylistAction.ConfirmRemovePlaylist -> {
                    playlistViewModel.onAction(action)
                    onNavigateBack()
                }
                PlaylistAction.PlayAll -> {
                    val items = state.tracks.map { track ->
                        track.toPlayableItem(state.playlistId)
                    }
                    if (items.isNotEmpty()) {
                        coroutineScope.launch {
                            playbackController.play(items = items)
                        }
                    }
                }
                is PlaylistAction.PlayTrack -> {
                    val items = state.tracks.map { track ->
                        track.toPlayableItem(state.playlistId)
                    }
                    val startIndex = state.tracks
                        .indexOfFirst { track -> track.id == action.trackId }
                        .coerceAtLeast(0)
                    if (items.isNotEmpty()) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = items,
                                startIndex = startIndex,
                            )
                        }
                    }
                }
                else -> playlistViewModel.onAction(action)
            }
        },
    )
}

private fun PlaylistTrackItem.toPlayableItem(playlistId: Long): PlayableItem {
    return PlayableItem(
        mediaId = mediaId,
        title = title,
        artist = artist?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        libraryTrackId = id,
        libraryPlaylistId = playlistId,
    )
}
