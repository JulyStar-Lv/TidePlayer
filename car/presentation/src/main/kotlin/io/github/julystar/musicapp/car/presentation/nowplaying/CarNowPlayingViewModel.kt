package io.github.julystar.musicapp.car.presentation.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.service.playback.domain.NowPlayingRepository
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CarNowPlayingUiState(
    val player: PlayerState = PlayerState(),
    val position: PlaybackPosition = PlaybackPosition.Zero,
    val queue: PlaybackQueue = PlaybackQueue.Empty,
    val trackInfo: CurrentTrackInfo? = null,
    val favoriteTrackIds: Set<Long> = emptySet(),
) {
    val currentTrackId: Long?
        get() = player.currentItem?.libraryTrackId

    val isFavorite: Boolean
        get() = currentTrackId?.let(favoriteTrackIds::contains) == true
}

sealed interface CarNowPlayingAction {
    data object PlayPause : CarNowPlayingAction
    data object Pause : CarNowPlayingAction
    data object Previous : CarNowPlayingAction
    data object Next : CarNowPlayingAction
    data class Seek(val positionMs: Long) : CarNowPlayingAction
    data object ToggleFavorite : CarNowPlayingAction
    data object CyclePlaybackMode : CarNowPlayingAction
    data class PlayQueueItem(val index: Int) : CarNowPlayingAction
    data class RemoveQueueItem(val index: Int) : CarNowPlayingAction
    data class MoveQueueItem(val from: Int, val to: Int) : CarNowPlayingAction
}

class CarNowPlayingViewModel(
    private val playbackController: PlaybackController,
    nowPlayingRepository: NowPlayingRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    val state: StateFlow<CarNowPlayingUiState> = combine(
        playbackController.state,
        playbackController.position,
        playbackController.queue,
        nowPlayingRepository.currentTrackInfo,
        favoritesRepository.favoriteTrackIds,
    ) { player, position, queue, trackInfo, favoriteTrackIds ->
        CarNowPlayingUiState(player, position, queue, trackInfo, favoriteTrackIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CarNowPlayingUiState(
            player = playbackController.state.value,
            position = playbackController.position.value,
            queue = playbackController.queue.value,
            trackInfo = nowPlayingRepository.currentTrackInfo.value,
        ),
    )

    fun onAction(action: CarNowPlayingAction) {
        when (action) {
            CarNowPlayingAction.PlayPause -> playbackController.togglePlayPause()
            CarNowPlayingAction.Pause -> playbackController.pause()
            CarNowPlayingAction.Previous -> playbackController.skipPrevious()
            CarNowPlayingAction.Next -> playbackController.skipNext()
            is CarNowPlayingAction.Seek -> playbackController.seekTo(action.positionMs)
            CarNowPlayingAction.ToggleFavorite -> state.value.currentTrackId?.let { trackId ->
                viewModelScope.launch { favoritesRepository.toggleFavorite(trackId) }
            }
            CarNowPlayingAction.CyclePlaybackMode -> {
                val nextMode = state.value.player.nextCarPlaybackMode()
                playbackController.setShuffle(nextMode.shuffleEnabled)
                playbackController.setRepeatMode(nextMode.repeatMode)
            }
            is CarNowPlayingAction.PlayQueueItem -> {
                val queue = state.value.queue
                if (action.index in queue.items.indices) {
                    viewModelScope.launch { playbackController.play(queue.items, action.index) }
                }
            }
            is CarNowPlayingAction.RemoveQueueItem -> playbackController.removeQueueItem(action.index)
            is CarNowPlayingAction.MoveQueueItem -> playbackController.moveQueueItem(action.from, action.to)
        }
    }
}
