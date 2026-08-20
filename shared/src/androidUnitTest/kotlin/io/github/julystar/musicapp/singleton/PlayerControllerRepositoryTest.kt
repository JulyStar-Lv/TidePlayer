package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.ToastRepositoryImpl

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import uniffi.app_backend.ArgRemoveMusicFromPlaylist
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistAbstract
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.PlaylistMeta
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class PlayerControllerRepositoryTest {
    @Test
    fun readyEngineStartsPlaybackAndReleasesResourceOnStop() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.playerState.playing.value &&
                harness.playerState.music.value?.meta?.id?.value == TRACK_ID
        }

        assertEquals(listOf(TEST_RESOURCE.uri), harness.engine.loadedRequests.map { it.resource.uri })
        assertEquals(listOf(TRACK_TITLE), harness.engine.loadedRequests.map { it.item.title })
        assertEquals(0, harness.engine.playCalls)
        assertEquals(TRACK_TITLE, harness.playerState.music.value?.meta?.title)
        assertEquals(PLAYLIST_ID, harness.playerState.playlist.value?.abstr?.meta?.id?.value)
        assertEquals(listOf(TEST_RESOURCE.uri), harness.source.resolvedUris)
        assertEquals(123_000L, harness.controller.getDuration())

        harness.controller.seek(5_000UL)
        harness.controller.pause()
        harness.controller.resume()
        harness.controller.stop()

        awaitUntil { TEST_RESOURCE.uri in harness.playbackResolver.releasedUris }
        assertEquals(listOf(5_000L), harness.engine.seekCalls)
        assertEquals(1, harness.engine.pauseCalls)
        assertEquals(1, harness.engine.playCalls)
        assertFalse(harness.playerState.playing.value)
        assertNull(harness.playerState.music.value)
    }

    @Test
    fun unsupportedEngineReleasesResolvedResourceAndKeepsPlayerIdle() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Unsupported()),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.engine.loadedRequests.isNotEmpty() &&
                TEST_RESOURCE.uri in harness.playbackResolver.releasedUris
        }

        assertEquals(listOf(TEST_RESOURCE.uri), harness.engine.loadedRequests.map { it.resource.uri })
        assertFalse(harness.playerState.loading.value)
        assertFalse(harness.playerState.playing.value)
        assertNull(harness.playerState.music.value)
    }

    @Test
    fun resolveFailureDoesNotLoadEngineAndClearsLoadingState() = withHarness(
        sourceResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.source.resolveCalls == 1 &&
                !harness.playerState.loading.value
        }

        assertEquals(emptyList(), harness.engine.loadedRequests)
        assertEquals(emptyList(), harness.playbackResolver.releasedUris)
        assertFalse(harness.playerState.playing.value)
        assertNull(harness.playerState.music.value)
    }

    @Test
    fun preparedPlaybackQueueSurvivesTrackLoading() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        val preparedQueue = playlist(
            id = PLAYLIST_ID,
            musics = listOf(musicAbstract(id = TRACK_ID, title = TRACK_TITLE)),
        ).let { playlist ->
            playlist.copy(
                abstr = playlist.abstr.copy(
                    meta = playlist.abstr.meta.copy(title = "Daily Picks"),
                ),
            )
        }
        harness.playerState.playlist.value = preparedQueue

        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil { harness.playerState.playing.value }
        assertEquals("Daily Picks", harness.playerState.playlist.value?.abstr?.meta?.title)
    }

    @Test
    fun restoredPreviewLoadsPlayerWithoutReplacingVisibleTrack() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        val previewMusic = music(id = TRACK_ID, title = TRACK_TITLE)
        val previewPlaylist = playlist(
            id = PLAYLIST_ID,
            musics = listOf(musicAbstract(id = TRACK_ID, title = TRACK_TITLE)),
        )
        val resolveGate = CompletableDeferred<Unit>()
        harness.playerState.setCurrent(previewMusic, previewPlaylist)
        harness.source.resolveGate = resolveGate

        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerState.loading.value && harness.source.resolveCalls == 1 }

        assertSame(previewMusic, harness.playerState.music.value)

        resolveGate.complete(Unit)
        awaitUntil { harness.playerState.playing.value }

        assertSame(previewMusic, harness.playerState.music.value)
        assertEquals(1, harness.engine.queueLoadCalls)
        assertEquals(1, harness.engine.loadedRequests.size)
    }

    @Test
    fun restoredPlaybackPassesSavedPositionIntoQueueLoad() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(
            loadResult = PlaybackEngineLoadResult.Ready,
            queueLoadResult = PlaybackEngineLoadResult.Ready,
        ),
    ) { harness ->
        harness.controller.play(
            MusicId(TRACK_ID),
            PlaylistId(PLAYLIST_ID),
            startPositionMs = 45_000L,
        )

        awaitUntil { harness.playerState.playing.value }

        assertEquals(45_000L, harness.engine.queueLoadRequests.single().startPositionMs)
        assertEquals(emptyList(), harness.engine.seekCalls)
    }

    @Test
    fun currentTrackRemainsVisibleWhileNextTrackIsLoading() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        val queue = playlist(
            id = PLAYLIST_ID,
            musics = listOf(musicAbstract(id = TRACK_ID, title = TRACK_TITLE)),
        )
        harness.playerState.setCurrent(
            music = music(id = PREVIOUS_TRACK_ID, title = "Previous"),
            playlist = queue,
        )
        val resolveGate = CompletableDeferred<Unit>()
        harness.source.resolveGate = resolveGate

        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerState.loading.value && harness.source.resolveCalls == 1 }

        assertEquals(PREVIOUS_TRACK_ID, harness.playerState.music.value?.meta?.id?.value)

        resolveGate.complete(Unit)
        awaitUntil { harness.playerState.music.value?.meta?.id?.value == TRACK_ID }
    }

    @Test
    fun nativeQueueTransitionDoesNotStopTheActivePlayer() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingAndroidPlaybackEngine(
            loadResult = PlaybackEngineLoadResult.Ready,
            queueLoadResult = PlaybackEngineLoadResult.Ready,
        ),
    ) { harness ->
        harness.playerState.setCurrent(
            music = music(id = PREVIOUS_TRACK_ID, title = "Previous"),
            playlist = playlist(
                id = PLAYLIST_ID,
                musics = listOf(musicAbstract(id = TRACK_ID, title = TRACK_TITLE)),
            ),
        )

        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil { harness.playerState.music.value?.meta?.id?.value == TRACK_ID }

        assertEquals(0, harness.engine.stopCalls)
        assertEquals(1, harness.engine.queueLoadCalls)
    }

    private fun withHarness(
        sourceResult: SourcePlaybackResult,
        engine: RecordingAndroidPlaybackEngine,
        block: suspend (AndroidPlaybackHarness) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: ".")
        try {
            val music = music(id = TRACK_ID, title = TRACK_TITLE)
            val playlist = playlist(
                id = PLAYLIST_ID,
                musics = listOf(musicAbstract(id = TRACK_ID, title = TRACK_TITLE)),
            )
            val playerState = FakeAndroidPlayerStateStore()
            val source = RecordingMusicSource(sourceResult)
            val playbackResolver = RecordingLegacyPlaybackResolver()
            val playbackResourceResolver = PlaybackResourceResolver(
                storageLookup = LegacyStorageLookup {
                    storage(id = STORAGE_ID, type = StorageType.WEBDAV)
                },
                trackSourceRefDao = EmptyTrackSourceRefDao,
                sourceRegistry = MusicSourceRegistry(listOf(source)),
                legacyStoragePlaybackResolver = playbackResolver,
                sourceItemPropertyReader = io.github.julystar.musicapp.service.playback.data.SourceItemPropertyReader.Empty,
            )
            val toastRepository = ToastRepositoryImpl(scope)
            val controller = PlayerControllerRepository(
                playerState = playerState,
                toastRepository = toastRepository,
                removalEvents = FakeAndroidPlaybackRemovalEvents(),
                bridge = Bridge(
                    appDocumentDir = tempDir.absolutePath,
                    appCacheDir = tempDir.absolutePath,
                    toastRepository = toastRepository,
                ),
                playbackLibrary = FakeAndroidPlaybackLibrary(
                    music = music,
                    playlist = playlist,
                ),
                playbackResourceResolver = playbackResourceResolver,
                _scope = scope,
                initialPlaybackEngine = engine,
                mainDispatcher = Dispatchers.Default,
            )

            block(
                AndroidPlaybackHarness(
                    controller = controller,
                    playerState = playerState,
                    source = source,
                    engine = engine,
                    playbackResolver = playbackResolver,
                )
            )
        } finally {
            scope.cancel()
        }
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private fun music(
        id: Long,
        title: String,
    ): Music {
        return Music(
            meta = musicMeta(id = id, title = title),
            loc = StorageEntryLoc(
                storageId = StorageId(STORAGE_ID),
                path = TRACK_PATH,
            ),
            cover = null,
            lyric = null,
        )
    }

    private fun musicAbstract(
        id: Long,
        title: String,
    ): MusicAbstract {
        return MusicAbstract(
            meta = musicMeta(id = id, title = title),
            cover = null,
        )
    }

    private fun musicMeta(
        id: Long,
        title: String,
    ): MusicMeta {
        return MusicMeta(
            id = MusicId(id),
            title = title,
            duration = 123_000.milliseconds,
            order = listOf(id.toUInt()),
        )
    }

    private fun playlist(
        id: Long,
        musics: List<MusicAbstract>,
    ): Playlist {
        return Playlist(
            abstr = PlaylistAbstract(
                meta = PlaylistMeta(
                    id = PlaylistId(id),
                    title = "Queue",
                    cover = null,
                    showCover = null,
                    createdTime = 0.milliseconds,
                    order = listOf(id.toUInt()),
                ),
                musicCount = musics.size.toULong(),
                duration = null,
            ),
            musics = musics,
        )
    }

    private fun storage(
        id: Long,
        type: StorageType,
    ) = Storage(
        id = StorageId(id),
        addr = "",
        alias = "Storage $id",
        username = "",
        password = "",
        isAnonymous = true,
        typ = type,
        musicCount = 1u,
    )
}

private data class AndroidPlaybackHarness(
    val controller: PlayerControllerRepository,
    val playerState: FakeAndroidPlayerStateStore,
    val source: RecordingMusicSource,
    val engine: RecordingAndroidPlaybackEngine,
    val playbackResolver: RecordingLegacyPlaybackResolver,
)

private class FakeAndroidPlayerStateStore : AndroidPlayerStateStore {
    override val playlist = MutableStateFlow<Playlist?>(null)
    override val music = MutableStateFlow<Music?>(null)
    override val nextMusic = MutableStateFlow<MusicAbstract?>(null)
    override val previousMusic = MutableStateFlow<MusicAbstract?>(null)
    override val pauseRequest: Flow<Unit> = MutableSharedFlow()
    val loading = MutableStateFlow(false)
    val playing = MutableStateFlow(false)
    var durationChangedCount = 0

    override fun setIsLoading(loading: Boolean) {
        this.loading.value = loading
    }

    override fun setIsPlaying(playing: Boolean) {
        this.playing.value = playing
    }

    override fun setCurrent(music: Music, playlist: Playlist) {
        this.music.value = music
        this.playlist.value = playlist
    }

    override fun resetCurrent() {
        music.value = null
        playlist.value = null
    }

    override fun notifyDurationChanged() {
        durationChangedCount += 1
    }

    override fun refreshPlaylistIfMatch(playlist: Playlist) {
        if (this.playlist.value?.abstr?.meta?.id == playlist.abstr.meta.id) {
            this.playlist.value = playlist
        }
    }

    override fun emitPauseRequest() = Unit

    override fun reload() = Unit
}

private class FakeAndroidPlaybackRemovalEvents : AndroidPlaybackRemovalEvents {
    override val preRemovePlaylistEvent = MutableSharedFlow<PlaylistId>()
    override val preRemoveMusicEvent = MutableSharedFlow<ArgRemoveMusicFromPlaylist>()
    override val preRemoveStorageEvent = MutableSharedFlow<StorageId>()
    val removedMusic = mutableListOf<Pair<Long, Long>>()

    override suspend fun removeMusic(playlistId: Long, musicId: Long) {
        removedMusic += playlistId to musicId
    }
}

private class FakeAndroidPlaybackLibrary(
    private val music: Music,
    private val playlist: Playlist,
) : AndroidPlaybackLibrary {
    override suspend fun getMusic(id: MusicId): Music? {
        return music.takeIf { it.meta.id == id }
    }

    override suspend fun getPlaylist(id: PlaylistId): Playlist? {
        return playlist.takeIf { it.abstr.meta.id == id }
    }
}

private class RecordingAndroidPlaybackEngine(
    private val loadResult: PlaybackEngineLoadResult,
    private val queueLoadResult: PlaybackEngineLoadResult = PlaybackEngineLoadResult.Unsupported(),
) : AndroidPlaybackEngine {
    val loadedRequests = mutableListOf<PlaybackEngineLoadRequest>()
    val seekCalls = mutableListOf<Long>()
    var playCalls = 0
        private set
    var pauseCalls = 0
        private set
    var stopCalls = 0
        private set
    var releaseCalls = 0
        private set
    var queueLoadCalls = 0
        private set
    val queueLoadRequests = mutableListOf<AndroidPlaybackQueueLoadRequest>()
    private var loadedTrackId: Long? = null

    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        loadedRequests += request
        if (loadResult == PlaybackEngineLoadResult.Ready) {
            loadedTrackId = request.item.libraryTrackId
        }
        return loadResult
    }

    override fun loadQueue(request: AndroidPlaybackQueueLoadRequest): PlaybackEngineLoadResult {
        queueLoadCalls += 1
        queueLoadRequests += request
        if (queueLoadResult == PlaybackEngineLoadResult.Ready) {
            loadedTrackId = request.currentTrackId
        }
        return queueLoadResult
    }

    override fun hasLoadedTrack(trackId: Long): Boolean = loadedTrackId == trackId

    override fun play() {
        playCalls += 1
    }

    override fun pause() {
        pauseCalls += 1
    }

    override fun stop() {
        stopCalls += 1
        loadedTrackId = null
    }

    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
    }

    override fun readPosition(): PlaybackPosition {
        return PlaybackPosition(
            positionMs = 1_000L,
            bufferedMs = 2_000L,
            durationMs = 123_000L,
        )
    }

    override fun release() {
        releaseCalls += 1
    }
}

private class RecordingMusicSource(
    private val result: SourcePlaybackResult,
) : MusicSource {
    override val descriptor = MusicSourceDescriptor(
        id = BuiltInSourceIds.WebDav,
        displayName = "WebDAV",
    )
    override val capabilities = setOf(SourceCapability.Stream)
    val resolvedUris = mutableListOf<String>()
    var resolveCalls = 0
        private set
    var resolveGate: CompletableDeferred<Unit>? = null

    override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
        return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
    }

    override suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult {
        return SourceListResult.Failure(SourceListFailureReason.UnsupportedAccount)
    }

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        resolveCalls += 1
        resolveGate?.await()
        val uri = (result as? SourcePlaybackResult.Success)?.resource?.uri
        if (uri != null) {
            resolvedUris += uri
        }
        return result
    }
}

private class RecordingLegacyPlaybackResolver : LegacyStoragePlaybackResolver {
    val releasedUris = mutableListOf<String>()

    override suspend fun resolve(
        accountId: SourceAccountId,
        path: String,
        expectedStorageKind: LegacyStorageKind,
    ): SourcePlaybackResult {
        return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    override suspend fun release(uri: String) {
        releasedUris += uri
    }

    override suspend fun releaseAll() = Unit
}

private object EmptyTrackSourceRefDao : TrackSourceRefDao {
    override suspend fun findByTrackId(trackId: Long): List<TrackSourceRefEntity> {
        return emptyList()
    }

    override suspend fun contains(trackId: Long, sourceItemId: Long) = false

    override suspend fun updatePreferredSource(trackId: Long, sourceItemId: Long, now: Long) = Unit

    override suspend fun findBySourceItemIds(sourceItemIds: List<Long>): List<TrackSourceRefEntity> {
        return emptyList()
    }

    override suspend fun countForTrack(trackId: Long): Int {
        return 0
    }

    override suspend fun webDavMetadataCandidatesForTrack(trackId: Long) =
        emptyList<io.github.julystar.musicapp.database.MetadataRefreshCandidate>()

    override suspend fun metadataResetCandidateForTrack(
        trackId: Long,
    ): io.github.julystar.musicapp.database.MetadataRefreshCandidate? = null

    override suspend fun webDavMetadataCandidatesForAlbum(albumId: Long) =
        emptyList<io.github.julystar.musicapp.database.MetadataRefreshCandidate>()

    override suspend fun missingWebDavMetadataCandidates(target: String) =
        emptyList<io.github.julystar.musicapp.database.MetadataRefreshCandidate>()

    override suspend fun hasSourceAccount(trackId: Long, sourceAccountId: Long) = false

    override suspend fun upsertAll(refs: List<TrackSourceRefEntity>) = Unit

    override suspend fun updateEmbeddedMetadataPresence(
        sourceItemId: Long,
        hasEmbeddedArtwork: Boolean,
        embeddedLyricsKind: String,
        now: Long,
    ) = Unit

    override suspend fun markAvailableBySourceItemIds(sourceItemIds: List<Long>, now: Long) = Unit

    override suspend fun markUnavailableBySourceItemIds(sourceItemIds: List<Long>, now: Long) = Unit

    override suspend fun markUnavailableForDeletedSourceItems(libraryRootId: Long, now: Long) = Unit

    override suspend fun playbackCandidates(trackId: Long): List<TrackSourcePlaybackCandidate> {
        return emptyList()
    }

    override suspend fun playbackCandidatesForTracks(trackIds: List<Long>): List<TrackSourcePlaybackCandidate> {
        return emptyList()
    }
}

private const val STORAGE_ID = 2L
private const val PREVIOUS_TRACK_ID = 6L
private const val TRACK_ID = 7L
private const val TRACK_TITLE = "Moon"
private const val TRACK_PATH = "/Music/Moon.flac"
private const val PLAYLIST_ID = 3L

private val TEST_RESOURCE = PlaybackResource(
    uri = "http://127.0.0.1/track.flac",
    mimeType = "audio/flac",
)
