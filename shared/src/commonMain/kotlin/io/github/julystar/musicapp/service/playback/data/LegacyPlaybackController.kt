package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.data.datastore.PersistedPlaybackSession
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.PlayNextMode
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.core.domain.model.StartupPlaybackMode
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.playback.data.PlayerController as LegacyPlayerController
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.singleton.PlaybackItemMetadata
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.PlayMode
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistId
import kotlin.time.Duration

class LegacyPlaybackController(
    private val playerRepository: PlayerRepository,
    private val legacyController: LegacyPlayerController,
    private val roomLibraryStore: RoomLibraryStore,
    private val scope: CoroutineScope,
    private val positionPollMillis: Long = 100,
    private val settingsRepository: SettingsRepository? = null,
) : PlaybackController {
    private val queueOrderKeyManager = QueueOrderKeyManager()
    private val immediatePositionRefreshes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
    )
    private val settings = settingsRepository?.settings?.stateIn(
        scope,
        SharingStarted.Eagerly,
        AppSettings.Default,
    )
    private val shuffleEnabled = MutableStateFlow(false)
    private val restoredSession = MutableStateFlow<PersistedPlaybackSession?>(null)
    private val requestedNext = mutableListOf<MusicAbstract>()

    init {
        scope.launch { restoreStartupPlayback() }
        scope.launch {
            playerRepository.music.collect { current ->
                val currentId = current?.meta?.id ?: return@collect
                requestedNext.removeAll { it.meta.id == currentId }
            }
        }
        scope.launch {
            combine(
                playerRepository.music,
                playerRepository.playlist,
            ) { music, playlist ->
                if (
                    music == null ||
                    playlist == null ||
                    playlist.musics.none { it.meta.id == music.meta.id }
                ) {
                    null
                } else {
                    PlaybackSessionIdentity(
                        trackId = music.meta.id.value,
                        playlistId = playlist.abstr.meta.id.value,
                        queueTrackIds = playlist.musics.map { item -> item.meta.id.value },
                    )
                }
            }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    if (restoredSession.value == null) {
                        playerRepository.savePlaybackSession(
                            positionMs = readPosition().positionMs,
                            wasPlaying = playerRepository.playing.value,
                        )
                    }
                }
        }
        scope.launch {
            while (isActive) {
                delay(2_000)
                val position = readPosition()
                if (restoredSession.value == null) {
                    playerRepository.savePlaybackSession(
                        positionMs = position.positionMs,
                        wasPlaying = state.value.status == PlaybackStatus.Playing,
                    )
                }
            }
        }
    }

    private val legacyState: StateFlow<PlayerState> = combine(
        playerRepository.music,
        playerRepository.playing,
        playerRepository.loading,
        playerRepository.playMode,
    ) { music, playing, loading, playMode ->
        legacyPlayerState(
            music = music,
            playing = playing,
            loading = loading,
            playMode = playMode,
            playlistId = playerRepository.playlist.value?.abstr?.meta?.id?.value,
        )
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState())

    override val state: StateFlow<PlayerState> = combine(
        legacyState,
        shuffleEnabled,
    ) { state, shuffled ->
        state.copy(shuffleEnabled = shuffled)
    }.stateIn(scope, SharingStarted.Eagerly, PlayerState())

    override val position: StateFlow<PlaybackPosition> = merge(
        flow {
            while (true) {
                emit(Unit)
                delay(positionPollMillis.coerceAtLeast(100))
            }
        },
        playerRepository.durationChanged,
        immediatePositionRefreshes,
        restoredSession.map { Unit },
    ).map {
        readPosition()
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        PlaybackPosition.Zero,
    )

    override val queue: StateFlow<PlaybackQueue> = combine(
        playerRepository.playlist,
        playerRepository.music,
    ) { playlist, music ->
        legacyPlaybackQueue(
            playlist = playlist,
            currentMusic = music,
        )
    }.withPlaybackItemMetadata(roomLibraryStore::getPlaybackItemMetadata)
        .stateIn(scope, SharingStarted.Eagerly, PlaybackQueue.Empty)

    override suspend fun play(
        items: List<PlayableItem>,
        startIndex: Int,
    ) {
        val item = items.getOrNull(startIndex) ?: return
        val musicId = item.libraryTrackId ?: return
        val playlistId = item.libraryPlaylistId
            ?: playerRepository.playlist.value?.abstr?.meta?.id?.value
            ?: return
        val saved = playerRepository.persistedPlaybackSession()
        val playlist = roomLibraryStore.getPlaylist(PlaylistId(playlistId))
            ?.forPlaybackItems(items)
            ?.takeIf { queue -> queue.musics.any { it.meta.id.value == musicId } }
            ?: return
        if (restoredSession.value != null) {
            restoredSession.value = null
            playerRepository.resetCurrent()
        }
        playerRepository.setPlaybackQueue(playlist)
        val playMode = playbackModeForQueue(
            current = playerRepository.playMode.value,
            queueSize = playlist.musics.size,
        )
        if (playMode != playerRepository.playMode.value) {
            playerRepository.setPlayMode(playMode)
        }
        val startPositionMs = if (
            settings?.value?.playbackAdvanced?.resumePlaybackPosition != false &&
            saved?.trackId == musicId &&
            saved.playlistId == playlistId &&
            saved.positionMs > 0L
        ) {
            saved.positionMs
        } else {
            0L
        }
        legacyController.play(
            MusicId(musicId),
            PlaylistId(playlistId),
            startPositionMs,
        )
    }

    override fun play() {
        val restored = restoredSession.value
        if (restored != null) {
            scope.launch {
                val resumePosition = settingsRepository
                    ?.settings
                    ?.first()
                    ?.playbackAdvanced
                    ?.resumePlaybackPosition
                    ?: true
                startRestoredPlayback(
                    session = restored,
                    resumePosition = resumePosition,
                )
            }
            return
        }
        legacyController.resume()
    }

    override fun pause() {
        restoredSession.value?.let { restored ->
            scope.launch { playerRepository.savePlaybackSession(restored.positionMs, false) }
            playerRepository.setIsPlaying(false)
            return
        }
        scope.launch { playerRepository.savePlaybackSession(readPosition().positionMs, false) }
        legacyController.pause()
    }

    override fun togglePlayPause() {
        if (state.value.status == PlaybackStatus.Playing) {
            pause()
        } else {
            play()
        }
    }

    override fun seekTo(positionMs: Long) {
        restoredSession.value?.let { restored ->
            val durationMs = playerRepository.music.value?.meta?.duration?.inWholeMilliseconds
            val updated = restored.copy(
                positionMs = restoredPlaybackPosition(positionMs, durationMs),
            )
            restoredSession.value = updated
            scope.launch { playerRepository.savePlaybackSession(updated.positionMs, false) }
            immediatePositionRefreshes.tryEmit(Unit)
            return
        }
        val targetPositionMs = positionMs.coerceAtLeast(0)
        legacyController.seek(targetPositionMs.toULong())
        scope.launch {
            playerRepository.savePlaybackSession(
                positionMs = targetPositionMs,
                wasPlaying = state.value.status == PlaybackStatus.Playing,
            )
        }
        immediatePositionRefreshes.tryEmit(Unit)
    }

    override fun skipNext() {
        restoredSession.value = null
        val advanced = settings?.value?.playbackAdvanced ?: PlaybackAdvancedSettings.Default
        if (shuffleEnabled.value && advanced.shuffleStrategy == ShuffleStrategy.TrueRandom) {
            val playlist = playerRepository.playlist.value ?: return
            val currentId = playerRepository.music.value?.meta?.id
            val next = playlist.musics.filterNot { it.meta.id == currentId }.randomOrNull() ?: return
            legacyController.play(next.meta.id, playlist.abstr.meta.id)
        } else {
            legacyController.playNext()
        }
    }

    override fun skipPrevious() {
        restoredSession.value = null
        legacyController.playPrevious()
    }

    override fun enqueueNext(item: PlayableItem) {
        val trackId = item.libraryTrackId ?: return
        scope.launch {
            val currentId = playerRepository.music.value?.meta?.id?.value ?: return@launch
            if (trackId == currentId) return@launch
            val music = playerRepository.musicAbstract(trackId) ?: return@launch
            requestedNext.removeAll { it.meta.id.value == trackId }
            when (settings?.value?.playbackAdvanced?.playNextMode ?: PlayNextMode.FirstRequestedFirst) {
                PlayNextMode.FirstRequestedFirst -> requestedNext.add(music)
                PlayNextMode.LastRequestedFirst -> requestedNext.add(0, music)
            }
            rebuildRequestedNextQueue()
        }
    }

    override fun setShuffle(enabled: Boolean) {
        if (shuffleEnabled.value == enabled) return
        shuffleEnabled.value = enabled
        requestedNext.clear()
        scope.launch {
            val strategy = settings?.value?.playbackAdvanced?.shuffleStrategy
                ?: ShuffleStrategy.QueueOrder
            if (!enabled || strategy == ShuffleStrategy.TrueRandom) {
                if (!enabled) playerRepository.restorePlaybackQueueOrder()
                return@launch
            }
            val playlist = playerRepository.playlist.value ?: return@launch
            val currentId = playerRepository.music.value?.meta?.id
            val current = playlist.musics.firstOrNull { it.meta.id == currentId }
            val shuffled = playlist.musics.filterNot { it.meta.id == currentId }.shuffled()
            playerRepository.replacePlaybackQueue(
                queueOrderKeyManager.rebalance(listOfNotNull(current) + shuffled),
            )
        }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        playerRepository.setPlayMode(
            mode.toLegacyPlayMode(queue.value.items.size)
        )
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val playlist = playerRepository.playlist.value ?: return
        val reordered = queueOrderKeyManager.move(
            items = playlist.musics,
            fromIndex = from,
            toIndex = to,
        )
        if (reordered == playlist.musics) return
        playerRepository.replacePlaybackQueue(reordered)
    }

    override fun removeQueueItem(index: Int) {
        val playlist = playerRepository.playlist.value ?: return
        if (index !in playlist.musics.indices) return
        val removingCurrent = playlist.musics[index].meta.id == playerRepository.music.value?.meta?.id
        val musics = playlist.musics.toMutableList().apply { removeAt(index) }
        requestedNext.removeAll { queued -> musics.none { it.meta.id == queued.meta.id } }
        playerRepository.replacePlaybackQueue(musics)
        if (removingCurrent) {
            restoredSession.value = null
            val replacement = musics.getOrNull(index) ?: musics.lastOrNull()
            if (replacement == null) {
                legacyController.stop()
                scope.launch { playerRepository.clearPlaybackSession() }
            } else {
                legacyController.play(replacement.meta.id, playlist.abstr.meta.id)
            }
            immediatePositionRefreshes.tryEmit(Unit)
        }
    }

    override fun clearQueue() {
        restoredSession.value = null
        legacyController.stop()
        scope.launch { playerRepository.clearPlaybackSession() }
        immediatePositionRefreshes.tryEmit(Unit)
    }

    private fun readPosition(): PlaybackPosition {
        val current = legacyPlaybackPosition(
            currentPositionMs = legacyController.getCurrentPosition(),
            bufferedPositionMs = legacyController.getBufferedPosition(),
            durationMs = legacyController.getDuration(),
            pendingSeekPositionMs = legacyController.getPendingSeekPosition(),
        )
        val restored = restoredSession.value ?: return current
        val currentMusic = playerRepository.music.value
        val currentPlaylist = playerRepository.playlist.value
        if (
            currentMusic?.meta?.id?.value != restored.trackId ||
            currentPlaylist?.abstr?.meta?.id?.value != restored.playlistId
        ) {
            return current
        }
        if (
            restoredPlaybackReadyForLivePosition(
                restoredPositionMs = restored.positionMs,
                livePositionMs = current.positionMs,
                playing = playerRepository.playing.value,
            )
        ) {
            if (restoredSession.value == restored) {
                restoredSession.value = null
            }
            return current
        }
        return current.withRestoredPlaybackPreview(
            positionMs = restored.positionMs,
            durationMs = currentMusic.meta.duration?.inWholeMilliseconds,
        )
    }

    private suspend fun restoreStartupPlayback() {
        val advanced = settingsRepository?.settings?.first()?.playbackAdvanced
            ?: PlaybackAdvancedSettings.Default
        val saved = playerRepository.persistedPlaybackSession()
        val restored = saved?.let { restorePlaybackPreview(it) }
        if (saved != null && restored == null) {
            playerRepository.clearPlaybackSession()
        }

        when (advanced.startupPlaybackMode) {
            StartupPlaybackMode.Off -> Unit
            StartupPlaybackMode.ResumeLastQueue -> restored?.let { session ->
                startRestoredPlayback(
                    session = session,
                    resumePosition = advanced.resumePlaybackPosition,
                )
            }
            StartupPlaybackMode.ShuffleLibrary -> {
                playerRepository.randomTrackInPlaylist(LIBRARY_PLAYBACK_PLAYLIST_ID)?.let { trackId ->
                    restoredSession.value = null
                    playerRepository.resetCurrent()
                    legacyController.play(
                        MusicId(trackId),
                        PlaylistId(LIBRARY_PLAYBACK_PLAYLIST_ID),
                    )
                }
            }
        }
    }

    private suspend fun restorePlaybackPreview(
        saved: PersistedPlaybackSession,
    ): PersistedPlaybackSession? {
        val music = roomLibraryStore.getMusic(MusicId(saved.trackId)) ?: return null
        val storedPlaylist = roomLibraryStore.getPlaylist(PlaylistId(saved.playlistId)) ?: return null
        val playlist = if (saved.queueTrackIds != null) {
            storedPlaylist.forPlaybackTrackIds(saved.queueTrackIds) ?: return null
        } else {
            storedPlaylist
        }
        if (playlist.musics.none { it.meta.id.value == saved.trackId }) return null

        val restored = saved.copy(
            positionMs = restoredPlaybackPosition(
                positionMs = saved.positionMs,
                durationMs = music.meta.duration?.inWholeMilliseconds,
            ),
            wasPlaying = false,
        )
        restoredSession.value = restored
        playerRepository.setCurrent(music, playlist)
        playerRepository.setIsPlaying(false)
        playerRepository.notifyDurationChanged()
        return restored
    }

    private suspend fun startRestoredPlayback(
        session: PersistedPlaybackSession,
        resumePosition: Boolean,
    ) {
        if (restoredSession.value != session) return
        legacyController.play(
            MusicId(session.trackId),
            PlaylistId(session.playlistId),
            if (resumePosition) session.positionMs else 0L,
        )
    }

    private fun rebuildRequestedNextQueue() {
        val playlist = playerRepository.playlist.value ?: return
        val currentId = playerRepository.music.value?.meta?.id ?: return
        val requestedIds = requestedNext.map { it.meta.id }.toSet()
        val base = playlist.musics.filterNot { it.meta.id in requestedIds }.toMutableList()
        val currentIndex = base.indexOfFirst { it.meta.id == currentId }
        if (currentIndex < 0) return
        base.addAll(currentIndex + 1, requestedNext)
        playerRepository.replacePlaybackQueue(base)
    }
}

internal fun Playlist.forPlaybackItems(items: List<PlayableItem>): Playlist? {
    return forPlaybackTrackIds(items.mapNotNull(PlayableItem::libraryTrackId))
}

internal fun Playlist.forPlaybackTrackIds(trackIds: List<Long>): Playlist? {
    val musicsById = musics.associateBy { music -> music.meta.id.value }
    val queue = trackIds.mapNotNull(musicsById::get)
    if (queue.isEmpty()) return null
    return copy(
        abstr = abstr.copy(
            musicCount = queue.size.toULong(),
            duration = queue.totalDuration(),
        ),
        musics = queue,
    )
}

private data class PlaybackSessionIdentity(
    val trackId: Long,
    val playlistId: Long,
    val queueTrackIds: List<Long>,
)

internal fun playbackModeForQueue(current: PlayMode, queueSize: Int): PlayMode {
    return if (current == PlayMode.SINGLE && queueSize > 1) PlayMode.LIST else current
}

private fun List<MusicAbstract>.totalDuration(): Duration? {
    var total = Duration.ZERO
    for (music in this) {
        total += music.meta.duration ?: return null
    }
    return total
}

internal fun legacyPlaybackPosition(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    pendingSeekPositionMs: Long?,
): PlaybackPosition {
    return PlaybackPosition(
        positionMs = (pendingSeekPositionMs ?: currentPositionMs).coerceAtLeast(0),
        bufferedMs = bufferedPositionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        isSeeking = pendingSeekPositionMs != null,
    )
}

internal fun restoredPlaybackPosition(positionMs: Long, durationMs: Long?): Long {
    val maximum = durationMs?.takeIf { it > 0L } ?: Long.MAX_VALUE
    return positionMs.coerceIn(0L, maximum)
}

internal fun restoredPlaybackReadyForLivePosition(
    restoredPositionMs: Long,
    livePositionMs: Long,
    playing: Boolean,
): Boolean {
    if (!playing) return false
    val restored = restoredPositionMs.coerceAtLeast(0L)
    if (restored == 0L) return true
    return livePositionMs >= restored
}

internal fun PlaybackPosition.withRestoredPlaybackPreview(
    positionMs: Long,
    durationMs: Long?,
): PlaybackPosition {
    val previewDurationMs = this.durationMs.takeIf { it > 0L }
        ?: durationMs?.coerceAtLeast(0L)
        ?: 0L
    return copy(
        positionMs = restoredPlaybackPosition(positionMs, previewDurationMs),
        durationMs = previewDurationMs,
    )
}

internal fun legacyPlayerState(
    music: Music?,
    playing: Boolean,
    loading: Boolean,
    playMode: PlayMode,
    playlistId: Long?,
): PlayerState {
    val currentItem = music?.toPlayableItem(playlistId)
    return PlayerState(
        currentItem = currentItem,
        status = when {
            loading -> PlaybackStatus.Loading
            currentItem == null -> PlaybackStatus.Idle
            playing -> PlaybackStatus.Playing
            else -> PlaybackStatus.Paused
        },
        repeatMode = playMode.toRepeatMode(),
        shuffleEnabled = false,
    )
}

internal fun legacyPlaybackQueue(
    playlist: Playlist?,
    currentMusic: Music?,
): PlaybackQueue {
    if (playlist == null) return PlaybackQueue.Empty
    val playlistId = playlist.abstr.meta.id.value
    val items = playlist.musics.map { music ->
        music.toPlayableItem(playlistId)
    }
    val currentIndex = currentMusic?.meta?.id?.value?.let { currentId ->
        playlist.musics.indexOfFirst { music -> music.meta.id.value == currentId }
    } ?: -1
    return PlaybackQueue(
        items = items,
        currentIndex = currentIndex,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<PlaybackQueue>.withPlaybackItemMetadata(
    metadataForTrack: suspend (Long) -> PlaybackItemMetadata,
): Flow<PlaybackQueue> = flow {
    val metadataCache = mutableMapOf<Long, PlaybackItemMetadata>()
    var previousQueue: PlaybackQueue? = null

    this@withPlaybackItemMetadata.transformLatest { queue ->
        val selectionOnlyUpdate = previousQueue?.let { previous ->
            previous.items == queue.items && previous.currentIndex != queue.currentIndex
        } == true
        previousQueue = queue
        val queuedTrackIds = queue.items.mapNotNull(PlayableItem::libraryTrackId).toSet()
        metadataCache.keys.retainAll(queuedTrackIds)

        val cachedItems = queue.items.map { item ->
            val trackId = item.libraryTrackId ?: return@map item
            val metadata = metadataCache[trackId] ?: return@map item
            item.withMetadata(metadata)
        }
        emit(queue.copy(items = cachedItems))

        val enrichedItems = queue.items.map { item ->
            val trackId = item.libraryTrackId ?: return@map item
            val metadata = if (selectionOnlyUpdate && metadataCache.containsKey(trackId)) {
                metadataCache.getValue(trackId)
            } else {
                metadataForTrack(trackId).also { metadataCache[trackId] = it }
            }
            item.withMetadata(metadata)
        }
        if (enrichedItems != cachedItems) {
            emit(queue.copy(items = enrichedItems))
        }
    }.collect { queue -> emit(queue) }
}

private fun PlayableItem.withMetadata(metadata: PlaybackItemMetadata): PlayableItem = copy(
    artist = metadata.artist,
    album = metadata.album,
)

private fun PlayMode.toRepeatMode(): RepeatMode {
    return when (this) {
        PlayMode.SINGLE,
        PlayMode.LIST -> RepeatMode.Off
        PlayMode.SINGLE_LOOP -> RepeatMode.One
        PlayMode.LIST_LOOP -> RepeatMode.All
    }
}

private fun RepeatMode.toLegacyPlayMode(queueSize: Int): PlayMode {
    return when (this) {
        RepeatMode.Off -> if (queueSize > 1) PlayMode.LIST else PlayMode.SINGLE
        RepeatMode.One -> PlayMode.SINGLE_LOOP
        RepeatMode.All -> PlayMode.LIST_LOOP
    }
}
