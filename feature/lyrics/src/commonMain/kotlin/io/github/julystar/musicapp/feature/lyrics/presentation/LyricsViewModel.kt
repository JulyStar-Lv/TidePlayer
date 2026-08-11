package io.github.julystar.musicapp.feature.lyrics.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.DomainLyrics
import io.github.julystar.musicapp.core.domain.repository.LyricsRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LyricsViewModel(
    private val lyricsRepository: LyricsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val trackId: Long = savedStateHandle["id"]!!
    private val _state = MutableStateFlow(LyricsState(trackId = trackId))
    private val _events = Channel<LyricsEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: LyricsAction) {
        when (action) {
            LyricsAction.NavigateBack -> Unit
            LyricsAction.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val lyrics: DomainLyrics = lyricsRepository.loadLyrics(trackId)

                _state.value = LyricsState(
                    trackId = trackId,
                    isLoading = false,
                    trackTitle = lyrics.trackTitle,
                    trackArtist = lyrics.trackArtist,
                    lines = lyrics.lines.toPersistentList(),
                    format = lyrics.format,
                    synchronized = lyrics.synchronized,
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.LyricsLoadFailed),
                )
            }
        }
    }
}
