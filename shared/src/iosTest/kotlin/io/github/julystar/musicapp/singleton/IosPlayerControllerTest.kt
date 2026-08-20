package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.data.ToastRepositoryImpl
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.service.playback.data.PlayerRepository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.PlaylistEntity
import io.github.julystar.musicapp.database.PlaylistTrackCrossRef
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import uniffi.app_backend.MusicId
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class IosPlayerControllerTest {
    @Test
    fun restoredPlaybackSeeksBeforeStarting() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(
            MusicId(TRACK_ID),
            PlaylistId(PLAYLIST_ID),
            startPositionMs = 45_000L,
        )

        awaitUntil { harness.playerRepository.playing.value }

        assertEquals(listOf(45_000L), harness.engine.seekCalls)
        assertEquals(1, harness.engine.playCalls)
    }

    @Test
    fun seekPublishesTargetUntilAvPlayerCompletes() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.controller.seek(5_000UL)

        assertEquals(5_000L, harness.controller.getCurrentPosition())
        assertEquals(5_000L, harness.controller.getPendingSeekPosition())

        harness.engine.positionMs = 5_120L
        harness.engine.completeSeek()

        assertEquals(null, harness.controller.getPendingSeekPosition())
        assertEquals(5_120L, harness.controller.getCurrentPosition())
    }

    @Test
    fun supersededSeekCompletionDoesNotClearLatestTarget() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.controller.seek(5_000UL)
        harness.controller.seek(30_000UL)
        harness.engine.completeSeek(index = 0, finished = false)

        assertEquals(30_000L, harness.controller.getPendingSeekPosition())

        harness.engine.positionMs = 30_080L
        harness.engine.completeSeek(index = 0)

        assertEquals(null, harness.controller.getPendingSeekPosition())
        assertEquals(30_080L, harness.controller.getCurrentPosition())
    }

    @Test
    fun readyEngineStartsPlaybackAndReleasesResourceOnStop() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.playerRepository.playing.value &&
                harness.playerRepository.music.value?.meta?.id?.value == TRACK_ID
        }

        assertEquals(listOf(TEST_RESOURCE.uri), harness.engine.loadedRequests.map { it.resource.uri })
        assertEquals(listOf(TRACK_TITLE), harness.engine.loadedRequests.map { it.item.title })
        assertEquals(1, harness.engine.playCalls)
        assertEquals(TRACK_TITLE, harness.playerRepository.music.value?.meta?.title)
        assertEquals(PLAYLIST_ID, harness.playerRepository.playlist.value?.abstr?.meta?.id?.value)
        assertEquals(listOf(TEST_RESOURCE.uri), harness.source.resolvedUris)
        assertEquals(123_000L, harness.controller.getDuration())

        harness.controller.seek(5_000UL)
        harness.controller.pause()
        harness.controller.resume()
        harness.controller.stop()

        awaitUntil { TEST_RESOURCE.uri in harness.playbackResolver.releasedUris }
        assertEquals(listOf(5_000L), harness.engine.seekCalls)
        assertEquals(1, harness.engine.pauseCalls)
        assertEquals(2, harness.engine.playCalls)
        assertFalse(harness.playerRepository.playing.value)
        assertNull(harness.playerRepository.music.value)
    }

    @Test
    fun unsupportedEngineReleasesResolvedResourceAndKeepsPlayerIdle() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Unsupported()),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.engine.loadedRequests.isNotEmpty() &&
                TEST_RESOURCE.uri in harness.playbackResolver.releasedUris
        }

        assertEquals(listOf(TEST_RESOURCE.uri), harness.engine.loadedRequests.map { it.resource.uri })
        assertFalse(harness.playerRepository.loading.value)
        assertFalse(harness.playerRepository.playing.value)
        assertNull(harness.playerRepository.music.value)
    }

    @Test
    fun resolveFailureDoesNotLoadEngineAndClearsLoadingState() = withHarness(
        sourceResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))

        awaitUntil {
            harness.source.resolveCalls == 1 &&
                !harness.playerRepository.loading.value
        }

        assertEquals(emptyList(), harness.engine.loadedRequests)
        assertEquals(emptyList(), harness.playbackResolver.releasedUris)
        assertFalse(harness.playerRepository.playing.value)
        assertNull(harness.playerRepository.music.value)
    }

    @Test
    fun naturalPlaybackCompletionClearsPlayingStateWhenQueueHasNoNextTrack() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingIosPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.engine.completePlayback()

        awaitUntil { !harness.playerRepository.playing.value }
        assertEquals(TRACK_ID, harness.playerRepository.music.value?.meta?.id?.value)
    }

    private fun withHarness(
        sourceResult: SourcePlaybackResult,
        engine: RecordingIosPlaybackEngine,
        block: suspend (IosPlaybackHarness) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tempDir = NSTemporaryDirectory()
        val preferencesPath = "${tempDir}musicapp-ios-player-${NSUUID.UUID().UUIDString}.preferences_pb".toPath()
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedLibrary(database)
            val roomLibraryStore = RoomLibraryStore(
                database = database,
                trackDao = database.trackDao(),
                sourceItemDao = database.sourceItemDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                playlistDao = database.playlistDao(),
                metadataDao = database.metadataDao(),
            )
            val toastRepository = ToastRepositoryImpl(scope)
            val storageRepository = StorageRepositoryImpl(
                bridge = Bridge(
                    appDocumentDir = tempDir,
                    appCacheDir = tempDir,
                    toastRepository = toastRepository,
                ),
                scope = scope,
                sourceAccountDao = database.sourceAccountDao(),
                credentialStore = InMemoryCredentialStore(),
            )
            val playerRepository = PlayerRepository(
                roomLibraryStore = roomLibraryStore,
                appPreferencesRepository = AppPreferencesRepository(
                    createAppDataStore { preferencesPath }
                ),
                _scope = scope,
                storageLookup = LegacyStorageLookup {
                    storage(id = STORAGE_ID, type = StorageType.WEBDAV)
                },
            )
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
            val controller = IosPlayerController(
                playerRepository = playerRepository,
                toastRepository = toastRepository,
                playlistRepository = PlaylistRepositoryImpl(
                    storageRepository = storageRepository,
                    _scope = scope,
                    playlistDao = database.playlistDao(),
                    roomLibraryStore = roomLibraryStore,
                    storageLookup = LegacyStorageLookup {
                        storage(id = STORAGE_ID, type = StorageType.WEBDAV)
                    },
                ),
                storageRepository = storageRepository,
                roomLibraryStore = roomLibraryStore,
                playbackResourceResolver = playbackResourceResolver,
                scope = scope,
                playbackEngine = engine,
                mainDispatcher = Dispatchers.Default,
            )

            block(
                IosPlaybackHarness(
                    controller = controller,
                    playerRepository = playerRepository,
                    source = source,
                    engine = engine,
                    playbackResolver = playbackResolver,
                )
            )
        } finally {
            scope.cancel()
            database.close()
            FileSystem.SYSTEM.delete(preferencesPath, mustExist = false)
        }
    }

    private suspend fun seedLibrary(database: AppDatabase) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = STORAGE_ID,
                providerType = ProviderTypes.WebDav,
                displayName = "WebDAV",
                endpoint = "https://example.invalid/dav",
                externalAccountId = null,
                credentialRef = "storage-$STORAGE_ID",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    id = TRACK_ID,
                    title = TRACK_TITLE,
                    sortTitle = null,
                    albumId = null,
                    albumArtist = null,
                    composer = null,
                    comment = null,
                    grouping = null,
                    durationMs = 123_000,
                    discNumber = null,
                    discTotal = null,
                    trackNumber = null,
                    trackTotal = null,
                    year = null,
                    date = null,
                    sampleRate = null,
                    bitRate = null,
                    bitsPerSample = null,
                    channels = null,
                    channelLayout = null,
                    codec = null,
                    container = null,
                    lossless = null,
                    createdAt = 1,
                    updatedAt = 1,
                    artist = "Luna",
                )
            )
        )
        database.sourceItemDao().upsertAll(
            listOf(
                SourceItemEntity(
                    id = TRACK_ID,
                    sourceAccountId = STORAGE_ID,
                    libraryRootId = null,
                    itemType = SourceItemTypes.Track,
                    providerItemId = "item-$TRACK_ID",
                    parentProviderItemId = null,
                    canonicalPath = TRACK_PATH,
                    displayPath = TRACK_PATH,
                    displayName = TRACK_PATH.substringAfterLast('/'),
                    mimeType = "audio/flac",
                    sizeBytes = 1_000,
                    etag = "\"etag-$TRACK_ID\"",
                    revision = null,
                    createdAtRemote = 1,
                    modifiedAtRemote = 1,
                    contentHash = null,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = 1,
                    lastSyncedAt = 1,
                    lastSeenScanId = "scan-1",
                )
            )
        )
        database.trackSourceRefDao().upsertAll(
            listOf(
                TrackSourceRefEntity(
                    trackId = TRACK_ID,
                    sourceItemId = TRACK_ID,
                    role = "primary",
                    matchMethod = "test",
                    matchConfidence = 100,
                    isPreferred = true,
                    isAvailable = true,
                    isDownloaded = false,
                    playable = true,
                    downloadable = true,
                    codec = null,
                    container = null,
                    bitRate = null,
                    sampleRate = null,
                    bitsPerSample = null,
                    channels = null,
                    lossless = null,
                    createdAt = 1,
                    updatedAt = 1,
                )
            )
        )
        database.playlistDao().upsert(
            PlaylistEntity(
                id = PLAYLIST_ID,
                title = "Queue",
                artworkId = null,
                createdAt = 1,
                updatedAt = 1,
                sortOrder = 0,
            )
        )
        database.playlistDao().upsertTracks(
            listOf(
                PlaylistTrackCrossRef(
                    playlistId = PLAYLIST_ID,
                    trackId = TRACK_ID,
                    sortOrder = 0,
                    addedAt = 1,
                )
            )
        )
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                delay(10)
            }
        }
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

private data class IosPlaybackHarness(
    val controller: IosPlayerController,
    val playerRepository: PlayerRepository,
    val source: RecordingMusicSource,
    val engine: RecordingIosPlaybackEngine,
    val playbackResolver: RecordingLegacyPlaybackResolver,
)

private class RecordingIosPlaybackEngine(
    private val loadResult: PlaybackEngineLoadResult,
) : IosPlaybackEngine {
    private val playbackCompletedChannel = Channel<Unit>(Channel.BUFFERED)
    override val playbackCompleted = playbackCompletedChannel.receiveAsFlow()
    val loadedRequests = mutableListOf<PlaybackEngineLoadRequest>()
    val seekCalls = mutableListOf<Long>()
    var positionMs = 1_000L
    private val seekCompletionHandlers = mutableListOf<(Boolean) -> Unit>()
    var playCalls = 0
        private set
    var pauseCalls = 0
        private set
    var stopCalls = 0
        private set

    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        loadedRequests += request
        return loadResult
    }

    override fun play() {
        playCalls += 1
    }

    override fun pause() {
        pauseCalls += 1
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
    }

    override fun seekTo(positionMs: Long, completionHandler: (Boolean) -> Unit) {
        seekCalls += positionMs
        seekCompletionHandlers += completionHandler
    }

    override fun readPosition(): PlaybackPosition {
        return PlaybackPosition(
            positionMs = positionMs,
            bufferedMs = 2_000L,
            durationMs = 123_000L,
        )
    }

    override fun release() = Unit

    fun completePlayback() {
        playbackCompletedChannel.trySend(Unit)
    }

    fun completeSeek(index: Int = 0, finished: Boolean = true) {
        seekCompletionHandlers.removeAt(index).invoke(finished)
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

private class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()

    override suspend fun load(storageId: Long): StoredCredential? {
        return values[storageId]
    }

    override suspend fun save(storageId: Long, credential: StoredCredential) {
        values[storageId] = credential
    }

    override suspend fun delete(storageId: Long) {
        values.remove(storageId)
    }

    override suspend fun clear() {
        values.clear()
    }
}

private const val STORAGE_ID = 2L
private const val TRACK_ID = 7L
private const val TRACK_TITLE = "Moon"
private const val TRACK_PATH = "/Music/Moon.flac"
private const val PLAYLIST_ID = 3L

private val TEST_RESOURCE = PlaybackResource(
    uri = "http://127.0.0.1/track.flac",
    mimeType = "audio/flac",
)
