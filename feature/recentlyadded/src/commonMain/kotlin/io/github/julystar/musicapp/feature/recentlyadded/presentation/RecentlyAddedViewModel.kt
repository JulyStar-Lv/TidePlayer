package io.github.julystar.musicapp.feature.recentlyadded.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.repository.TrackBrowserRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class RecentlyAddedViewModel(
    private val trackBrowserRepository: TrackBrowserRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RecentlyAddedState())
    private val _events = Channel<RecentlyAddedEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: RecentlyAddedAction) {
        when (action) {
            RecentlyAddedAction.NavigateBack -> Unit
            RecentlyAddedAction.Retry -> load()
            RecentlyAddedAction.PlayAll -> Unit
            is RecentlyAddedAction.PlayTrack -> Unit
            is RecentlyAddedAction.DownloadTrack -> downloadTrack(action.track)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tracks = trackBrowserRepository.findRecentlyAdded(100)
                val trackItems = tracks.map { track ->
                    RecentlyAddedTrackItem(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        albumName = track.albumName,
                        durationMs = track.durationMs,
                        mediaId = track.mediaId,
                        canDownload = track.canDownload,
                    )
                }
                _state.value = RecentlyAddedState(
                    isLoading = false,
                    tracks = trackItems.toPersistentList(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.RecentlyAddedLoadFailed),
                )
            }
        }
    }

    private fun downloadTrack(track: RecentlyAddedTrackItem) {
        val mediaId = track.mediaId ?: run {
            viewModelScope.launch {
                _events.send(RecentlyAddedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded)))
            }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(DownloadRequest(mediaId = mediaId, title = track.title, durationMs = track.durationMs))
                _events.send(RecentlyAddedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _events.send(RecentlyAddedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed)))
            }
        }
    }
}
