package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.core.data.ToastRepositoryImpl
import io.github.julystar.musicapp.core.audio.AudioDspRuntimeMonitor

import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.domain.SleepModeState
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.data.PlaybackPreparationResult
import io.github.julystar.musicapp.service.playback.data.preparePlayback
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.app_backend.MusicId
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistId
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.diagnostics.AppLogger
import kotlin.math.max
import kotlin.math.log10
import kotlin.math.min

class IosPlayerController internal constructor(
    private val playerRepository: PlayerRepository,
    private val toastRepository: ToastRepositoryImpl,
    private val playlistRepository: PlaylistRepositoryImpl,
    private val storageRepository: StorageRepositoryImpl,
    private val roomLibraryStore: RoomLibraryStore,
    private val playbackResourceResolver: PlaybackResourceResolver,
    private val scope: CoroutineScope,
    private val playbackEngine: IosPlaybackEngine,
    private val mainDispatcher: CoroutineDispatcher,
    private val settingsRepository: SettingsRepository? = null,
    private val networkStatusProvider: NetworkStatusProvider? = null,
) : PlayerController {
    private val sleep = MutableStateFlow(SleepModeState())
    private var sleepJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackResource: PlaybackResource? = null
    private var pendingNetworkRecovery: Pair<MusicId, PlaylistId>? = null
    private var pendingSeekPositionMs: Long? = null
    private var seekGeneration = 0L
    private var currentSettings = AppSettings.Default

    override val sleepState = sleep.asStateFlow()

    constructor(
        playerRepository: PlayerRepository,
        toastRepository: ToastRepositoryImpl,
        playlistRepository: PlaylistRepositoryImpl,
        storageRepository: StorageRepositoryImpl,
        roomLibraryStore: RoomLibraryStore,
        playbackResourceResolver: PlaybackResourceResolver,
        scope: CoroutineScope,
        settingsRepository: SettingsRepository,
        networkStatusProvider: NetworkStatusProvider,
    ) : this(
        playerRepository = playerRepository,
        toastRepository = toastRepository,
        playlistRepository = playlistRepository,
        storageRepository = storageRepository,
        roomLibraryStore = roomLibraryStore,
        playbackResourceResolver = playbackResourceResolver,
        scope = scope,
        playbackEngine = AvPlayerIosPlaybackEngine(),
        mainDispatcher = Dispatchers.Main,
        settingsRepository = settingsRepository,
        networkStatusProvider = networkStatusProvider,
    )

    init {
        scope.launch {
            playerRepository.pauseRequest.collect { pause() }
        }
        settingsRepository?.let { repository ->
            scope.launch {
                repository.settings.collect { settings ->
                    currentSettings = settings
                    updateAudioDsp(settings)
                }
            }
        }
        scope.launch {
            playbackEngine.playbackCompleted.collect {
                if (!playerRepository.playing.value) return@collect
                val next = playerRepository.onCompleteMusic.value
                val playlist = playerRepository.playlist.value
                if (next != null && playlist != null) {
                    play(next.meta.id, playlist.abstr.meta.id)
                } else {
                    playerRepository.setIsPlaying(false)
                }
            }
        }
        scope.launch {
            while (true) {
                playbackEngine.audioDspRuntimeSnapshot()?.let(AudioDspRuntimeMonitor::publish)
                delay(150)
            }
        }
        scope.launch {
            playlistRepository.preRemovePlaylistEvent.collect { id ->
                if (playerRepository.playlist.value?.abstr?.meta?.id == id) {
                    stop()
                }
            }
        }
        scope.launch {
            playlistRepository.preRemoveMusicEvent.collect { arg ->
                if (
                    playerRepository.playlist.value?.abstr?.meta?.id == arg.playlistId &&
                    playerRepository.music.value?.meta?.id == arg.musicId
                ) {
                    stop()
                }
            }
        }
        scope.launch {
            storageRepository.preRemoveStorageEvent.collect { id ->
                if (playerRepository.music.value?.loc?.storageId == id) {
                    stop()
                }
            }
        }
        networkStatusProvider?.let { provider ->
            scope.launch {
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
    }

    override fun getCurrentPosition(): Long =
        pendingSeekPositionMs ?: playbackEngine.readPosition().positionMs

    override fun getBufferedPosition(): Long = playbackEngine.readPosition().bufferedMs

    override fun getDuration(): Long = playbackEngine.readPosition().durationMs

    override fun getPendingSeekPosition(): Long? = pendingSeekPositionMs

    override fun play(id: MusicId, playlistId: PlaylistId) {
        if (
            playerRepository.music.value?.meta?.id == id &&
            playerRepository.playlist.value?.abstr?.meta?.id == playlistId &&
            playbackResource != null
        ) {
            resume()
            return
        }

        clearPendingSeek()
        playbackJob?.cancel()
        playbackJob = scope.launch(mainDispatcher) {
            playerRepository.setIsLoading(true)
            try {
                val queuedPlaylist = playerRepository.playlist.value?.takeIf { queue ->
                    queue.abstr.meta.id == playlistId && queue.musics.any { it.meta.id == id }
                }
                playbackEngine.stop()
                releasePlaybackResource()
                playerRepository.setIsPlaying(false)
                val music = roomLibraryStore.getMusic(id)
                val playlist = queuedPlaylist ?: roomLibraryStore.getPlaylist(playlistId)
                val belongsToPlaylist = playlist?.musics?.any { it.meta.id == id } == true
                if (music == null || playlist == null || !belongsToPlaylist) {
                    playerRepository.resetCurrent()
                    return@launch
                }

                when (
                    val preparation = preparePlayback(
                        music = music,
                        playlistId = playlist.abstr.meta.id.value,
                        playbackResourceResolver = playbackResourceResolver,
                        playbackEngine = playbackEngine,
                        settingsRepository = settingsRepository,
                        networkStatusProvider = networkStatusProvider,
                    )
                ) {
                    is PlaybackPreparationResult.Ready -> {
                        playbackResource = preparation.resource
                        pendingNetworkRecovery = null
                    }
                    PlaybackPreparationResult.NetworkBlocked,
                    PlaybackPreparationResult.Failed -> {
                        pendingNetworkRecovery = id to playlistId
                        toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                        playerRepository.resetCurrent()
                        return@launch
                    }
                }

                if (
                    playerRepository.music.value?.meta?.id != id ||
                    playerRepository.playlist.value?.abstr?.meta?.id != playlistId
                ) {
                    playerRepository.setCurrent(music, playlist)
                }
                updateAudioDsp(currentSettings, music.meta.id.value)
                playbackEngine.play()
                playerRepository.setIsPlaying(true)
                playerRepository.notifyDurationChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val resource = playbackResource
                if (resource != null) {
                    playbackResource = null
                    playbackResourceResolver.release(resource)
                }
                AppLogger.error(
                    DiagnosticLogCategory.Playback,
                    "IosPlayerController",
                    "iOS playback failed",
                    error.stackTraceToString(),
                )
                toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                playerRepository.resetCurrent()
                playerRepository.setIsPlaying(false)
            } finally {
                playerRepository.setIsLoading(false)
            }
        }
    }

    override fun resume() {
        playbackEngine.play()
        playerRepository.setIsPlaying(true)
    }

    override fun pause() {
        playbackEngine.pause()
        playerRepository.setIsPlaying(false)
    }

    override fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        clearPendingSeek()
        playbackEngine.stop()
        AudioDspRuntimeMonitor.reset()
        releasePlaybackResourceAsync()
        playerRepository.setIsPlaying(false)
        playerRepository.resetCurrent()
    }

    override fun playNext() {
        val music = playerRepository.nextMusic.value
        val playlist = playerRepository.playlist.value
        if (music != null && playlist != null) {
            play(music.meta.id, playlist.abstr.meta.id)
        }
    }

    override fun playPrevious() {
        val music = playerRepository.previousMusic.value
        val playlist = playerRepository.playlist.value
        if (music != null && playlist != null) {
            play(music.meta.id, playlist.abstr.meta.id)
        }
    }

    override fun seek(ms: ULong) {
        val targetPositionMs = ms.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
        val generation = ++seekGeneration
        pendingSeekPositionMs = targetPositionMs
        playbackEngine.seekTo(targetPositionMs) {
            if (generation != seekGeneration) return@seekTo
            pendingSeekPositionMs = null
            playerRepository.notifyDurationChanged()
        }
    }

    override fun scheduleSleep(newExpiredMs: Long) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            sleep.update { it.copy(enabled = true, expiredMs = newExpiredMs) }
            delay(max(newExpiredMs - currentTimeMillis(), 0L))
            pause()
            sleep.update { it.copy(enabled = false, expiredMs = 0L) }
        }
    }

    override fun refreshPlaylistIfMatch(playlist: Playlist) {
        playerRepository.refreshPlaylistIfMatch(playlist)
    }

    override fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleep.update { it.copy(enabled = false, expiredMs = 0L) }
    }

    private fun clearPendingSeek() {
        seekGeneration += 1
        pendingSeekPositionMs = null
    }

    private suspend fun releasePlaybackResource() {
        val resource = playbackResource ?: return
        playbackResource = null
        playbackResourceResolver.release(resource)
    }

    private suspend fun updateAudioDsp(
        settings: AppSettings,
        trackId: Long? = playerRepository.music.value?.meta?.id?.value,
    ) {
        val replayGainDb = if (
            trackId == null ||
            settings.playbackAdvanced.replayGainMode == ReplayGainMode.Off
        ) {
            0f
        } else {
            val replayGain = withContext(Dispatchers.Default) {
                roomLibraryStore.getTrackReplayGain(trackId)
            }
            val (metadataGain, peak) = when (settings.playbackAdvanced.replayGainMode) {
                ReplayGainMode.Off -> null to null
                ReplayGainMode.Track -> replayGain?.trackGainDb to replayGain?.trackPeak
                ReplayGainMode.Album -> replayGain?.albumGainDb to replayGain?.albumPeak
                ReplayGainMode.Auto -> {
                    (replayGain?.trackGainDb ?: replayGain?.albumGainDb) to
                        (replayGain?.trackPeak ?: replayGain?.albumPeak)
                }
            }
            var gain = (metadataGain ?: 0.0) +
                settings.playbackAdvanced.replayGainPreampTenthsDb / 10.0
            if (peak != null && peak > 0.0) {
                gain = min(gain, -20.0 * log10(peak))
            }
            gain.toFloat()
        }
        playbackEngine.updateAudioDsp(settings.audioEffects, replayGainDb)
    }

    private fun releasePlaybackResourceAsync() {
        val resource = playbackResource ?: return
        playbackResource = null
        scope.launch {
            playbackResourceResolver.release(resource)
        }
    }
}
