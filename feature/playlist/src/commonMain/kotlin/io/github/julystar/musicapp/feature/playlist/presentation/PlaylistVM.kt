package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.DomainPlaylistTrack
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.domain.repository.PlaylistRepository
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import io.github.julystar.musicapp.service.playback.domain.PlaylistPlaybackSync
import io.github.julystar.musicapp.source.api.ImportRepository
import io.github.julystar.musicapp.source.api.PlaylistImportTarget
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey

class PlaylistVM constructor(
    private val playlistRepository: PlaylistRepository,
    private val importRepository: ImportRepository,
    private val playlistImportTarget: PlaylistImportTarget,
    private val playlistPlaybackSync: PlaylistPlaybackSync,
    private val enqueueDownload: EnqueueDownloadUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _id: Long = savedStateHandle["id"]!!

    private val _removeModalOpen = MutableStateFlow(false)
    private val _playlistSummary = MutableStateFlow<PlaylistSummary?>(null)
    private val _playlistTracks = MutableStateFlow(persistentListOf<DomainPlaylistTrack>())
    private val _state = MutableStateFlow(PlaylistState(playlistId = _id))
    private val _events = Channel<PlaylistEvent>(Channel.BUFFERED)

    val removeModalOpen = _removeModalOpen.asStateFlow()
    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            playlistRepository.playlistSummaries.collect { summaries ->
                val summary = summaries.find { it.id == _id }
                _playlistSummary.value = summary
                if (summary != null) {
                    _state.value = summary.toPlaylistHeaderState(_state.value)
                    syncToPlayer()
                }
            }
        }
        viewModelScope.launch {
            playlistRepository.observePlaylistTracks(_id).collect { rows ->
                _playlistTracks.value = rows.toPersistentList()
                _state.value = _state.value.copy(
                    tracks = rows.map { row ->
                        row.toPlaylistTrackItem()
                    }.toPersistentList()
                )
                syncToPlayer()
            }
        }
        viewModelScope.launch {
            playlistRepository.playlistRefreshEvents.collect {
                playlistPlaybackSync.refreshPlaylistIfCurrent(_id)
            }
        }
    }

    fun onAction(action: PlaylistAction) {
        when (action) {
            PlaylistAction.NavigateBack -> Unit
            PlaylistAction.EditPlaylist -> Unit
            PlaylistAction.PlayAll -> Unit
            is PlaylistAction.PlayTrack -> Unit
            PlaylistAction.ImportTracks -> prepareImportMusics()
            PlaylistAction.OpenRemoveDialog -> openRemoveModal()
            PlaylistAction.CloseRemoveDialog -> closeRemoveModal()
            PlaylistAction.ConfirmRemovePlaylist -> {
                closeRemoveModal()
                remove()
            }
            is PlaylistAction.DownloadTrack -> downloadTrack(action.track)
            is PlaylistAction.RemoveTrack -> removeMusic(action.trackId)
            is PlaylistAction.MoveTrack -> musicMoveTo(action.fromIndex, action.toIndex)
        }
    }

    fun remove() {
        playlistRepository.removePlaylist(_id)
    }

    fun removeMusic(trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeMusic(_id, trackId)
        }
    }

    fun prepareImportMusics() {
        importRepository.prepare(listOf(SourceNodeType.Track)) { entries ->
            viewModelScope.launch {
                val added = playlistImportTarget.addMusicSelectionsToPlaylist(_id, entries)
                playlistRepository.requestTotalDurationById(added)
                playlistPlaybackSync.refreshPlaylistIfCurrent(_id)
                playlistRepository.scheduleReload()
            }
        }
    }

    fun musicMoveTo(fromIndex: Int, toIndex: Int) {
        val from = _playlistTracks.value.getOrNull(fromIndex) ?: return

        _playlistTracks.value = _playlistTracks.value
            .removingAt(fromIndex)
            .addingAt(toIndex, from)

        viewModelScope.launch {
            playlistRepository.replaceMusicOrderById(
                _id,
                _playlistTracks.value.map { it.trackId },
            )
            playlistPlaybackSync.refreshPlaylistIfCurrent(_id)
        }
    }

    fun openRemoveModal() {
        _removeModalOpen.value = true
        _state.value = _state.value.copy(isRemoveDialogOpen = true)
    }

    fun closeRemoveModal() {
        _removeModalOpen.value = false
        _state.value = _state.value.copy(isRemoveDialogOpen = false)
    }

    private fun syncToPlayer() {
        val summary = _playlistSummary.value ?: return
        playlistPlaybackSync.refreshPlaylistIfCurrent(summary, _playlistTracks.value)
    }

    private fun downloadTrack(track: PlaylistTrackItem) {
        val mediaId = track.mediaId
        if (mediaId == null) {
            viewModelScope.launch {
                _events.send(PlaylistEvent.ShowMessage(UiMessage.Resource(UiMessageKey.TrackCannotBeDownloaded)))
            }
            return
        }
        viewModelScope.launch {
            try {
                enqueueDownload(
                    DownloadRequest(
                        mediaId = mediaId,
                        title = track.title,
                        durationMs = track.durationMs,
                    )
                )
                _events.send(PlaylistEvent.ShowMessage(UiMessage.Resource(UiMessageKey.AddedToDownloads)))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _events.send(
                    PlaylistEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadFailed))
                )
            }
        }
    }
}
