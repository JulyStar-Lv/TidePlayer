package io.github.julystar.musicapp.feature.queue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
    private val playbackController: PlaybackController,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val _events = Channel<QueueEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var cachedItems: List<PlayableItem> = emptyList()

    val state: StateFlow<QueueState> = combine(
        playbackController.queue,
        playbackController.state,
        favoritesRepository.favoriteTrackIds,
    ) { queue, playerState, favoriteTrackIds ->
        cachedItems = queue.items
        val isPlaying = playerState.status == PlaybackStatus.Playing
        QueueState(
            items = queue.items.mapIndexed { index, item ->
                QueueItemUi(
                    index = index,
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    trackId = item.libraryTrackId,
                    isFavorite = item.libraryTrackId?.let { it in favoriteTrackIds } == true,
                )
            }.toPersistentList(),
            currentIndex = queue.currentIndex,
            isPlaying = isPlaying,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QueueState(),
    )

    fun onAction(action: QueueAction) {
        when (action) {
            is QueueAction.PlayItem -> {
                viewModelScope.launch {
                    if (action.index in cachedItems.indices) {
                        playbackController.play(items = cachedItems, startIndex = action.index)
                    }
                }
            }
            is QueueAction.ToggleFavorite -> {
                viewModelScope.launch {
                    favoritesRepository.toggleFavorite(action.trackId)
                }
            }
            is QueueAction.MoveItem -> {
                if (
                    action.fromIndex in cachedItems.indices &&
                    action.toIndex in cachedItems.indices &&
                    action.fromIndex != action.toIndex
                ) {
                    playbackController.moveQueueItem(
                        from = action.fromIndex,
                        to = action.toIndex,
                    )
                }
            }
            is QueueAction.RemoveItem -> playbackController.removeQueueItem(action.index)
            QueueAction.ClearQueue -> playbackController.clearQueue()
        }
    }
}
