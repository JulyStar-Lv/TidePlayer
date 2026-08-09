package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ListeningRoot(
    onNavigateBack: () -> Unit,
    viewModel: ListeningViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playbackController = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()

    ListeningScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ListeningAction.NavigateBack -> onNavigateBack()
                is ListeningAction.PlayTrack -> {
                    val track = (state.durationRanking + state.playCountRanking)
                        .firstOrNull { it.trackId == action.trackId }
                        ?: state.recentHistory
                            .firstOrNull { it.trackId == action.trackId }
                            ?.let { history ->
                                ListeningRankedTrack(
                                    trackId = history.trackId,
                                    mediaId = history.mediaId,
                                    title = history.title,
                                    artist = history.artist,
                                    album = null,
                                    playCount = 1,
                                    listenedMs = history.listenedMs,
                                )
                            }
                    if (track != null) {
                        scope.launch {
                            playbackController.play(
                                items = listOf(track.toPlayableItem()),
                                startIndex = 0,
                            )
                        }
                    }
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

private fun ListeningRankedTrack.toPlayableItem() = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = artist,
    libraryTrackId = trackId,
    libraryPlaylistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
)
