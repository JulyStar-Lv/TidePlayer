package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.domain.SleepModeState
import io.github.julystar.musicapp.core.data.ToastRepositoryImpl

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.data.PlaybackPreparationResult
import io.github.julystar.musicapp.service.playback.data.preparePlayback
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uniffi.app_backend.ArgRemoveMusicFromPlaylist
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.StorageId
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.diagnostics.AppLogger
import kotlin.math.max

internal interface AndroidPlayerStateStore {
    val playlist: StateFlow<Playlist?>
    val music: StateFlow<Music?>
    val nextMusic: StateFlow<MusicAbstract?>
    val previousMusic: StateFlow<MusicAbstract?>
    val pauseRequest: Flow<Unit>

    fun setIsLoading(loading: Boolean)
    fun setIsPlaying(playing: Boolean)
    fun setCurrent(music: Music, playlist: Playlist)
    fun resetCurrent()
    fun notifyDurationChanged()
    fun refreshPlaylistIfMatch(playlist: Playlist)
    fun emitPauseRequest()
    fun reload()
}

internal interface AndroidPlaybackRemovalEvents {
    val preRemovePlaylistEvent: Flow<PlaylistId>
    val preRemoveMusicEvent: Flow<ArgRemoveMusicFromPlaylist>
    val preRemoveStorageEvent: Flow<StorageId>

    suspend fun removeMusic(playlistId: Long, musicId: Long)
}

internal interface AndroidPlaybackLibrary {
    suspend fun getMusic(id: MusicId): Music?
    suspend fun getPlaylist(id: PlaylistId): Playlist?
}

class PlayerControllerRepository internal constructor(
    private val playerState: AndroidPlayerStateStore,
    private val toastRepository: ToastRepositoryImpl,
    private val removalEvents: AndroidPlaybackRemovalEvents,
    private val bridge: Bridge,
    private val playbackLibrary: AndroidPlaybackLibrary,
    private val playbackResourceResolver: PlaybackResourceResolver,
    private val _scope: CoroutineScope,
    initialPlaybackEngine: AndroidPlaybackEngine?,
    private val mainDispatcher: CoroutineDispatcher,
    private val settingsRepository: SettingsRepository? = null,
    private val networkStatusProvider: NetworkStatusProvider? = null,
) : PlayerController {
    private var _mediaController: MediaController? = null
    private val _playlist = playerState.playlist
    private val _music = playerState.music
    private val _sleep = MutableStateFlow(SleepModeState())

    private var _sleepJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackResource: PlaybackResource? = null
    private var playbackEngine: AndroidPlaybackEngine? = initialPlaybackEngine
    private val nextMusic = playerState.nextMusic
    private val previousMusic = playerState.previousMusic
    private var pendingNetworkRecovery: Pair<MusicId, PlaylistId>? = null

    override val sleepState = _sleep.asStateFlow()

    constructor(
        playerRepository: PlayerRepository,
        toastRepository: ToastRepositoryImpl,
        playlistRepository: PlaylistRepositoryImpl,
        storageRepository: StorageRepositoryImpl,
        bridge: Bridge,
        roomLibraryStore: RoomLibraryStore,
        playbackResourceResolver: PlaybackResourceResolver,
        _scope: CoroutineScope,
        settingsRepository: SettingsRepository,
        networkStatusProvider: NetworkStatusProvider,
    ) : this(
        playerState = AndroidPlayerRepositoryStateStore(playerRepository),
        toastRepository = toastRepository,
        removalEvents = RepositoryAndroidPlaybackRemovalEvents(
            playlistRepository = playlistRepository,
            storageRepository = storageRepository,
        ),
        bridge = bridge,
        playbackLibrary = object : AndroidPlaybackLibrary {
            override suspend fun getMusic(id: MusicId): Music? {
                return roomLibraryStore.getMusic(id)
            }

            override suspend fun getPlaylist(id: PlaylistId): Playlist? {
                return roomLibraryStore.getPlaylist(id)
            }
        },
        playbackResourceResolver = playbackResourceResolver,
        _scope = _scope,
        initialPlaybackEngine = null,
        mainDispatcher = Dispatchers.Main,
        settingsRepository = settingsRepository,
        networkStatusProvider = networkStatusProvider,
    )

    init {
        _scope.launch(mainDispatcher) {
            removalEvents.preRemovePlaylistEvent.collect { id ->
                if (_playlist.value?.abstr?.meta?.id == id) {
                    stop()
                }
            }
        }
        networkStatusProvider?.let { provider ->
            _scope.launch(mainDispatcher) {
                provider.status.collect { network ->
                    val pending = pendingNetworkRecovery ?: return@collect
                    val settings = settingsRepository?.settings?.first() ?: return@collect
                    if (network.isOnline && settings.resumePlaybackAfterNetworkRecovery) {
                        pendingNetworkRecovery = null
                        play(pending.first, pending.second)
                    }
                }
            }
        }
        _scope.launch(mainDispatcher) {
            removalEvents.preRemoveMusicEvent.collect { arg ->
                if (_playlist.value?.abstr?.meta?.id == arg.playlistId && _music.value?.meta?.id == arg.musicId) {
                    stop()
                }
            }
        }
        _scope.launch(mainDispatcher) {
            removalEvents.preRemoveStorageEvent.collect { id ->
                if (_music.value?.loc?.storageId == id) {
                    stop()
                }
            }
        }
    }

    fun setupMediaController(mediaController: MediaController) {
        _mediaController = mediaController
        playbackEngine = MediaControllerAndroidPlaybackEngine(
            mediaController = mediaController,
            bridge = bridge,
            scope = _scope,
        )

        mediaController.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                val trackId = mediaController.currentMediaItem?.mediaId?.toLongOrNull()
                val playlistId = _playlist.value?.abstr?.meta?.id
                if (trackId != null && playlistId != null) {
                    pendingNetworkRecovery = MusicId(trackId) to playlistId
                }
                playerState.setIsLoading(false)
                playerState.setIsPlaying(false)
                _scope.launch {
                    AppLogger.error(
                        DiagnosticLogCategory.Playback,
                        "PlayerControllerRepository",
                        "Android playback failed",
                        error.stackTraceToString(),
                    )
                    toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerState.setIsPlaying(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerState.setIsLoading(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    playerState.notifyDurationChanged()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull() ?: return
                syncCurrentMediaItem(trackId)
            }
        })
        _scope.launch {
            playerState.reload()
        }
        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlayerControllerRepository",
            "Media controller attached",
        )
    }

    fun destroyMediaController() {
        playbackEngine?.release()
        playbackEngine = null
        _mediaController = null

        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlayerControllerRepository",
            "Media controller detached",
        )
    }

    override fun getCurrentPosition(): Long {
        return playbackEngine?.readPosition()?.positionMs ?: 0
    }

    override fun getBufferedPosition(): Long {
        return playbackEngine?.readPosition()?.bufferedMs ?: 0
    }

    override fun getDuration(): Long {
        return playbackEngine?.readPosition()?.durationMs ?: 0
    }

    override fun play(
        id: MusicId,
        playlistId: PlaylistId,
        startPositionMs: Long,
    ) {
        val engine = playbackEngine ?: return
        val normalizedStartPositionMs = startPositionMs.coerceAtLeast(0L)

        if (
            _music.value?.meta?.id == id &&
            _playlist.value?.abstr?.meta?.id == playlistId &&
            engine.hasLoadedTrack(id.value)
        ) {
            if (normalizedStartPositionMs > 0L) {
                engine.seekTo(normalizedStartPositionMs)
            }
            resume()
            return
        }

        playbackJob?.cancel()
        playbackJob = _scope.launch(mainDispatcher) {
            playerState.setIsLoading(true)
            try {
                val queuedPlaylist = _playlist.value?.takeIf { queue ->
                    queue.abstr.meta.id == playlistId && queue.musics.any { it.meta.id == id }
                }

                val music = playbackLibrary.getMusic(id)
                val playlist = queuedPlaylist ?: playbackLibrary.getPlaylist(playlistId)
                if (
                    music == null ||
                    playlist == null ||
                    playlist.musics.none { playlistMusic -> playlistMusic.meta.id == id }
                ) {
                    playerState.resetCurrent()
                    return@launch
                }
                releasePlaybackResource()

                when (
                    val queueLoad = engine.loadQueue(
                        AndroidPlaybackQueueLoadRequest(
                            playlist = playlist,
                            currentTrackId = id.value,
                            startPositionMs = normalizedStartPositionMs,
                        )
                    )
                ) {
                    PlaybackEngineLoadResult.Ready -> {
                        playbackResource = null
                        pendingNetworkRecovery = null
                    }
                    is PlaybackEngineLoadResult.Unsupported -> {
                        when (
                            val preparation = preparePlayback(
                                music = music,
                                playlistId = playlist.abstr.meta.id.value,
                                playbackResourceResolver = playbackResourceResolver,
                                playbackEngine = engine,
                                settingsRepository = settingsRepository,
                                networkStatusProvider = networkStatusProvider,
                            )
                        ) {
                            is PlaybackPreparationResult.Ready -> {
                                playbackResource = preparation.resource
                                pendingNetworkRecovery = null
                                if (normalizedStartPositionMs > 0L) {
                                    engine.seekTo(normalizedStartPositionMs)
                                }
                            }
                            PlaybackPreparationResult.NetworkBlocked,
                            PlaybackPreparationResult.Failed -> {
                                pendingNetworkRecovery = id to playlistId
                                toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                                playerState.resetCurrent()
                                return@launch
                            }
                        }
                    }
                    is PlaybackEngineLoadResult.Failure -> {
                        pendingNetworkRecovery = id to playlistId
                        toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                        playerState.resetCurrent()
                        return@launch
                    }
                }

                if (
                    _music.value?.meta?.id != id ||
                    _playlist.value?.abstr?.meta?.id != playlistId
                ) {
                    playerState.setCurrent(music, playlist)
                }
                playerState.setIsPlaying(true)
                playerState.notifyDurationChanged()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                releasePlaybackResource()
                AppLogger.error(
                    DiagnosticLogCategory.Playback,
                    "PlayerControllerRepository",
                    "Android playback preparation failed",
                    exception.stackTraceToString(),
                )
                toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                playerState.setIsPlaying(false)
                playerState.resetCurrent()
            } finally {
                playerState.setIsLoading(false)
            }
        }
    }

    override fun resume() {
        val engine = playbackEngine ?: return
        engine.play()
        playerState.setIsPlaying(true)
    }

    override fun pause() {
        val engine = playbackEngine ?: return
        engine.pause()
        playerState.setIsPlaying(false)
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        playbackEngine?.stop()
        releasePlaybackResourceAsync()
        playerState.setIsPlaying(false)
        playerState.resetCurrent()
    }

    private suspend fun releasePlaybackResource() {
        val resource = playbackResource ?: return
        playbackResource = null
        playbackResourceResolver.release(resource)
    }

    private fun releasePlaybackResourceAsync() {
        val resource = playbackResource ?: return
        playbackResource = null
        _scope.launch {
            playbackResourceResolver.release(resource)
        }
    }

    private fun syncCurrentMediaItem(trackId: Long) {
        _scope.launch(mainDispatcher) {
            val playlist = _playlist.value ?: return@launch
            if (playlist.musics.none { music -> music.meta.id.value == trackId }) return@launch
            val music = playbackLibrary.getMusic(MusicId(trackId)) ?: return@launch
            if (_mediaController?.currentMediaItem?.mediaId != trackId.toString()) return@launch
            releasePlaybackResource()
            playerState.setCurrent(music, playlist)
            playerState.setIsPlaying(_mediaController?.isPlaying == true)
            playerState.notifyDurationChanged()
        }
    }

    override fun playNext() {
        val m = nextMusic.value
        val p = _playlist.value
        if (m != null && p != null) {
            play(m.meta.id, p.abstr.meta.id)
        }
    }

    override fun playPrevious() {
        val m = previousMusic.value
        val p = _playlist.value
        if (m != null && p != null) {
            play(m.meta.id, p.abstr.meta.id)
        }
    }

    override fun seek(ms: ULong) {
        playbackEngine?.seekTo(ms.coerceAtMost(Long.MAX_VALUE.toULong()).toLong())
    }

    override fun scheduleSleep(newExpiredMs: Long) {
        _sleepJob?.cancel()

        val delayMs = max(newExpiredMs - System.currentTimeMillis(), 0)
        _sleepJob = _scope.launch {
            _sleep.update { state -> state.copy(enabled = true, expiredMs = newExpiredMs) }
            AppLogger.info(
                DiagnosticLogCategory.Playback,
                "PlayerControllerRepository",
                "Sleep timer scheduled",
            )
            delay(delayMs)
            AppLogger.info(
                DiagnosticLogCategory.Playback,
                "PlayerControllerRepository",
                "Sleep timer elapsed",
            )
            playerState.emitPauseRequest()
            _sleep.update { state -> state.copy(enabled = false, expiredMs = 0) }
        }
    }

    override fun refreshPlaylistIfMatch(playlist: Playlist) {
        playerState.refreshPlaylistIfMatch(playlist)
    }

    override fun cancelSleep() {
        _sleepJob?.cancel()
        _sleepJob = null
        _sleep.update { state -> state.copy(enabled = false, expiredMs = 0) }
    }

    fun remove() {
        val m = _music.value
        val p = _playlist.value
        _scope.launch {
            if (m != null && p != null) {
                removalEvents.removeMusic(p.abstr.meta.id.value, m.meta.id.value)
            }
        }
    }
}

private class AndroidPlayerRepositoryStateStore(
    private val playerRepository: PlayerRepository,
) : AndroidPlayerStateStore {
    override val playlist = playerRepository.playlist
    override val music = playerRepository.music
    override val nextMusic = playerRepository.nextMusic
    override val previousMusic = playerRepository.previousMusic
    override val pauseRequest = playerRepository.pauseRequest

    override fun setIsLoading(loading: Boolean) {
        playerRepository.setIsLoading(loading)
    }

    override fun setIsPlaying(playing: Boolean) {
        playerRepository.setIsPlaying(playing)
    }

    override fun setCurrent(music: Music, playlist: Playlist) {
        playerRepository.setCurrent(music, playlist)
    }

    override fun resetCurrent() {
        playerRepository.resetCurrent()
    }

    override fun notifyDurationChanged() {
        playerRepository.notifyDurationChanged()
    }

    override fun refreshPlaylistIfMatch(playlist: Playlist) {
        playerRepository.refreshPlaylistIfMatch(playlist)
    }

    override fun emitPauseRequest() {
        playerRepository.emitPauseRequest()
    }

    override fun reload() {
        playerRepository.reload()
    }
}

private class RepositoryAndroidPlaybackRemovalEvents(
    private val playlistRepository: PlaylistRepositoryImpl,
    private val storageRepository: StorageRepositoryImpl,
) : AndroidPlaybackRemovalEvents {
    override val preRemovePlaylistEvent = playlistRepository.preRemovePlaylistEvent
    override val preRemoveMusicEvent = playlistRepository.preRemoveMusicEvent
    override val preRemoveStorageEvent = storageRepository.preRemoveStorageEvent

    override suspend fun removeMusic(playlistId: Long, musicId: Long) {
        playlistRepository.removeMusic(playlistId, musicId)
    }
}
