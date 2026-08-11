package io.github.julystar.musicapp.feature.recentlyplayed.presentation

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

class RecentlyPlayedViewModel(
    private val trackBrowserRepository: TrackBrowserRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RecentlyPlayedState())
    private val _events = Channel<RecentlyPlayedEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: RecentlyPlayedAction) {
        when (action) {
            RecentlyPlayedAction.NavigateBack -> Unit
            RecentlyPlayedAction.Retry -> load()
            RecentlyPlayedAction.PlayAll -> Unit
            is RecentlyPlayedAction.PlayTrack -> Unit
            is RecentlyPlayedAction.DownloadTrack -> downloadTrack(action.track)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tracks = trackBrowserRepository.findRecentlyPlayed(100)
                val trackItems = tracks.map { track ->
                    RecentlyPlayedTrackItem(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        albumName = track.albumName,
                        durationMs = track.durationMs,
                        mediaId = track.mediaId,
                        canDownload = track.canDownload,
                    )
                }
                _state.value = RecentlyPlayedState(
                    isLoading = false,
                    tracks = trackItems.toPersistentList(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.RecentlyPlayedLoadFailed),
                )
            }
        }
    }

    private fun downloadTrack(track: RecentlyPlayedTrackItem) {
        val mediaId = track.mediaId ?: run {
            viewModelScope.launch {
                _events.send(RecentlyPlayedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded)))
            }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(DownloadRequest(mediaId = mediaId, title = track.title, durationMs = track.durationMs))
                _events.send(RecentlyPlayedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _events.send(RecentlyPlayedEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed)))
            }
        }
    }
}
