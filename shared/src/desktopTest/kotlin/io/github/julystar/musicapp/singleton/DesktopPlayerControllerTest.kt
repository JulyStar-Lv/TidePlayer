package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.core.data.ToastRepositoryImpl
import io.github.julystar.musicapp.core.domain.repository.ToastRepository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.MetadataRefreshCandidate
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
import io.github.julystar.musicapp.core.data.datastore.PersistedPlaybackSession
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.service.playback.data.LegacyPlaybackController
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import uniffi.app_backend.MusicId
import uniffi.app_backend.PlayMode
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPlayerControllerTest {
    @Test
    fun selectingTrackAfterSessionRestoreKeepsRestoredQueueSubset() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.appPreferencesRepository.savePlaybackSession(
            PersistedPlaybackSession(
                trackId = SECOND_TRACK_ID,
                playlistId = PLAYLIST_ID,
                positionMs = 0L,
                wasPlaying = false,
                queueTrackIds = listOf(SECOND_TRACK_ID),
            )
        )
        val playbackController = LegacyPlaybackController(
            playerRepository = harness.playerRepository,
            legacyController = harness.controller,
            roomLibraryStore = harness.roomLibraryStore,
            scope = harness.scope,
        )
        awaitUntil {
            harness.playerRepository.playlist.value?.musics?.map { it.meta.id.value } ==
                listOf(SECOND_TRACK_ID)
        }

        playbackController.play(
            items = listOf(
                PlayableItem(
                    title = SECOND_TRACK_TITLE,
                    libraryTrackId = SECOND_TRACK_ID,
                    libraryPlaylistId = PLAYLIST_ID,
                )
            ),
            startIndex = 0,
        )

        awaitUntil { harness.playerRepository.playing.value }
        assertEquals(
            listOf(SECOND_TRACK_ID),
            harness.playerRepository.playlist.value?.musics?.map { it.meta.id.value },
        )
    }

    @Test
    fun restoredPlaybackSeeksBeforeStarting() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
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
    fun readyEngineStartsPlaybackAndReleasesResourceOnStop() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
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
        assertTrue(harness.engine.stopCalls >= 1)
        assertFalse(harness.playerRepository.playing.value)
        assertNull(harness.playerRepository.music.value)
    }

    @Test
    fun unsupportedEngineReleasesResolvedResourceAndKeepsPlayerIdle() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Unsupported()),
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
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
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
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.engine.playbackCompleted = true

        awaitUntil { !harness.playerRepository.playing.value }
        assertEquals(TRACK_ID, harness.playerRepository.music.value?.meta?.id?.value)
    }

    @Test
    fun currentTrackRemainsVisibleWhileNextTrackIsLoading() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.music.value?.meta?.id?.value == TRACK_ID }
        val resolveGate = CompletableDeferred<Unit>()
        harness.source.resolveGate = resolveGate

        harness.controller.play(MusicId(SECOND_TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.loading.value && harness.source.resolveCalls == 2 }

        assertEquals(TRACK_ID, harness.playerRepository.music.value?.meta?.id?.value)

        resolveGate.complete(Unit)
        awaitUntil { harness.playerRepository.music.value?.meta?.id?.value == SECOND_TRACK_ID }
    }

    @Test
    fun endPositionDoesNotAdvanceWithoutNativeCompletionWhenCrossfadeIsDisabled() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.playerRepository.setPlayMode(PlayMode.LIST_LOOP)
        awaitUntil { harness.playerRepository.playMode.value == PlayMode.LIST_LOOP }
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.engine.positionMs = harness.engine.durationMs
        delay(250)

        assertEquals(TRACK_ID, harness.playerRepository.music.value?.meta?.id?.value)
        assertEquals(1, harness.engine.loadedRequests.size)
    }

    @Test
    fun naturalCompletionInListLoopAdvancesAndWrapsTheQueue() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.playerRepository.setPlayMode(PlayMode.LIST_LOOP)
        awaitUntil { harness.playerRepository.playMode.value == PlayMode.LIST_LOOP }
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.engine.playbackCompleted = true
        awaitUntil {
            harness.playerRepository.music.value?.meta?.id?.value == SECOND_TRACK_ID
        }

        harness.engine.playbackCompleted = true
        awaitUntil {
            harness.playerRepository.music.value?.meta?.id?.value == TRACK_ID &&
                harness.engine.loadedRequests.size == 3
        }

        assertEquals(
            listOf(TRACK_TITLE, SECOND_TRACK_TITLE, TRACK_TITLE),
            harness.engine.loadedRequests.map { it.item.title },
        )
    }

    @Test
    fun naturalCompletionInSingleLoopReloadsTheCurrentTrack() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.playerRepository.setPlayMode(PlayMode.SINGLE_LOOP)
        awaitUntil { harness.playerRepository.playMode.value == PlayMode.SINGLE_LOOP }
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.playing.value }

        harness.engine.playbackCompleted = true
        awaitUntil { harness.engine.loadedRequests.size == 2 }

        assertEquals(TRACK_ID, harness.playerRepository.music.value?.meta?.id?.value)
        assertEquals(listOf(TRACK_TITLE, TRACK_TITLE), harness.engine.loadedRequests.map { it.item.title })
    }

    @Test
    fun refreshCurrentMetadataPublishesUpdatedTitleAndLyricsBeforeReturning() = withHarness(
        sourceResult = SourcePlaybackResult.Success(TEST_RESOURCE),
        engine = RecordingDesktopPlaybackEngine(PlaybackEngineLoadResult.Ready),
    ) { harness ->
        harness.controller.play(MusicId(TRACK_ID), PlaylistId(PLAYLIST_ID))
        awaitUntil { harness.playerRepository.currentTrackInfo.value?.id == TRACK_ID }

        val track = requireNotNull(harness.database.trackDao().get(TRACK_ID))
        harness.database.trackDao().upsertAll(
            listOf(track.copy(title = "Updated title", updatedAt = 2)),
        )
        harness.database.metadataDao().upsertLyrics(
            listOf(
                LyricsEntity(
                    trackId = TRACK_ID,
                    format = "LRC",
                    language = null,
                    synchronized = true,
                    content = "[00:01.00]<00:01.000>Updated <00:01.500>lyric<00:02.000>",
                    sourcePath = null,
                    updatedAt = 2,
                ),
            ),
        )

        harness.playerRepository.refreshCurrentMetadata()

        val refreshed = requireNotNull(harness.playerRepository.currentTrackInfo.value)
        assertEquals("Updated title", refreshed.title)
        assertEquals("Updated lyric", refreshed.lyrics.lines.single().text)
        assertEquals(2, refreshed.lyrics.lines.single().words.size)
        assertEquals(500, refreshed.lyrics.lines.single().words.first().duration.inWholeMilliseconds)
    }

    private fun withHarness(
        sourceResult: SourcePlaybackResult,
        engine: RecordingDesktopPlaybackEngine,
        block: suspend (DesktopPlaybackHarness) -> Unit,
    ) = runBlocking {
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.Default)
        val preferencesFile = File.createTempFile("musicapp-player-", ".preferences_pb").apply {
            delete()
        }
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
                    appDocumentDir = preferencesFile.parentFile.absolutePath,
                    appCacheDir = preferencesFile.parentFile.absolutePath,
                    toastRepository = toastRepository,
                ),
                scope = scope,
                sourceAccountDao = database.sourceAccountDao(),
                credentialStore = InMemoryCredentialStore(),
            )
            val appPreferencesRepository = AppPreferencesRepository(
                createAppDataStore { preferencesFile.absolutePath.toPath() }
            )
            val playerRepository = PlayerRepository(
                roomLibraryStore = roomLibraryStore,
                appPreferencesRepository = appPreferencesRepository,
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
            )
            val controller = DesktopPlayerController(
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
                playbackEngine = engine,
                scope = scope,
            )

            block(
                DesktopPlaybackHarness(
                    controller = controller,
                    playerRepository = playerRepository,
                    database = database,
                    roomLibraryStore = roomLibraryStore,
                    appPreferencesRepository = appPreferencesRepository,
                    scope = scope,
                    source = source,
                    engine = engine,
                    playbackResolver = playbackResolver,
                )
            )
        } finally {
            scopeJob.cancelAndJoin()
            database.close()
            preferencesFile.delete()
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
        val firstTrack = TrackEntity(
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
        database.trackDao().upsertAll(
            listOf(
                firstTrack,
                firstTrack.copy(
                    id = SECOND_TRACK_ID,
                    title = SECOND_TRACK_TITLE,
                    artist = "Prism",
                ),
            )
        )
        val firstSourceItem = SourceItemEntity(
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
        database.sourceItemDao().upsertAll(
            listOf(
                firstSourceItem,
                firstSourceItem.copy(
                    id = SECOND_TRACK_ID,
                    providerItemId = "item-$SECOND_TRACK_ID",
                    canonicalPath = SECOND_TRACK_PATH,
                    displayPath = SECOND_TRACK_PATH,
                    displayName = SECOND_TRACK_PATH.substringAfterLast('/'),
                    etag = "\"etag-$SECOND_TRACK_ID\"",
                ),
            )
        )
        val firstSourceRef = TrackSourceRefEntity(
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
        database.trackSourceRefDao().upsertAll(
            listOf(
                firstSourceRef,
                firstSourceRef.copy(
                    trackId = SECOND_TRACK_ID,
                    sourceItemId = SECOND_TRACK_ID,
                ),
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
                ),
                PlaylistTrackCrossRef(
                    playlistId = PLAYLIST_ID,
                    trackId = SECOND_TRACK_ID,
                    sortOrder = 1,
                    addedAt = 1,
                ),
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

private data class DesktopPlaybackHarness(
    val controller: DesktopPlayerController,
    val playerRepository: PlayerRepository,
    val database: AppDatabase,
    val roomLibraryStore: RoomLibraryStore,
    val appPreferencesRepository: AppPreferencesRepository,
    val scope: CoroutineScope,
    val source: RecordingMusicSource,
    val engine: RecordingDesktopPlaybackEngine,
    val playbackResolver: RecordingLegacyPlaybackResolver,
)

private class RecordingDesktopPlaybackEngine(
    private val loadResult: PlaybackEngineLoadResult,
) : DesktopPlaybackEngine {
    val loadedRequests = mutableListOf<PlaybackEngineLoadRequest>()
    val seekCalls = mutableListOf<Long>()
    var playCalls = 0
        private set
    var pauseCalls = 0
        private set
    var stopCalls = 0
        private set
    var playbackCompleted = false
    var positionMs = 1_000L
    val durationMs = 123_000L

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

    override fun readPosition(): PlaybackPosition {
        return PlaybackPosition(
            positionMs = positionMs,
            bufferedMs = 2_000L,
            durationMs = durationMs,
        )
    }

    override fun takePlaybackCompleted(): Boolean = playbackCompleted.also {
        playbackCompleted = false
    }

    override fun release() = Unit
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

    override suspend fun webDavMetadataCandidatesForTrack(trackId: Long) = emptyList<MetadataRefreshCandidate>()

    override suspend fun metadataResetCandidateForTrack(trackId: Long): MetadataRefreshCandidate? = null

    override suspend fun webDavMetadataCandidatesForAlbum(albumId: Long) = emptyList<MetadataRefreshCandidate>()

    override suspend fun missingWebDavMetadataCandidates(target: String) = emptyList<MetadataRefreshCandidate>()

    override suspend fun countForTrack(trackId: Long): Int {
        return 0
    }

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
private const val SECOND_TRACK_ID = 8L
private const val SECOND_TRACK_TITLE = "Neon"
private const val SECOND_TRACK_PATH = "/Music/Neon.flac"
private const val PLAYLIST_ID = 3L

private val TEST_RESOURCE = PlaybackResource(
    uri = "http://127.0.0.1/track.flac",
    mimeType = "audio/flac",
)
