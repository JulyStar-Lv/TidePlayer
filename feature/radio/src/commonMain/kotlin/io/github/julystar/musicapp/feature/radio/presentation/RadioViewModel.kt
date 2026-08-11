package io.github.julystar.musicapp.feature.radio.presentation

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

class RadioViewModel(
    private val trackBrowserRepository: TrackBrowserRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RadioState())
    private val _events = Channel<RadioEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init { generate() }

    fun onAction(action: RadioAction) {
        when (action) {
            RadioAction.NavigateBack -> Unit
            RadioAction.Refresh -> generate()
            RadioAction.PlayAll -> Unit
            is RadioAction.PlayTrack -> Unit
            is RadioAction.DownloadTrack -> downloadTrack(action.track)
        }
    }

    private fun generate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tracks = trackBrowserRepository.findRecentlyAdded(200)
                val shuffled = tracks.shuffled().take(30)
                val trackItems = shuffled.map { track ->
                    RadioTrackItem(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        albumName = track.albumName,
                        durationMs = track.durationMs,
                        mediaId = track.mediaId,
                        canDownload = track.canDownload,
                    )
                }
                _state.value = RadioState(isLoading = false, tracks = trackItems.toPersistentList())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.RadioGenerationFailed),
                )
            }
        }
    }

    private fun downloadTrack(track: RadioTrackItem) {
        val mediaId = track.mediaId ?: run {
            viewModelScope.launch { _events.send(RadioEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded))) }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(DownloadRequest(mediaId = mediaId, title = track.title, durationMs = track.durationMs))
                _events.send(RadioEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _events.send(RadioEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed)))
            }
        }
    }
}
