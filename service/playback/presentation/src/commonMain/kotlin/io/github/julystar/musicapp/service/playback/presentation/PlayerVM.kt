package io.github.julystar.musicapp.service.playback.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.model.PreviousButtonBehavior
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import io.github.julystar.musicapp.service.playback.domain.NowPlayingRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlaybackSourceOption
import io.github.julystar.musicapp.service.playback.domain.PlaybackSourceRepository
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingAction
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingEvent
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingSourceItem
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.toInitialNowPlayingState
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.toNowPlayingControlsState
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.toNowPlayingQueueState
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.toNowPlayingTrackItem
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.withPlaybackSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class PlayerVM constructor(
    private val nowPlayingRepository: NowPlayingRepository,
    private val playbackController: PlaybackController,
    private val enqueueDownload: EnqueueDownloadUseCase,
    private val settingsRepository: SettingsRepository,
    private val playbackSourceRepository: PlaybackSourceRepository,
) : ViewModel() {
    private val whileSubscribed = SharingStarted.WhileSubscribed(5_000)
    // A destination-scoped VM must expose the current cover before its first transition frame.
    private var currentTrackInfo: CurrentTrackInfo? = nowPlayingRepository.currentTrackInfo.value
    private val _nowPlayingState = MutableStateFlow(
        currentTrackInfo.toInitialNowPlayingState(),
    )
    private val _nowPlayingEvents = Channel<NowPlayingEvent>(Channel.BUFFERED)
    private val _playbackAdvancedSettings = MutableStateFlow(PlaybackAdvancedSettings.Default)

    val playbackState = playbackController.state
    val playbackPosition = playbackController.position
    val playbackQueue = playbackController.queue
    val nowPlayingState = _nowPlayingState.asStateFlow()
    val nowPlayingEvents = _nowPlayingEvents.receiveAsFlow()

    val playing = playbackState.map { state ->
        state.status == PlaybackStatus.Playing
    }.stateIn(viewModelScope, whileSubscribed, false)

    val currentDuration = playbackPosition.map { position ->
        position.positionMs.milliseconds
    }.stateIn(viewModelScope, whileSubscribed, 0.milliseconds)

    val bufferDuration = playbackPosition.map { position ->
        position.bufferedMs.milliseconds
    }.stateIn(viewModelScope, whileSubscribed, 0.milliseconds)

    val playerDuration = playbackPosition.map { position ->
        position.durationMs.milliseconds
    }.stateIn(viewModelScope, whileSubscribed, 0.milliseconds)

    val loading = playbackState.map { state ->
        state.status == PlaybackStatus.Loading
    }.stateIn(viewModelScope, whileSubscribed, false)

    val lyricIndex = combine(currentDuration, nowPlayingState) {
        currentDuration, nowPlayingState ->
            nowPlayingState.currentTrack?.lyrics?.lines?.indexOfLast { it.duration <= currentDuration } ?: -1
    }.stateIn(viewModelScope, SharingStarted.Lazily, -1)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _playbackAdvancedSettings.value = settings.playbackAdvanced
            }
        }
        viewModelScope.launch {
            nowPlayingRepository.currentTrackInfo.collect { info ->
                currentTrackInfo = info
                val playbackSources = info?.let { track ->
                    runCatching { playbackSourceRepository.sources(track.id) }
                        .getOrDefault(emptyList())
                        .map(PlaybackSourceOption::toNowPlayingSourceItem)
                }.orEmpty()
                _nowPlayingState.value = _nowPlayingState.value.copy(
                    currentTrack = info?.toNowPlayingTrackItem(),
                    playbackSources = playbackSources,
                )
            }
        }
        viewModelScope.launch {
            combine(
                nowPlayingRepository.previousArtwork,
                nowPlayingRepository.nextArtwork,
                nowPlayingRepository.canPlayPrevious,
                nowPlayingRepository.canPlayNext,
                playbackQueue,
            ) { prevArt, nextArt, canPlayPrevious, canPlayNext, queue ->
                queue.toNowPlayingQueueState(
                    previousArtwork = prevArt,
                    nextArtwork = nextArt,
                    canPlayPrevious = canPlayPrevious,
                    canPlayNext = canPlayNext,
                )
            }.collect { queue ->
                _nowPlayingState.value = _nowPlayingState.value.copy(queue = queue)
            }
        }
        viewModelScope.launch {
            playbackState.collect { state ->
                val currentState = _nowPlayingState.value
                val playbackArtist = state.currentItem?.artist?.takeIf { it.isNotBlank() }
                _nowPlayingState.value = currentState.copy(
                    currentTrack = currentState.currentTrack?.let { track ->
                        playbackArtist?.let { artist -> track.copy(artist = artist) } ?: track
                    },
                    controls = state.toNowPlayingControlsState(),
                )
            }
        }
    }

    fun onNowPlayingAction(action: NowPlayingAction) {
        when (action) {
            NowPlayingAction.NavigateBack -> Unit
            NowPlayingAction.AddLyric -> Unit
            NowPlayingAction.SearchMetadata -> Unit
            NowPlayingAction.RemoveLyric -> removeLyric()
            NowPlayingAction.RemoveCurrentTrack -> remove()
            NowPlayingAction.DownloadCurrentTrack -> downloadCurrentTrack()
            is NowPlayingAction.SelectPlaybackSource -> selectPlaybackSource(action.sourceItemId)
            NowPlayingAction.OpenSleepTimer -> Unit
            NowPlayingAction.OpenLyrics -> Unit
            NowPlayingAction.OpenQueue -> Unit
            NowPlayingAction.PlayPrevious -> playPrevious()
            NowPlayingAction.PlayNext -> playNext()
            NowPlayingAction.Resume -> resume()
            NowPlayingAction.Pause -> pause()
            NowPlayingAction.CycleRepeatMode -> changePlayModeToNext()
            is NowPlayingAction.SeekTo -> seek(action.positionMs)
        }
    }

    fun resume() {
        playbackController.play()
    }

    fun pause() {
        playbackController.pause()
    }

    fun stop() {
        playbackController.clearQueue()
    }

    fun playNext() {
        playbackController.skipNext()
    }

    fun playPrevious() {
        when (_playbackAdvancedSettings.value.previousButtonBehavior) {
            PreviousButtonBehavior.PreviousTrack -> playbackController.skipPrevious()
            PreviousButtonBehavior.RestartCurrentTrack -> playbackController.seekTo(0L)
        }
    }

    fun remove() {
        nowPlayingRepository.removeCurrentTrack()
    }

    fun seek(ms: ULong) {
        playbackController.seekTo(ms.toPlaybackPositionMs())
    }

    fun play(trackId: Long, playlistId: Long) {
        viewModelScope.launch {
            playbackController.play(
                items = listOf(
                    PlayableItem(
                        title = "Track $trackId",
                        libraryTrackId = trackId,
                        libraryPlaylistId = playlistId,
                    )
                ),
            )
        }
    }

    fun changePlayModeToNext() {
        val nextMode = playbackState.value.nextPlaybackMode()
        playbackController.setShuffle(nextMode.shuffleEnabled)
        playbackController.setRepeatMode(nextMode.repeatMode)
    }

    fun removeLyric() {
        nowPlayingRepository.removeCurrentLyrics()
    }

    private fun downloadCurrentTrack() {
        val track = _nowPlayingState.value.currentTrack
        val mediaId = track?.mediaId
        if (track == null || mediaId == null) {
            viewModelScope.launch {
                _nowPlayingEvents.send(NowPlayingEvent.ShowMessage("This track cannot be downloaded yet."))
            }
            return
        }
        enqueueTrackDownload(track)
    }

    private fun selectPlaybackSource(sourceItemId: Long) {
        val trackId = _nowPlayingState.value.currentTrack?.id ?: return
        viewModelScope.launch {
            if (!playbackSourceRepository.select(trackId, sourceItemId)) return@launch
            val sources = playbackSourceRepository.sources(trackId)
                .map(PlaybackSourceOption::toNowPlayingSourceItem)
            _nowPlayingState.value = _nowPlayingState.value.withPlaybackSources(sources)
            _nowPlayingEvents.send(
                NowPlayingEvent.ShowMessage("Preferred source updated; it will be used first next time."),
            )
        }
    }

    private fun enqueueTrackDownload(track: NowPlayingTrackItem) {
        val mediaId = track.mediaId ?: return
        viewModelScope.launch {
            try {
                enqueueDownload(
                    DownloadRequest(
                        mediaId = mediaId,
                        title = track.title,
                        durationMs = track.durationMs,
                    )
                )
                _nowPlayingEvents.send(NowPlayingEvent.ShowMessage("Added to Downloads."))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _nowPlayingEvents.send(
                    NowPlayingEvent.ShowMessage(
                        exception.message?.takeIf { it.isNotBlank() } ?: "Failed to add download.",
                    )
                )
            }
        }
    }
}

private fun PlaybackSourceOption.toNowPlayingSourceItem() = NowPlayingSourceItem(
    sourceItemId = sourceItemId,
    accountName = accountName,
    displayName = displayName,
    quality = quality,
    isSelected = isSelected,
    playbackAudioInfo = playbackAudioInfo,
)

private fun ULong.toPlaybackPositionMs(): Long {
    val max = Long.MAX_VALUE.toULong()
    return if (this > max) Long.MAX_VALUE else toLong()
}

internal data class PlaybackModeSelection(
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
)

internal fun PlayerState.nextPlaybackMode(): PlaybackModeSelection {
    return when {
        shuffleEnabled -> PlaybackModeSelection(RepeatMode.One, shuffleEnabled = false)
        repeatMode == RepeatMode.One -> PlaybackModeSelection(RepeatMode.All, shuffleEnabled = false)
        repeatMode == RepeatMode.All -> PlaybackModeSelection(RepeatMode.All, shuffleEnabled = true)
        else -> PlaybackModeSelection(RepeatMode.All, shuffleEnabled = false)
    }
}
