package io.github.julystar.musicapp.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.WAKE_MODE_NETWORK
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.julystar.musicapp.core.audio.RustDspAudioProcessor
import io.github.julystar.musicapp.core.audio.AudioDspRuntimeMonitor
import io.github.julystar.musicapp.core.audio.AudioReactiveMonitor
import io.github.julystar.musicapp.core.audio.AUDIO_DSP_DIAGNOSTICS_INTERVAL_MS
import io.github.julystar.musicapp.core.audio.AUDIO_REACTIVE_VISUALIZATION_INTERVAL_MS
import io.github.julystar.musicapp.core.audio.Media3AudioRenderersFactory
import io.github.julystar.musicapp.core.audio.monitoringRequested
import io.github.julystar.musicapp.core.audio.toDomainAudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.ReplayGainMode
import io.github.julystar.musicapp.core.domain.model.AudioReactiveSnapshot
import io.github.julystar.musicapp.core.domain.repository.AudioMonitoringRequester
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.data.toPlaybackArtwork
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.shared.R
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.Playlist
import kotlin.math.log10
import kotlin.math.min

const val PLAYER_TO_PREV_COMMAND = "PLAYER_TO_PREV_COMMAND"
const val PLAYER_TO_NEXT_COMMAND = "PLAYER_TO_NEXT_COMMAND"
const val PLAYER_TOGGLE_FAVORITE_COMMAND = "PLAYER_TOGGLE_FAVORITE_COMMAND"
const val PLAYER_CYCLE_PLAYBACK_MODE_COMMAND = "PLAYER_CYCLE_PLAYBACK_MODE_COMMAND"

class PlaybackService : MediaLibraryService() {
    private val playerRepository: PlayerRepository by inject()
    private val playbackController: PlaybackController by inject()
    private val artworkRepository: ArtworkRepository by inject()
    private val favoritesRepository: FavoritesRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val networkStatusProvider: NetworkStatusProvider by inject()
    private val roomLibraryStore: RoomLibraryStore by inject()
    private val playbackResourceResolver: PlaybackResourceResolver by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var _mediaSession: MediaLibrarySession? = null
    private var audioFocusController: PlaybackAudioFocusController? = null
    private var lyricOutputController: AndroidLyricOutputController? = null
    private var dspAudioProcessor: RustDspAudioProcessor? = null
    private var currentSettings = AppSettings.Default
    private var favoriteTrackIds: Set<Long> = emptySet()
    private val notificationLyrics = AndroidNotificationLyrics()
    private val previousCommand = SessionCommand(PLAYER_TO_PREV_COMMAND, Bundle.EMPTY)
    private val nextCommand = SessionCommand(PLAYER_TO_NEXT_COMMAND, Bundle.EMPTY)
    private val toggleFavoriteCommand = SessionCommand(PLAYER_TOGGLE_FAVORITE_COMMAND, Bundle.EMPTY)
    private val cyclePlaybackModeCommand =
        SessionCommand(PLAYER_CYCLE_PLAYBACK_MODE_COMMAND, Bundle.EMPTY)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlaybackService",
            "Playback service creating",
        )
        setMediaNotificationProvider(
            object : DefaultMediaNotificationProvider(this) {
                init {
                    setSmallIcon(R.drawable.notification_small_icon)
                }

                override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
                    notificationLyrics.resolveContentTitle(metadata)
            }
        )
        val context = this

        val intent = Intent(this, Class.forName("io.github.julystar.musicapp.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dspProcessor = RustDspAudioProcessor().also {
            it.updateSettings(AppSettings.Default.audioEffects)
            dspAudioProcessor = it
        }
        val resolvingDataSourceFactory = AndroidPlaybackDataSourceFactory(
            upstreamFactory = DefaultDataSource.Factory(context),
            roomLibraryStore = roomLibraryStore,
            playbackResourceResolver = playbackResourceResolver,
            settingsRepository = settingsRepository,
            networkStatusProvider = networkStatusProvider,
        )
        val player = ExoPlayer.Builder(
            context,
            Media3AudioRenderersFactory(context, dspProcessor),
        )
            .setAudioAttributes(
                mediaAudioAttributes(),
                false,
            )
            .setHandleAudioBecomingNoisy(AppSettings.Default.pauseOnDisconnect)
            .setWakeMode(WAKE_MODE_NETWORK)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(resolvingDataSourceFactory))
            .build()
        serviceScope.launch(Dispatchers.Default) {
            AudioDspRuntimeMonitor.monitoringRequesters
                .monitoringRequested(AudioMonitoringRequester.Diagnostics)
                .collectLatest { monitoringEnabled ->
                if (monitoringEnabled) {
                    while (isActive) {
                        dspAudioProcessor?.runtimeSnapshot()?.let { snapshot ->
                            AudioDspRuntimeMonitor.publish(snapshot.toDomainAudioDspRuntimeSnapshot())
                        } ?: AudioDspRuntimeMonitor.reset()
                        delay(AUDIO_DSP_DIAGNOSTICS_INTERVAL_MS)
                    }
                } else AudioDspRuntimeMonitor.reset()
            }
        }
        serviceScope.launch(Dispatchers.Default) {
            AudioDspRuntimeMonitor.monitoringRequesters
                .monitoringRequested(AudioMonitoringRequester.Visualization)
                .collectLatest { monitoringEnabled ->
                if (monitoringEnabled) {
                    try {
                        var reactiveSampleCount = 0
                        while (isActive) {
                            if (playerRepository.playing.value) {
                                val reactiveSnapshot = dspAudioProcessor?.audioReactiveSnapshot()
                                    ?: AudioReactiveSnapshot()
                                AudioReactiveMonitor.publish(reactiveSnapshot)
                                if (reactiveSampleCount++ % 30 == 0) {
                                    AppLogger.debug(
                                        category = DiagnosticLogCategory.Dsp,
                                        target = "PlaybackService",
                                        message = "Temporary audio reactive sample",
                                        fields = mapOf(
                                            "level" to reactiveSnapshot.level.toString(),
                                            "beat" to reactiveSnapshot.beat.toString(),
                                        ),
                                    )
                                }
                            } else {
                                AudioReactiveMonitor.reset()
                            }
                            delay(AUDIO_REACTIVE_VISUALIZATION_INTERVAL_MS)
                        }
                    } finally {
                        AudioReactiveMonitor.reset()
                    }
                } else AudioReactiveMonitor.reset()
            }
        }
        val sessionPlayer = TidePlayerSessionPlayer(
            player = player,
            onNextBoundary = ::playNext,
            onPreviousBoundary = ::playPrevious,
        )
        audioFocusController = PlaybackAudioFocusController(this, player).apply {
            updateMode(AppSettings.Default.audioFocusMode)
        }
        _mediaSession = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            object : MediaLibrarySession.Callback {
                @OptIn(UnstableApi::class)
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    if (session.isMediaNotificationController(controller)) {
                        val sessionCommands =
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(previousCommand)
                                .add(nextCommand)
                                .add(toggleFavoriteCommand)
                                .add(cyclePlaybackModeCommand)
                                .build()
                        val playerCommands =
                            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_TO_NEXT)
                                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .remove(Player.COMMAND_SEEK_BACK)
                                .remove(Player.COMMAND_SEEK_FORWARD)
                                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                                .build()
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setMediaButtonPreferences(buildMediaButtonPreferences(session.player))
                            .setAvailablePlayerCommands(playerCommands)
                            .setAvailableSessionCommands(sessionCommands)
                            .build()
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                }

                @Suppress("DEPRECATION")
                @OptIn(UnstableApi::class)
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent,
                ): Boolean {
                    val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false
                    if (!event.keyCode.isHandledPlaybackMediaKey()) return false

                    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
                        return true
                    }

                    val sessionPlayer = session.player
                    AppLogger.info(
                        DiagnosticLogCategory.Playback,
                        "PlaybackService",
                        "Media button received: ${KeyEvent.keyCodeToString(event.keyCode)} " +
                            "from ${controllerInfo.packageName}",
                    )

                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            if (sessionPlayer.playWhenReady) sessionPlayer.pause() else sessionPlayer.play()
                        }

                        KeyEvent.KEYCODE_MEDIA_PLAY -> sessionPlayer.play()

                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_STOP -> sessionPlayer.pause()

                        KeyEvent.KEYCODE_MEDIA_NEXT -> sessionPlayer.seekToNextMediaItem()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> sessionPlayer.seekToPreviousMediaItem()

                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD ->
                            seekPlayerBy(sessionPlayer, MEDIA_SEEK_INTERVAL_MS)

                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD ->
                            seekPlayerBy(sessionPlayer, -MEDIA_SEEK_INTERVAL_MS)
                    }
                    return true
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    return when (customCommand.customAction) {
                        PLAYER_TO_PREV_COMMAND -> {
                            session.player.seekToPreviousMediaItem()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        PLAYER_TO_NEXT_COMMAND -> {
                            session.player.seekToNextMediaItem()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        PLAYER_TOGGLE_FAVORITE_COMMAND -> {
                            toggleCurrentFavorite()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        PLAYER_CYCLE_PLAYBACK_MODE_COMMAND -> {
                            cyclePlaybackMode()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        else -> super.onCustomCommand(session, controller, customCommand, args)
                    }
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> {
                    val playlist = playerRepository.playlist.value
                    val hydrated = mediaItems.mapNotNull { item ->
                        item.takeIf { it.localConfiguration != null }
                            ?: androidCurrentQueueItem(playlist, item.mediaId)
                    }
                    return Futures.immediateFuture(hydrated)
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val appName = applicationInfo.loadLabel(packageManager).toString()
                    return Futures.immediateFuture(
                        LibraryResult.ofItem(androidLibraryRoot(appName), params)
                    )
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val playlist = playerRepository.playlist.value
                    val item = when (mediaId) {
                        ANDROID_LIBRARY_ROOT_ID -> {
                            val appName = applicationInfo.loadLabel(packageManager).toString()
                            androidLibraryRoot(appName)
                        }
                        ANDROID_LIBRARY_CURRENT_QUEUE_ID -> androidCurrentQueueFolder(playlist)
                        else -> androidCurrentQueueItem(playlist, mediaId)
                    }
                    return Futures.immediateFuture(
                        if (item != null) {
                            LibraryResult.ofItem(item, null)
                        } else {
                            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                        }
                    )
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    val playlist = playerRepository.playlist.value
                    val children = when (parentId) {
                        ANDROID_LIBRARY_ROOT_ID -> listOf(androidCurrentQueueFolder(playlist))
                        ANDROID_LIBRARY_CURRENT_QUEUE_ID ->
                            androidCurrentQueueItems(playlist, page, pageSize)
                        else -> {
                            return Futures.immediateFuture(
                                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                            )
                        }
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(children, params)
                    )
                }

                override fun onSubscribe(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<Void>> {
                    return Futures.immediateFuture(LibraryResult.ofVoid(params))
                }
            },
        )
            .setSessionActivity(pendingIntent)
            .build()
        lyricOutputController = AndroidLyricOutputController(
            context = this,
            settingsRepository = settingsRepository,
            playerRepository = playerRepository,
            roomLibraryStore = roomLibraryStore,
            scope = serviceScope,
            playerProvider = { _mediaSession?.player },
            notificationLyrics = notificationLyrics,
            refreshMediaNotification = ::refreshMediaNotification,
        )

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerRepository.setIsPlaying(isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) audioFocusController?.requestFocus()
            }

            override fun onPlayerError(error: PlaybackException) {
                playerRepository.setIsLoading(false)
                playerRepository.setIsPlaying(false)
                AppLogger.error(
                    DiagnosticLogCategory.Playback,
                    "PlaybackService",
                    "Media3 playback failed for mediaId=${player.currentMediaItem?.mediaId.orEmpty()}",
                    error.toString(),
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull() ?: return
                syncApplicationStateFromMedia3(player, trackId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playerRepository.setIsPlaying(false)
                    playOnComplete()
                } else if (playbackState == Player.STATE_READY) {
                    playerRepository.setIsLoading(false)
                    syncMetadataUtil(serviceScope, playerRepository, player)
                } else if (playbackState == Player.STATE_BUFFERING) {
                    playerRepository.setIsLoading(true)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                playerRepository.notifyDurationChanged()
                dspAudioProcessor?.resetDspState()
            }
        })
        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlaybackService",
            "Playback service created",
        )

        serviceScope.launch(Dispatchers.Main) {
            settingsRepository.settings.collect { settings ->
                currentSettings = settings
                audioFocusController?.updateMode(settings.audioFocusMode)
                player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
                updateAudioDsp(settings)
            }
        }

        serviceScope.launch(Dispatchers.Main) {
            favoritesRepository.favoriteTrackIds.collect { trackIds ->
                favoriteTrackIds = trackIds
                updateMediaButtonPreferences()
            }
        }

        serviceScope.launch(Dispatchers.Main) {
            playbackController.state.collect { state ->
                player.repeatMode = if (state.repeatMode == RepeatMode.One) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
                updateMediaButtonPreferences()
            }
        }

        serviceScope.launch(Dispatchers.Main) {
            playerRepository.playlist.collect { playlist ->
                _mediaSession?.notifyChildrenChanged(
                    ANDROID_LIBRARY_ROOT_ID,
                    1,
                    null,
                )
                _mediaSession?.notifyChildrenChanged(
                    ANDROID_LIBRARY_CURRENT_QUEUE_ID,
                    playlist?.musics?.size ?: 0,
                    null,
                )

                val currentTrackId = playerRepository.music.value?.meta?.id?.value
                if (
                    playlist != null &&
                    currentTrackId != null &&
                    player.currentMediaItem?.mediaId == currentTrackId.toString()
                ) {
                    synchronizeMedia3QueueWithPlaylist(player, playlist, currentTrackId)
                }
            }
        }

        serviceScope.launch(Dispatchers.Main) {
            playerRepository.pauseRequest.collect {
                val sessionPlayer = _mediaSession?.player ?: return@collect

                if (sessionPlayer.isCommandAvailable(COMMAND_PLAY_PAUSE)) {
                    sessionPlayer.pause()
                } else {
                    AppLogger.warn(
                        DiagnosticLogCategory.Playback,
                        "PlaybackService",
                        "Pause command is unavailable",
                    )
                }
            }
        }

        serviceScope.launch(Dispatchers.Main) {
            playerRepository.music.collectLatest { music ->
                music ?: return@collectLatest
                val artworkData = withContext(Dispatchers.IO) {
                    artworkRepository.load(music.toPlaybackArtwork())
                } ?: return@collectLatest
                val latestItem = player.currentMediaItem ?: return@collectLatest
                if (latestItem.mediaId != music.meta.id.value.toString()) return@collectLatest
                if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                    return@collectLatest
                }
                player.replaceMediaItem(
                    player.currentMediaItemIndex,
                    latestItem.buildUpon()
                        .setMediaMetadata(latestItem.mediaMetadata.withArtworkData(artworkData))
                        .build(),
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return _mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        _mediaSession?.player?.stop()
        _mediaSession?.player?.release()
        dspAudioProcessor?.close()
        dspAudioProcessor = null
        AudioDspRuntimeMonitor.reset()
        AudioReactiveMonitor.reset()
        lyricOutputController?.destroy()
        lyricOutputController = null
        audioFocusController?.release()
        audioFocusController = null
        _mediaSession?.release()
        _mediaSession = null
        serviceScope.cancel()
    }

    fun play(musicAbstract: MusicAbstract, playlist: Playlist) {
        val player = _mediaSession?.player ?: return
        serviceScope.launch {
            val music = roomLibraryStore.getMusic(musicAbstract.meta.id) ?: return@launch
            val window = buildAndroidMediaQueueWindow(
                playlist = playlist,
                currentTrackId = music.meta.id.value,
            ) ?: return@launch

            playerRepository.setCurrent(music, playlist)
            updateAudioDsp(currentSettings, music.meta.id.value)
            player.setMediaItems(window.mediaItems, window.currentIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    private fun syncApplicationStateFromMedia3(player: Player, trackId: Long) {
        serviceScope.launch {
            val playlist = playerRepository.playlist.value ?: return@launch
            if (playlist.musics.none { music -> music.meta.id.value == trackId }) return@launch
            val music = roomLibraryStore.getMusic(MusicId(trackId)) ?: return@launch
            if (player.currentMediaItem?.mediaId != trackId.toString()) return@launch
            if (playerRepository.music.value?.meta?.id?.value != trackId) {
                playerRepository.setCurrent(music, playlist)
            }
            updateAudioDsp(currentSettings, trackId)
            playerRepository.notifyDurationChanged()
            recenterMedia3QueueIfNeeded(player, playlist, trackId)
        }
    }

    @OptIn(UnstableApi::class)
    private fun refreshMediaNotification() {
        triggerNotificationUpdate()
    }

    private fun recenterMedia3QueueIfNeeded(
        player: Player,
        playlist: Playlist,
        trackId: Long,
    ) {
        val localIndex = player.currentMediaItemIndex
        val localCount = player.mediaItemCount
        if (localIndex < 0 || localCount <= 0) return
        val globalIndex = playlist.musics.indexOfFirst { music -> music.meta.id.value == trackId }
        if (globalIndex < 0) return

        val hasMoreBefore = globalIndex > 0
        val hasMoreAfter = globalIndex < playlist.musics.lastIndex
        val nearStart = localIndex <= MEDIA_QUEUE_RECENTER_THRESHOLD && hasMoreBefore
        val nearEnd = localIndex >= localCount - 1 - MEDIA_QUEUE_RECENTER_THRESHOLD && hasMoreAfter
        if (!nearStart && !nearEnd) return

        val window = buildAndroidMediaQueueWindow(
            playlist = playlist,
            currentTrackId = trackId,
        ) ?: return
        if (
            window.mediaItems.size == localCount &&
            window.currentIndex == localIndex &&
            window.mediaItems.firstOrNull()?.mediaId == player.getMediaItemAt(0).mediaId
        ) {
            return
        }

        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        player.setMediaItems(window.mediaItems, window.currentIndex, positionMs)
        player.prepare()
        if (shouldPlay) player.play() else player.pause()
        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlaybackService",
            "Recentered Media3 queue at globalIndex=$globalIndex size=${window.mediaItems.size}",
        )
    }

    private fun synchronizeMedia3QueueWithPlaylist(
        player: Player,
        playlist: Playlist,
        trackId: Long,
    ) {
        val window = buildAndroidMediaQueueWindow(
            playlist = playlist,
            currentTrackId = trackId,
        ) ?: return
        val alreadyMatches =
            player.mediaItemCount == window.mediaItems.size &&
                player.currentMediaItemIndex == window.currentIndex &&
                window.mediaItems.indices.all { index ->
                    player.getMediaItemAt(index).mediaId == window.mediaItems[index].mediaId
                }
        if (alreadyMatches) return

        val currentIndex = player.currentMediaItemIndex
        if (
            currentIndex != C.INDEX_UNSET &&
            player.currentMediaItem?.mediaId == window.mediaItems[window.currentIndex].mediaId &&
            player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        ) {
            val oldItemCount = player.mediaItemCount
            if (currentIndex + 1 < oldItemCount) {
                player.removeMediaItems(currentIndex + 1, oldItemCount)
            }
            if (currentIndex > 0) {
                player.removeMediaItems(0, currentIndex)
            }
            if (window.currentIndex > 0) {
                player.addMediaItems(
                    0,
                    window.mediaItems.subList(0, window.currentIndex),
                )
            }
            if (window.currentIndex < window.mediaItems.lastIndex) {
                player.addMediaItems(
                    window.mediaItems.subList(window.currentIndex + 1, window.mediaItems.size)
                )
            }
            AppLogger.info(
                DiagnosticLogCategory.Playback,
                "PlaybackService",
                "Synchronized Media3 queue without replacing current item size=${window.mediaItems.size}",
            )
            return
        }

        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        player.setMediaItems(window.mediaItems, window.currentIndex, positionMs)
        player.prepare()
        if (shouldPlay) player.play() else player.pause()
        AppLogger.info(
            DiagnosticLogCategory.Playback,
            "PlaybackService",
            "Synchronized Media3 queue after application queue change size=${window.mediaItems.size}",
        )
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
            val replayGain = withContext(Dispatchers.IO) {
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
        dspAudioProcessor?.updateSettings(settings.audioEffects, replayGainDb)
        AppLogger.debug(
            category = DiagnosticLogCategory.Dsp,
            target = "PlaybackService",
            message = "DSP configuration update published",
            fields = mapOf(
                "effectsEnabled" to settings.audioEffects.enabled.toString(),
                "headroomMode" to settings.audioEffects.headroom.mode.name,
                "truePeak" to settings.audioEffects.profile.limiter.truePeakEnabled.toString(),
            ),
        )
    }

    private fun playOnComplete() {
        val music = playerRepository.onCompleteMusic.value
        val playlist = playerRepository.playlist.value
        if (music != null && playlist != null) {
            play(music, playlist)
        }
    }

    private fun playNext() {
        playbackController.skipNext()
    }

    private fun playPrevious() {
        playbackController.skipPrevious()
    }

    private fun seekPlayerBy(player: Player, deltaMs: Long) {
        val maximum = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, maximum))
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaButtonPreferences(player: Player): ImmutableList<CommandButton> {
        val playbackState = playbackController.state.value
        val isFavorite = playbackState.currentItem?.libraryTrackId
            ?.let(favoriteTrackIds::contains) == true
        val playbackModeButton = when {
            playbackState.shuffleEnabled -> CommandButton.ICON_SHUFFLE_ON to
                R.string.notification_playback_mode_shuffle
            playbackState.repeatMode == RepeatMode.One -> CommandButton.ICON_REPEAT_ONE to
                R.string.notification_playback_mode_repeat_one
            playbackState.repeatMode == RepeatMode.All -> CommandButton.ICON_REPEAT_ALL to
                R.string.notification_playback_mode_repeat_all
            else -> CommandButton.ICON_REPEAT_OFF to
                R.string.notification_playback_mode_repeat_off
        }

        return ImmutableList.of(
            CommandButton.Builder(
                if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
            )
                .setSessionCommand(toggleFavoriteCommand)
                .setDisplayName(
                    getString(
                        if (isFavorite) {
                            R.string.notification_remove_favorite
                        } else {
                            R.string.notification_add_favorite
                        }
                    )
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(previousCommand)
                .setDisplayName(getString(R.string.notification_previous))
                .build(),
            CommandButton.Builder(
                if (player.isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
            )
                .setPlayerCommand(COMMAND_PLAY_PAUSE)
                .setDisplayName(
                    getString(
                        if (player.isPlaying) {
                            R.string.notification_pause
                        } else {
                            R.string.notification_play
                        }
                    )
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(nextCommand)
                .setDisplayName(getString(R.string.notification_next))
                .build(),
            CommandButton.Builder(playbackModeButton.first)
                .apply {
                    // Media3's repeat-off drawable is translucent and looks disabled on MIUI.
                    if (playbackModeButton.first == CommandButton.ICON_REPEAT_OFF) {
                        setCustomIconResId(R.drawable.icon_mode_list)
                    }
                }
                .setSessionCommand(cyclePlaybackModeCommand)
                .setDisplayName(getString(playbackModeButton.second))
                .build(),
        )
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaButtonPreferences() {
        val session = _mediaSession ?: return
        session.setMediaButtonPreferences(buildMediaButtonPreferences(session.player))
    }

    private fun toggleCurrentFavorite() {
        val trackId = playbackController.state.value.currentItem?.libraryTrackId ?: return
        serviceScope.launch {
            favoritesRepository.toggleFavorite(trackId)
        }
    }

    private fun cyclePlaybackMode() {
        val playbackState = playbackController.state.value
        val (repeatMode, shuffleEnabled) = when {
            playbackState.shuffleEnabled -> RepeatMode.One to false
            playbackState.repeatMode == RepeatMode.One -> RepeatMode.All to false
            playbackState.repeatMode == RepeatMode.All -> RepeatMode.All to true
            else -> RepeatMode.All to false
        }
        playbackController.setShuffle(shuffleEnabled)
        playbackController.setRepeatMode(repeatMode)
    }
}

private class PlaybackAudioFocusController(
    context: Context,
    private val player: Player,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mode = AudioFocusMode.Pause
    private var resumeOnGain = false
    private var ducked = false
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(::onAudioFocusChange)
        .build()

    fun updateMode(value: AudioFocusMode) {
        mode = value
        if (mode == AudioFocusMode.Mix) {
            restoreVolume()
            resumeOnGain = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun requestFocus() {
        if (mode == AudioFocusMode.Mix) return
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.pause()
    }

    fun release() {
        restoreVolume()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreVolume()
                if (resumeOnGain) {
                    resumeOnGain = false
                    player.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                restoreVolume()
                resumeOnGain = false
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForTransientLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                when (mode) {
                    AudioFocusMode.Pause -> pauseForTransientLoss()
                    AudioFocusMode.Duck -> {
                        player.volume = DUCK_VOLUME
                        ducked = true
                    }
                    AudioFocusMode.Mix -> Unit
                }
            }
        }
    }

    private fun pauseForTransientLoss() {
        resumeOnGain = player.isPlaying
        player.pause()
    }

    private fun restoreVolume() {
        if (ducked) player.volume = 1f
        ducked = false
    }
}

private const val DUCK_VOLUME = 0.2f
private const val MEDIA_SEEK_INTERVAL_MS = 10_000L
private const val MEDIA_QUEUE_RECENTER_THRESHOLD = 10

private fun Int.isHandledPlaybackMediaKey(): Boolean {
    return when (this) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> true
        else -> false
    }
}

private fun mediaAudioAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
}
