package io.github.julystar.musicapp.feature.album.presentation

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
fun AlbumRoot(
    onNavigateBack: () -> Unit,
    viewModel: AlbumViewModel = koinViewModel(),
) {
    val playbackController = koinInject<PlaybackController>()
    val favoritesRepository = koinInject<FavoritesRepository>()
    val toastRepository = koinInject<ToastRepository>()
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val playerState by playbackController.state.collectAsState()
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AlbumEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    AlbumScreen(
        state = state,
        currentPlayingTrackId = playerState.currentItem?.libraryTrackId,
        favoriteTrackIds = favoriteTrackIds,
        onToggleFavorite = { trackId ->
            coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
        },
        onAction = { action ->
            when (action) {
                AlbumAction.NavigateBack -> onNavigateBack()
                AlbumAction.Retry -> viewModel.onAction(action)
                AlbumAction.PlayAll -> {
                    val items = state.tracks.map { it.toPlayableItem(state.artist) }
                    coroutineScope.launch {
                        playbackController.play(items = items)
                    }
                }
                is AlbumAction.PlayTrack -> {
                    val items = state.tracks.map { it.toPlayableItem(state.artist) }
                    val startIndex = state.tracks.indexOfFirst { it.id == action.trackId }
                    if (startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(items = items, startIndex = startIndex)
                        }
                    }
                }
                is AlbumAction.DownloadTrack -> viewModel.onAction(action)
            }
        },
    )
}

private fun AlbumTrackItem.toPlayableItem(albumArtist: String): PlayableItem = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = artist?.takeIf { it.isNotBlank() } ?: albumArtist.ifBlank { null },
    durationMs = durationMs,
    libraryTrackId = id,
)
