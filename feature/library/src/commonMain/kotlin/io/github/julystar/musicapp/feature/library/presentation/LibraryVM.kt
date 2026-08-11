package io.github.julystar.musicapp.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.DomainTrackBrowserItem
import io.github.julystar.musicapp.core.domain.model.FilterCriteria
import io.github.julystar.musicapp.core.domain.model.LibrarySortField
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.model.SortCriteria
import io.github.julystar.musicapp.core.domain.model.SortDirection
import io.github.julystar.musicapp.core.domain.repository.DownloadCollectionRepository
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.FolderRepository
import io.github.julystar.musicapp.core.domain.repository.GenreRepository
import io.github.julystar.musicapp.core.domain.repository.HistoryRepository
import io.github.julystar.musicapp.core.domain.repository.LibraryRepository
import io.github.julystar.musicapp.core.domain.repository.LosslessRepository
import io.github.julystar.musicapp.core.domain.repository.PlaylistRepository
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey

class LibraryVM(
    libraryRepository: LibraryRepository,
    playlistRepository: PlaylistRepository,
    private val genreRepository: GenreRepository,
    private val folderRepository: FolderRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val losslessRepository: LosslessRepository,
    private val downloadCollectionRepository: DownloadCollectionRepository,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Sort / filter mutable state flows
    private val _genreSort = MutableStateFlow(SortCriteria.Default)
    private val _genreFilter = MutableStateFlow(FilterCriteria.GenreFilter())
    private val _folderSort = MutableStateFlow(SortCriteria.Default)
    private val _folderFilter = MutableStateFlow(FilterCriteria.FolderFilter())
    private val _favoritesSort = MutableStateFlow(SortCriteria.Default)
    private val _favoritesFilter = MutableStateFlow(FilterCriteria.FavoritesFilter())
    private val _historySort = MutableStateFlow(
        SortCriteria(field = LibrarySortField.LastPlayed, direction = SortDirection.Descending)
    )
    private val _historyFilter = MutableStateFlow(FilterCriteria.HistoryFilter())
    private val _losslessSort = MutableStateFlow(SortCriteria.Default)
    private val _losslessFilter = MutableStateFlow(FilterCriteria.LosslessFilter())
    private val _hiResSort = MutableStateFlow(SortCriteria.Default)
    private val _hiResFilter = MutableStateFlow(FilterCriteria.LosslessFilter())
    private val _downloadsSort = MutableStateFlow(SortCriteria.Default)
    private val _downloadsFilter = MutableStateFlow(FilterCriteria.DownloadsFilter())

    // Flat library
    private val flatLibrary = combine(
        libraryRepository.tracks,
        libraryRepository.albums,
        libraryRepository.artists,
        playlistRepository.playlistSummaries,
    ) { tracks, albums, artists, playlists ->
        FlatLibraryData(
            tracks = tracks.toPersistentList(),
            albums = albums.toPersistentList(),
            artists = artists.toPersistentList(),
            playlists = playlists.toPersistentList(),
        )
    }

    // Per-category reactive flows
    private val genreNamesFlow = genreRepository.genreNames
    private val genreTracksFlow = flowOf(emptyMap<String, RepositoryState<List<DomainTrackBrowserItem>>>())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val foldersFlow: kotlinx.coroutines.flow.Flow<RepositoryState<List<io.github.julystar.musicapp.core.domain.repository.LibraryFolderItem>>> =
        _folderSort.flatMapLatest { sort ->
            _folderFilter.flatMapLatest { filter ->
                folderRepository.libraryRoots
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favoritesFlow = _favoritesSort.flatMapLatest { sort ->
        _favoritesFilter.flatMapLatest { filter ->
            favoritesRepository.favoriteTracks(sort, filter)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyFlow = _historySort.flatMapLatest { sort ->
        _historyFilter.flatMapLatest { filter ->
            historyRepository.recentTracks(sort = sort, filter = filter)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val losslessFlow = _losslessSort.flatMapLatest { sort ->
        _losslessFilter.flatMapLatest { filter ->
            losslessRepository.losslessTracks(sort, filter)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hiResFlow = _hiResSort.flatMapLatest { sort ->
        _hiResFilter.flatMapLatest { filter ->
            losslessRepository.hiResTracks(sort, filter)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val downloadsFlow = _downloadsSort.flatMapLatest { sort ->
        _downloadsFilter.flatMapLatest { filter ->
            downloadCollectionRepository.downloadedTracks(sort, filter)
        }
    }

    private data class FlatLibraryData(
        val tracks: kotlinx.collections.immutable.ImmutableList<LibraryTrackItem>,
        val albums: kotlinx.collections.immutable.ImmutableList<io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem>,
        val artists: kotlinx.collections.immutable.ImmutableList<io.github.julystar.musicapp.core.domain.model.LibraryArtistItem>,
        val playlists: kotlinx.collections.immutable.ImmutableList<io.github.julystar.musicapp.core.domain.model.PlaylistSummary>,
    )

    val state: StateFlow<LibraryState> = combine(
        flatLibrary,
        genreNamesFlow,
        genreTracksFlow,
        foldersFlow,
        favoritesFlow,
    ) { flat, genreNames, genreTracks, folders, favorites ->
        // Build initial state from first 5 flows
        LibraryState(
            tracks = flat.tracks,
            albums = flat.albums,
            artists = flat.artists,
            playlists = flat.playlists,
            genreNames = genreNames,
            genreTracks = genreTracks,
            folders = folders,
            favorites = favorites,
        )
    }.combine(historyFlow) { state, history ->
        state.copy(history = history)
    }.combine(losslessFlow) { state, lossless ->
        state.copy(lossless = lossless)
    }.combine(hiResFlow) { state, hiRes ->
        state.copy(hiRes = hiRes)
    }.combine(downloadsFlow) { state, downloads ->
        state.copy(downloads = downloads)
    }.combine(_genreSort) { state, sort ->
        state.copy(genreSort = sort)
    }.combine(_genreFilter) { state, filter ->
        state.copy(genreFilter = filter)
    }.combine(_folderSort) { state, sort ->
        state.copy(folderSort = sort)
    }.combine(_folderFilter) { state, filter ->
        state.copy(folderFilter = filter)
    }.combine(_favoritesSort) { state, sort ->
        state.copy(favoritesSort = sort)
    }.combine(_favoritesFilter) { state, filter ->
        state.copy(favoritesFilter = filter)
    }.combine(_historySort) { state, sort ->
        state.copy(historySort = sort)
    }.combine(_historyFilter) { state, filter ->
        state.copy(historyFilter = filter)
    }.combine(_losslessSort) { state, sort ->
        state.copy(losslessSort = sort)
    }.combine(_losslessFilter) { state, filter ->
        state.copy(losslessFilter = filter)
    }.combine(_hiResSort) { state, sort ->
        state.copy(hiResSort = sort)
    }.combine(_hiResFilter) { state, filter ->
        state.copy(hiResFilter = filter)
    }.combine(_downloadsSort) { state, sort ->
        state.copy(downloadsSort = sort)
    }.combine(_downloadsFilter) { state, filter ->
        state.copy(downloadsFilter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryState(),
    )

    fun onAction(action: LibraryAction) {
        when (action) {
            LibraryAction.Refresh -> Unit
            is LibraryAction.PlayTrack -> Unit
            is LibraryAction.DownloadTrack -> downloadTrack(action.track)
            is LibraryAction.SelectGenre -> Unit
            is LibraryAction.BrowseFolder -> Unit

            is LibraryAction.UpdateGenreSort -> _genreSort.value = action.sort
            is LibraryAction.UpdateGenreFilter -> _genreFilter.value = action.filter
            is LibraryAction.UpdateFolderSort -> _folderSort.value = action.sort
            is LibraryAction.UpdateFolderFilter -> _folderFilter.value = action.filter
            is LibraryAction.UpdateFavoritesSort -> _favoritesSort.value = action.sort
            is LibraryAction.UpdateFavoritesFilter -> _favoritesFilter.value = action.filter
            is LibraryAction.UpdateHistorySort -> _historySort.value = action.sort
            is LibraryAction.UpdateHistoryFilter -> _historyFilter.value = action.filter
            is LibraryAction.UpdateLosslessSort -> _losslessSort.value = action.sort
            is LibraryAction.UpdateLosslessFilter -> _losslessFilter.value = action.filter
            is LibraryAction.UpdateHiResSort -> _hiResSort.value = action.sort
            is LibraryAction.UpdateHiResFilter -> _hiResFilter.value = action.filter
            is LibraryAction.UpdateDownloadsSort -> _downloadsSort.value = action.sort
            is LibraryAction.UpdateDownloadsFilter -> _downloadsFilter.value = action.filter

            is LibraryAction.ToggleFavorite -> toggleFavorite(action.trackId)
            is LibraryAction.ClearHistory -> clearHistory()
            is LibraryAction.RemoveDownload -> removeDownload(action.trackId)
        }
    }

    private fun downloadTrack(track: LibraryTrackItem) {
        val mediaId = track.mediaId
        if (mediaId == null) {
            viewModelScope.launch {
                _events.send(LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded)))
            }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(
                    DownloadRequest(
                        mediaId = mediaId,
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                    )
                )
                _events.send(LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _events.send(
                    LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed))
                )
            }
        }
    }

    private fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            try {
                val isNowFavorite = favoritesRepository.toggleFavorite(trackId)
                _events.send(
                    LibraryEvent.ShowMessage(
                        UiMessage.Resource(
                            if (isNowFavorite) UiMessageKey.FavoriteAdded else UiMessageKey.FavoriteRemoved
                        )
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(
                    LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.FavoriteOperationFailed))
                )
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                historyRepository.clearHistory()
                _events.send(LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.PlayHistoryCleared)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(
                    LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.PlayHistoryClearFailed))
                )
            }
        }
    }

    private fun removeDownload(trackId: Long) {
        viewModelScope.launch {
            try {
                downloadCollectionRepository.removeDownload(trackId)
                _events.send(LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadRemoved)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(
                    LibraryEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadRemoveFailed))
                )
            }
        }
    }
}
