package io.github.julystar.musicapp.feature.artist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.launchPlaybackUiAction
import io.github.julystar.musicapp.service.playback.domain.playbackUiRequest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArtistRoot(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (albumId: Long) -> Unit,
    viewModel: ArtistViewModel = koinViewModel(),
) {
    val playbackController = koinInject<PlaybackController>()
    val toastRepository = koinInject<ToastRepository>()
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val playerState by playbackController.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArtistEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    ArtistScreen(
        state = state,
        currentPlayingTrackId = playerState.currentItem?.libraryTrackId,
        onAction = { action ->
            when (action) {
                ArtistAction.NavigateBack -> onNavigateBack()
                ArtistAction.Retry -> viewModel.onAction(action)
                ArtistAction.PlayAll -> {
                    val items = state.tracks.map { it.toPlayableItem(state.name) }
                    playbackUiRequest(items)?.let { request ->
                        coroutineScope.launchPlaybackUiAction(
                            onFailure = { toastRepository.emit(sourceUnavailableMessage()) },
                        ) { playbackController.play(request.items, request.startIndex) }
                    }
                }
                is ArtistAction.PlayTrack -> {
                    val items = state.tracks.map { it.toPlayableItem(state.name) }
                    playbackUiRequest(items, action.trackId)?.let { request ->
                        coroutineScope.launchPlaybackUiAction(
                            onFailure = { toastRepository.emit(sourceUnavailableMessage()) },
                        ) { playbackController.play(request.items, request.startIndex) }
                    }
                }
                is ArtistAction.NavigateToAlbum -> onNavigateToAlbum(action.albumId)
                is ArtistAction.DownloadTrack -> viewModel.onAction(action)
            }
        },
    )
}

private fun ArtistTrackItem.toPlayableItem(artistName: String): PlayableItem = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = artistName.ifBlank { null },
    durationMs = durationMs,
    libraryTrackId = id,
)

private fun sourceUnavailableMessage() =
    io.github.julystar.musicapp.core.domain.repository.UiMessage.Resource(
        io.github.julystar.musicapp.core.domain.repository.UiMessageKey.SourceUnavailable,
    )
