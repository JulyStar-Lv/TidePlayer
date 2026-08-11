package io.github.julystar.musicapp.feature.browse.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.repository.BrowseRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val browseRepository: BrowseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    private val _events = Channel<BrowseEvent>(Channel.BUFFERED)
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init { load() }

    fun onAction(action: BrowseAction) {
        when (action) {
            BrowseAction.NavigateBack -> Unit
            BrowseAction.Retry -> load()
            is BrowseAction.NavigateToAlbum -> Unit
            is BrowseAction.NavigateToArtist -> Unit
            is BrowseAction.NavigateToGenre -> Unit
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val albums = browseRepository.loadAlbums(50)
                val artists = browseRepository.loadArtists(50)
                val genres = browseRepository.loadGenreNames(50)

                val albumItems = albums.map { album ->
                    BrowseAlbumItem(
                        id = album.id,
                        name = album.name,
                        year = album.year,
                        artwork = album.artworkTrackId?.let {
                            Artwork.LibraryTrack(trackId = it)
                        },
                        trackCount = album.trackCount,
                    )
                }

                val artistItems = artists.map { artist ->
                    BrowseArtistItem(
                        id = artist.id,
                        name = artist.name,
                        trackCount = artist.trackCount,
                    )
                }

                _state.value = BrowseState(
                    isLoading = false,
                    albums = albumItems.toPersistentList(),
                    artists = artistItems.toPersistentList(),
                    genres = genres.toPersistentList(),
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = UiMessage.Resource(UiMessageKey.BrowseLoadFailed),
                )
            }
        }
    }
}
