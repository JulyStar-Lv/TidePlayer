package io.github.julystar.musicapp.feature.recentlyadded.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.launchPlaybackUiAction
import io.github.julystar.musicapp.service.playback.domain.playbackUiRequest
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecentlyAddedRoot(
    onNavigateBack: () -> Unit,
    viewModel: RecentlyAddedViewModel = koinViewModel(),
) {
    val playbackController = koinInject<PlaybackController>()
    val toastRepository = koinInject<ToastRepository>()
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecentlyAddedEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    RecentlyAddedScreen(
        state = state,
        onAction = { action ->
            when (action) {
                RecentlyAddedAction.NavigateBack -> onNavigateBack()
                RecentlyAddedAction.Retry -> viewModel.onAction(action)
                RecentlyAddedAction.PlayAll -> {
                    val items = state.tracks.map(RecentlyAddedTrackItem::toPlayableItem)
                    playbackUiRequest(items)?.let { request ->
                        coroutineScope.launchPlaybackUiAction(
                            onFailure = { toastRepository.emit(UiMessage.Resource(UiMessageKey.SourceUnavailable)) },
                        ) { playbackController.play(request.items, request.startIndex) }
                    }
                }
                is RecentlyAddedAction.PlayTrack -> {
                    val items = state.tracks.map(RecentlyAddedTrackItem::toPlayableItem)
                    playbackUiRequest(items, action.trackId)?.let { request ->
                        coroutineScope.launchPlaybackUiAction(
                            onFailure = { toastRepository.emit(UiMessage.Resource(UiMessageKey.SourceUnavailable)) },
                        ) { playbackController.play(request.items, request.startIndex) }
                    }
                }
                is RecentlyAddedAction.DownloadTrack -> viewModel.onAction(action)
            }
        },
    )
}

private fun RecentlyAddedTrackItem.toPlayableItem() = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = artist,
    durationMs = durationMs,
    libraryTrackId = id,
)
