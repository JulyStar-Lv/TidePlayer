package io.github.julystar.musicapp.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.feature.search.domain.SearchHistoryRepository
import io.github.julystar.musicapp.feature.search.domain.SearchLibraryUseCase
import io.github.julystar.musicapp.feature.search.domain.SearchSourceAccountProvider
import io.github.julystar.musicapp.feature.search.domain.SearchSuggestionsUseCase
import io.github.julystar.musicapp.feature.search.domain.SearchAlbumItem
import io.github.julystar.musicapp.feature.search.domain.SearchArtistItem
import io.github.julystar.musicapp.feature.search.domain.SearchTrackItem
import io.github.julystar.musicapp.feature.search.domain.mergeSearchSuggestions
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchLibrary: SearchLibraryUseCase,
    private val searchSuggestions: SearchSuggestionsUseCase,
    private val sourceAccountProvider: SearchSourceAccountProvider,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MS,
    private val coroutineScopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private val coroutineScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launch {
            searchHistoryRepository.history.collect { history ->
                _state.update { current ->
                    current.copy(
                        history = history.toPersistentList(),
                    )
                }
                refreshSuggestions(_state.value.query)
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> updateQuery(action.query)
            SearchAction.SubmitSearch -> submitSearch()
            SearchAction.Retry -> submitSearch()
            SearchAction.ClearQuery -> clearQuery()
            SearchAction.ClearHistory -> clearHistory()
            is SearchAction.SelectSuggestion -> selectSuggestion(action.query)
            is SearchAction.OpenTrack -> openTrack(action.track)
            is SearchAction.OpenAlbum -> openAlbum(action.album)
            is SearchAction.OpenArtist -> openArtist(action.artist)
            is SearchAction.DownloadTrack -> downloadTrack(action.track)
        }
    }

    private fun updateQuery(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        _state.update { current ->
            current.copy(
                query = query,
                loadState = if (trimmed.isBlank()) SearchLoadState.Idle else SearchLoadState.Typing,
                tracks = if (trimmed.isBlank()) emptyList<SearchTrackItem>().toPersistentList() else current.tracks,
                albums = if (trimmed.isBlank()) emptyList<SearchAlbumItem>().toPersistentList() else current.albums,
                artists = if (trimmed.isBlank()) emptyList<SearchArtistItem>().toPersistentList() else current.artists,
                suggestions = mergeSearchSuggestions(
                    query = trimmed,
                    history = current.history,
                    localSuggestions = emptyList(),
                ).toPersistentList(),
                failedSourceCount = if (trimmed.isBlank()) 0 else current.failedSourceCount,
            )
        }
        refreshSuggestions(trimmed)
        if (trimmed.isBlank()) return
        searchJob = coroutineScope.launch {
            delay(debounceMillis.coerceAtLeast(0))
            runSearch(trimmed)
        }
    }

    private fun submitSearch() {
        val query = _state.value.query.trim()
        searchJob?.cancel()
        if (query.isBlank()) {
            clearQuery()
            return
        }
        searchJob = coroutineScope.launch {
            runSearch(query)
        }
    }

    private fun clearQuery() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        _state.update { current ->
            current.copy(
                query = "",
                loadState = SearchLoadState.Idle,
                tracks = emptyList<SearchTrackItem>().toPersistentList(),
                albums = emptyList<SearchAlbumItem>().toPersistentList(),
                artists = emptyList<SearchArtistItem>().toPersistentList(),
                suggestions = current.history,
                failedSourceCount = 0,
            )
        }
        refreshSuggestions("")
    }

    private fun clearHistory() {
        coroutineScope.launch {
            searchHistoryRepository.clear()
        }
        val query = _state.value.query
        _state.update { current ->
            current.copy(
                history = emptyList<String>().toPersistentList(),
                suggestions = emptyList<String>().toPersistentList(),
            )
        }
        refreshSuggestions(query)
    }

    private fun selectSuggestion(query: String) {
        _state.update { current ->
            current.copy(query = query)
        }
        submitSearch()
    }

    private fun openTrack(track: SearchTrackItem) {
        if (!track.isPlayableFromSearch()) {
            coroutineScope.launch {
                _events.send(
                    SearchEvent.ShowMessage(UiMessage.Resource(UiMessageKey.SourceResultMustBeImported)),
                )
            }
            return
        }
        coroutineScope.launch {
            _events.send(SearchEvent.OpenTrack(track))
        }
    }

    private fun openAlbum(album: SearchAlbumItem) {
        coroutineScope.launch {
            _events.send(SearchEvent.NavigateToAlbum(album.id))
        }
    }

    private fun openArtist(artist: SearchArtistItem) {
        coroutineScope.launch {
            _events.send(SearchEvent.NavigateToArtist(artist.id))
        }
    }

    private fun downloadTrack(track: SearchTrackItem) {
        val mediaId = track.mediaId
        if (mediaId == null) {
            coroutineScope.launch {
                _events.send(SearchEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded)))
            }
            return
        }
        coroutineScope.launch {
            try {
                enqueueDownload(
                    DownloadRequest(
                        mediaId = mediaId,
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                    )
                )
                _events.send(SearchEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _events.send(
                    SearchEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed))
                )
            }
        }
    }

    private suspend fun runSearch(query: String) {
        _state.update { current ->
            current.copy(
                query = query,
                loadState = SearchLoadState.Searching,
            )
        }
        val results = try {
            searchLibrary(
                query = query,
                sourceAccounts = sourceAccountProvider.sourceAccounts(),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Throwable) {
            searchHistoryRepository.remember(query)
            _state.update { current ->
                current.copy(
                    loadState = SearchLoadState.Error,
                    tracks = emptyList<SearchTrackItem>().toPersistentList(),
                    albums = emptyList<SearchAlbumItem>().toPersistentList(),
                    artists = emptyList<SearchArtistItem>().toPersistentList(),
                    failedSourceCount = 1,
                )
            }
            return
        }

        searchHistoryRepository.remember(query)
        _state.update { current ->
            current.copy(
                loadState = when {
                    results.tracks.isNotEmpty() -> SearchLoadState.Results
                    results.albums.isNotEmpty() -> SearchLoadState.Results
                    results.artists.isNotEmpty() -> SearchLoadState.Results
                    results.failedSources.isNotEmpty() -> SearchLoadState.Error
                    else -> SearchLoadState.Empty
                },
                tracks = results.tracks.toPersistentList(),
                albums = results.albums.toPersistentList(),
                artists = results.artists.toPersistentList(),
                failedSourceCount = results.failedSources.size,
            )
        }
    }

    private fun refreshSuggestions(query: String) {
        suggestionJob?.cancel()
        val normalizedQuery = query.trim()
        val history = _state.value.history.toList()
        suggestionJob = coroutineScope.launch {
            val suggestions = try {
                searchSuggestions(
                    query = normalizedQuery,
                    history = history,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                mergeSearchSuggestions(
                    query = normalizedQuery,
                    history = history,
                    localSuggestions = emptyList(),
                )
            }
            _state.update { current ->
                if (current.query.trim() == normalizedQuery) {
                    current.copy(suggestions = suggestions.toPersistentList())
                } else {
                    current
                }
            }
        }
    }
}

/**
 * The current [PlaybackController] starts playback from the legacy library playlist.
 * A source-only search result still has a real [mediaId] for download, but cannot be
 * sent to that controller until it has been added to the library and received a track id.
 */
internal fun SearchTrackItem.isPlayableFromSearch(): Boolean = id != null

const val SEARCH_DEBOUNCE_MS = 300L
