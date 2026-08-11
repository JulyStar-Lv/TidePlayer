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
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.app_backend.MusicId
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistId
import kotlin.math.max
import kotlin.math.log10
import kotlin.math.min

class DesktopPlayerController(
    private val playerRepository: PlayerRepository,
    private val toastRepository: ToastRepositoryImpl,
    private val playlistRepository: PlaylistRepositoryImpl,
    private val storageRepository: StorageRepositoryImpl,
    private val roomLibraryStore: RoomLibraryStore,
    private val playbackResourceResolver: PlaybackResourceResolver,
    private val playbackEngine: DesktopPlaybackEngine,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository? = null,
    private val networkStatusProvider: NetworkStatusProvider? = null,
) : PlayerController {
    private val sleep = MutableStateFlow(SleepModeState())
    private var sleepJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackResource: PlaybackResource? = null
    private var pendingNetworkRecovery: Pair<MusicId, PlaylistId>? = null
    private var currentSettings: AppSettings = AppSettings.Default
    private var crossfadeAdvancedTrackId: Long? = null

    override val sleepState: StateFlow<SleepModeState> = sleep.asStateFlow()

    init {
        settingsRepository?.let { repository ->
            scope.launch {
                repository.settings.collect { settings ->
                    currentSettings = settings
                    playerRepository.music.value?.meta?.id?.value?.let { trackId ->
                        configureAudioProcessing(trackId)
                    }
                }
            }
        }
        scope.launch {
            playerRepository.pauseRequest.collect { pause() }
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
        scope.launch {
            while (true) {
                delay(25)
                if (playbackEngine.takePlaybackCompleted()) {
                    playOnCompletion()
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
            while (true) {
                delay(100)
                val currentId = playerRepository.music.value?.meta?.id?.value
                if (currentId == null || !playerRepository.playing.value) {
                    crossfadeAdvancedTrackId = null
                    continue
                }
                val crossfadeDurationMs = currentSettings.playbackAdvanced.crossfadeDurationMs.toLong()
                if (crossfadeDurationMs <= 0L) continue
                val position = playbackEngine.readPosition()
                if (position.durationMs <= 0L) continue
                val nextMusic = playerRepository.onCompleteMusic.value
                val playlist = playerRepository.playlist.value
                if (
                    nextMusic != null &&
                    playlist != null &&
                    nextMusic.meta.id.value != currentId &&
                    position.positionMs >= (position.durationMs - crossfadeDurationMs).coerceAtLeast(0L) &&
                    crossfadeAdvancedTrackId != currentId
                ) {
                    crossfadeAdvancedTrackId = currentId
                    play(nextMusic.meta.id, playlist.abstr.meta.id)
                }
            }
        }
    }

    override fun getCurrentPosition(): Long = playbackEngine.readPosition().positionMs

    override fun getBufferedPosition(): Long = playbackEngine.readPosition().bufferedMs

    override fun getDuration(): Long = playbackEngine.readPosition().durationMs

    override fun play(id: MusicId, playlistId: PlaylistId) {
        play(id, playlistId, forceReload = false, allowTransition = true)
    }

    private fun play(
        id: MusicId,
        playlistId: PlaylistId,
        forceReload: Boolean,
        allowTransition: Boolean,
    ) {
        if (
            !forceReload &&
            playerRepository.music.value?.meta?.id == id &&
            playerRepository.playlist.value?.abstr?.meta?.id == playlistId &&
            playbackResource != null
        ) {
            resume()
            return
        }

        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.Main) {
            playerRepository.setIsLoading(true)
            val transitionDurationMs = currentSettings.playbackAdvanced.crossfadeDurationMs
            val canTransition = allowTransition &&
                transitionDurationMs > 0 &&
                playerRepository.playing.value
            val previousResource = playbackResource.takeIf { canTransition }
            try {
                val queuedPlaylist = playerRepository.playlist.value?.takeIf { queue ->
                    queue.abstr.meta.id == playlistId && queue.musics.any { it.meta.id == id }
                }
                stopForPlayback(canTransition)

                val music = roomLibraryStore.getMusic(id)
                val playlist = queuedPlaylist ?: roomLibraryStore.getPlaylist(playlistId)
                val belongsToPlaylist = playlist?.musics?.any { it.meta.id == id } == true
                if (music == null || playlist == null || !belongsToPlaylist) {
                    if (!canTransition) playerRepository.resetCurrent()
                    return@launch
                }

                configureAudioProcessing(id.value)

                val preparation = withContext(Dispatchers.IO) {
                    preparePlayback(
                        music = music,
                        playlistId = playlist.abstr.meta.id.value,
                        playbackResourceResolver = playbackResourceResolver,
                        playbackEngine = playbackEngine,
                        settingsRepository = settingsRepository,
                        networkStatusProvider = networkStatusProvider,
                    )
                }
                when (preparation) {
                    is PlaybackPreparationResult.Ready -> {
                        playbackResource = preparation.resource
                        pendingNetworkRecovery = null
                        if (
                            playerRepository.music.value?.meta?.id != id ||
                            playerRepository.playlist.value?.abstr?.meta?.id != playlistId
                        ) {
                            playerRepository.setCurrent(music, playlist)
                        }
                        playbackEngine.play()
                        playerRepository.setIsPlaying(true)
                        crossfadeAdvancedTrackId = null
                        playerRepository.notifyDurationChanged()
                        previousResource?.let { resource ->
                            scope.launch {
                                delay(transitionDurationMs.toLong())
                                playbackResourceResolver.release(resource)
                            }
                        }
                    }
                    PlaybackPreparationResult.NetworkBlocked,
                    PlaybackPreparationResult.Failed -> {
                        pendingNetworkRecovery = id to playlistId
                        toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                        if (!canTransition) {
                            playerRepository.resetCurrent()
                            playerRepository.setIsLoading(false)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!canTransition) releasePlaybackResource()
                AppLogger.error(
                    DiagnosticLogCategory.Playback,
                    "DesktopPlayerController",
                    "Desktop playback failed",
                    error.stackTraceToString(),
                )
                toastRepository.emit(UiMessageKey.UnableToOpenAudioStream)
                if (!canTransition) {
                    playerRepository.resetCurrent()
                    playerRepository.setIsPlaying(false)
                }
            } finally {
                playerRepository.setIsLoading(false)
            }
        }
    }

    override fun resume() {
        if (playbackResource == null) return
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

    private fun playOnCompletion() {
        if (!playerRepository.playing.value) return
        val music = playerRepository.onCompleteMusic.value
        val playlist = playerRepository.playlist.value
        if (music != null && playlist != null) {
            play(
                id = music.meta.id,
                playlistId = playlist.abstr.meta.id,
                forceReload = true,
                allowTransition = false,
            )
        } else {
            playerRepository.setIsPlaying(false)
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
        if (playbackResource == null) return
        playbackEngine.seekTo(ms.coerceAtMost(Long.MAX_VALUE.toULong()).toLong())
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

    private suspend fun stopForPlayback(allowTransition: Boolean) {
        if (allowTransition) return
        playbackEngine.stop()
        releasePlaybackResource()
        playerRepository.setIsPlaying(false)
    }

    private suspend fun configureAudioProcessing(trackId: Long) {
        val settings = currentSettings
        val replayGain = roomLibraryStore.getTrackReplayGain(trackId)
        val (metadataGain, peak) = when (settings.playbackAdvanced.replayGainMode) {
            ReplayGainMode.Off -> null to null
            ReplayGainMode.Track -> replayGain?.trackGainDb to replayGain?.trackPeak
            ReplayGainMode.Album -> replayGain?.albumGainDb to replayGain?.albumPeak
            ReplayGainMode.Auto -> {
                (replayGain?.trackGainDb ?: replayGain?.albumGainDb) to
                    (replayGain?.trackPeak ?: replayGain?.albumPeak)
            }
        }
        var gainDb = (metadataGain ?: 0.0) +
            settings.playbackAdvanced.replayGainPreampTenthsDb / 10.0
        if (peak != null && peak > 0.0) {
            gainDb = min(gainDb, -20.0 * log10(peak))
        }
        playbackEngine.configureAudioProcessing(
            effects = settings.audioEffects,
            playback = settings.playbackAdvanced,
            replayGainDb = gainDb.toFloat(),
        )
    }

    private suspend fun releasePlaybackResource() {
        val resource = playbackResource ?: return
        playbackResource = null
        playbackResourceResolver.release(resource)
    }

    private fun releasePlaybackResourceAsync() {
        val resource = playbackResource ?: return
        playbackResource = null
        scope.launch {
            playbackResourceResolver.release(resource)
        }
    }
}
