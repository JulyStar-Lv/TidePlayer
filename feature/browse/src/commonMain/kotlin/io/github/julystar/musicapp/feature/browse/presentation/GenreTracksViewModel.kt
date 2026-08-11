package io.github.julystar.musicapp.feature.browse.presentation

import androidx.lifecycle.SavedStateHandle
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

sealed interface GenreTracksEvent {
    data class ShowMessage(val message: UiMessage) : GenreTracksEvent
}

class GenreTracksViewModel(
    private val trackBrowserRepository: TrackBrowserRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(GenreTracksState())
    private val _events = Channel<GenreTracksEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    private val genre: String = savedStateHandle["genre"]!!

    init { load() }

    fun onAction(action: GenreTracksAction) {
        when (action) {
            GenreTracksAction.NavigateBack -> Unit
            GenreTracksAction.Retry -> load()
            GenreTracksAction.PlayAll -> Unit
            is GenreTracksAction.PlayTrack -> Unit
            is GenreTracksAction.DownloadTrack -> downloadTrack(action.track)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, genre = genre)
            try {
                val tracks = trackBrowserRepository.findTracksByGenre(genre, 100)
                val items = tracks.map { track ->
                    GenreTrackItem(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        albumName = track.albumName,
                        durationMs = track.durationMs,
                        mediaId = track.mediaId,
                        canDownload = track.canDownload,
                    )
                }
                _state.value = _state.value.copy(isLoading = false, tracks = items.toPersistentList())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.GenreTracksLoadFailed),
                )
            }
        }
    }

    private fun downloadTrack(track: GenreTrackItem) {
        val mediaId = track.mediaId ?: run {
            viewModelScope.launch { _events.send(GenreTracksEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded))) }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(DownloadRequest(mediaId = mediaId, title = track.title, durationMs = track.durationMs))
                _events.send(GenreTracksEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _events.send(GenreTracksEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed)))
            }
        }
    }
}
