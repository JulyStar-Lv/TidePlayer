package io.github.julystar.musicapp.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.media.NavidromeArtworkResolver
import io.github.julystar.musicapp.core.data.media.LegacyArtworkRepository
import io.github.julystar.musicapp.core.data.settings.DataStoreSettingsRepository
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemPropertyEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.domain.importing.TrackMetadataPrefetcher
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshResult
import io.github.julystar.musicapp.source.api.NavidromeProviderConfiguration
import io.github.julystar.musicapp.source.api.NavidromeProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenSubsonicCue
import io.github.julystar.musicapp.source.api.OpenSubsonicCueLine
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsAgent
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsLine
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrack
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrackKind
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsUnsupportedException
import io.github.julystar.musicapp.source.api.OpenSubsonicStructuredLyricsDocument
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerLyrics
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import java.io.File
import java.nio.file.Files
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteMusicException
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.data.security.CredentialStore

class NavidromeResourceResolverIntegrationTest {
    @Test
    fun openSubsonicUnsupportedUsesClassicButAuthAndCancellationDoNotFallback() = runBlocking {
        val database = buildDatabase()
        try {
            seedDatabase(database, ProviderTypes.OpenSubsonic)
            val gateway = ResolverGateway().also {
                it.structuredError = io.github.julystar.musicapp.source.api.OpenSubsonicLyricsUnsupportedException()
                it.lyricsResult = Result.success(RemoteServerLyrics("[00:01.00]classic", "lrc", true))
            }
            val resolver = OpenSubsonicLyricsResolver(
                database.metadataDao(), database.trackSourceRefDao(), database.sourceItemDao(),
                database.sourceAccountDao(), gateway,
            )
            assertEquals("[00:01.00]classic", resolver.load(1, "Song", "Artist")?.content)
            assertEquals(1, gateway.lyricsCalls)

            database.metadataDao().deleteLyricsForTracksBySource(listOf(1), "OpenSubsonic")
            gateway.structuredError = RemoteMusicException.Unauthorized()
            gateway.lyricsCalls = 0
            assertFailsWith<RemoteMusicException.Unauthorized> { resolver.load(1, "Song", "Artist") }
            assertEquals(0, gateway.lyricsCalls)

            gateway.structuredError = RemoteMusicException.PermissionDenied()
            assertFailsWith<RemoteMusicException.PermissionDenied> { resolver.load(1, "Song", "Artist") }
            assertEquals(0, gateway.lyricsCalls)

            gateway.structuredError = null
            gateway.structuredDocument = null
            gateway.throwOpenCancellation = true
            assertFailsWith<CancellationException> { resolver.load(1, "Song", "Artist") }
            assertEquals(0, gateway.lyricsCalls)
        } finally {
            database.close()
        }
    }

    @Test
    fun openSubsonicStructuredLyricsAreProjectedAndCachedByRealRepository() = runBlocking {
        val database = buildDatabase()
        val file = File.createTempFile("musicapp-open-subsonic-", ".preferences_pb").apply { delete() }
        try {
            seedDatabase(database, ProviderTypes.OpenSubsonic)
            database.metadataDao().upsertLyrics(listOf(LyricsEntity(
                trackId = 1, format = "plain", language = null, synchronized = false,
                content = "embedded fallback", sourcePath = "/music/song.lrc", updatedAt = 1,
                sourceKind = "EmbeddedPlain",
            )))
            val gateway = ResolverGateway().also {
                it.structuredDocument = OpenSubsonicStructuredLyricsDocument(
                    tracks = listOf(
                        OpenSubsonicLyricsTrack(
                            kind = OpenSubsonicLyricsTrackKind.Main,
                            language = "xxx",
                            offsetMs = 10,
                            synced = true,
                            lines = listOf(
                                OpenSubsonicLyricsLine(100, "main one"),
                                OpenSubsonicLyricsLine(1_100, "main two"),
                            ),
                            agents = listOf(
                                OpenSubsonicLyricsAgent(id = "lead", role = "main"),
                                OpenSubsonicLyricsAgent(id = "background", role = "background"),
                            ),
                            cueLines = listOf(
                                OpenSubsonicCueLine(0, 100, 900, "background duplicate", "background"),
                                OpenSubsonicCueLine(
                                    index = 0, startMs = 100, endMs = 900, value = "main one",
                                    agentId = "lead",
                                    cues = listOf(OpenSubsonicCue(100, 300, "你", 0, 2)),
                                ),
                                OpenSubsonicCueLine(1, 1_100, 1_900, "main two", "lead"),
                            ),
                        ),
                        OpenSubsonicLyricsTrack(
                            kind = OpenSubsonicLyricsTrackKind.Translation,
                            synced = true,
                            lines = listOf(OpenSubsonicLyricsLine(1_000, "translation near second")),
                        ),
                        OpenSubsonicLyricsTrack(
                            kind = OpenSubsonicLyricsTrackKind.Pronunciation,
                            synced = true,
                            lines = listOf(OpenSubsonicLyricsLine(100, "pronunciation near first")),
                        ),
                    ),
                )
            }
            val dataStore = createAppDataStore { file.absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            val prefetcher = TrackMetadataPrefetcher(
                database.metadataDao(), database.trackDao(),
                object : MetadataRefreshController {
                    override suspend fun refresh(request: MetadataRefreshRequest) =
                        error("prefetch must not run when fallback is persisted")
                },
            )
            val repository = LyricsRepositoryImpl(
                database.metadataDao(), database.trackDao(), prefetcher, settings,
                null,
                OpenSubsonicLyricsResolver(
                    database.metadataDao(), database.trackSourceRefDao(),
                    database.sourceItemDao(), database.sourceAccountDao(), gateway,
                ),
            )
            val first = repository.loadLyrics(1)
            val persistedBeforePlayback = database.metadataDao().getLyricsCandidates(1)
                .first { it.sourceKind == "OpenSubsonic" }
            val playback = persistedBeforePlayback.toPlaybackLyrics()
            assertEquals(2, playback.lines.size)
            assertTrue(playback.lines.first().words.isNotEmpty())
            assertTrue(playback.lines.first().text.contains("你"))
            assertTrue(playback.lines.none { it.text.contains("background duplicate") })
            val firstText = first.lines.joinToString("\n")
            assertTrue(firstText.contains("你"), firstText)
            assertTrue(firstText.contains("translation near second"), firstText)
            assertTrue(firstText.contains("pronunciation near first"), firstText)
            val persisted = database.metadataDao().getLyricsCandidates(1).first { it.sourceKind == "OpenSubsonic" }
            assertNull(persisted.language)
            assertEquals(gateway.structuredDocument, persisted.structuredContent?.let(io.github.julystar.musicapp.source.server.OpenSubsonicLyricsCodec::decode))
            assertTrue("token=secret" !in persisted.structuredContent.orEmpty())
            assertTrue("https://server.test" !in persisted.sourcePath.orEmpty())
            val second = repository.loadLyrics(1)
            assertTrue(second.lines.joinToString("\n").contains("你"))
            assertEquals(1, gateway.structuredCalls)

            database.metadataDao().deleteLyricsForTracksBySource(listOf(1), "OpenSubsonic")
            gateway.structuredDocument = null
            gateway.structuredError = OpenSubsonicLyricsUnsupportedException()
            gateway.lyricsResult = Result.failure(IllegalStateException("classic unavailable"))
            val fallback = repository.loadLyrics(1)
            assertTrue(fallback.lines.joinToString("\n").contains("embedded fallback"))
            assertEquals(2, gateway.structuredCalls)
            assertEquals(1, gateway.lyricsCalls)

            database.trackSourceRefDao().upsertAll(
                listOf(database.trackSourceRefDao().findByTrackId(1).single().copy(isAvailable = false))
            )
            val structuredCallsBeforeUnavailable = gateway.structuredCalls
            val lyricsCallsBeforeUnavailable = gateway.lyricsCalls
            val unavailableRefFallback = repository.loadLyrics(1)
            assertTrue(unavailableRefFallback.lines.joinToString("\n").contains("embedded fallback"))
            assertEquals(structuredCallsBeforeUnavailable, gateway.structuredCalls)
            assertEquals(lyricsCallsBeforeUnavailable, gateway.lyricsCalls)
        } finally {
            database.close()
            file.delete()
        }
    }

    @Test
    fun artworkAndLyricsUseSafePersistedServerResults() = runBlocking {
        val database = buildDatabase()
        try {
            seedDatabase(database)
            val gateway = ResolverGateway()
            var fetches = 0
            val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
            val artworkResolver = NavidromeArtworkResolver(
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                sourceItemDao = database.sourceItemDao(),
                sourceAccountDao = database.sourceAccountDao(),
                remoteServerGateway = gateway,
                fetchRemoteBytes = { _, _ ->
                    fetches++
                    image
                },
            )

            assertEquals(image.toList(), artworkResolver.load(Artwork.LibraryTrack(1))?.toList())
            assertEquals(1, gateway.coverArtCalls)
            assertEquals(768, gateway.lastCoverSize)
            assertEquals(1, fetches)
            val artworkEntity = database.metadataDao().getArtworkForTrack(1)
            assertEquals("image/jpeg", artworkEntity?.mimeType)
            assertTrue(artworkEntity?.contentHash.orEmpty().contains("token").not())
            assertTrue(artworkEntity?.localPath.orEmpty().contains("token").not())
            assertTrue("https://server.test" !in artworkEntity?.contentHash.orEmpty())
            assertTrue("https://server.test" !in artworkEntity?.localPath.orEmpty())

            val secondResolver = NavidromeArtworkResolver(
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                sourceItemDao = database.sourceItemDao(),
                sourceAccountDao = database.sourceAccountDao(),
                remoteServerGateway = gateway,
                fetchRemoteBytes = { _, _ -> error("persisted artwork should avoid fetch") },
            )
            assertEquals(image.toList(), secondResolver.load(Artwork.LibraryTrack(1))?.toList())
            assertEquals(1, gateway.coverArtCalls)

            val lyricsResolver = NavidromeLyricsResolver(
                metadataDao = database.metadataDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                sourceItemDao = database.sourceItemDao(),
                sourceAccountDao = database.sourceAccountDao(),
                remoteServerGateway = gateway,
            )
            val firstLyrics = lyricsResolver.load(1, "Song", "Artist")
            assertEquals("[00:01.00]server", firstLyrics?.content)
            assertEquals(1, gateway.lyricsCalls)
            assertTrue(database.metadataDao().getLyricsCandidates(1).single().sourcePath!!.startsWith("navidrome/7/"))
            assertTrue(database.metadataDao().getLyricsCandidates(1).single().content.contains("server"))

            val secondLyrics = lyricsResolver.load(1, "Song", "Artist")
            assertEquals(firstLyrics, secondLyrics)
            assertEquals(1, gateway.lyricsCalls)

        } finally {
            database.close()
        }
    }

    @Test
    fun lyricsRepositoryPrefersServerThenFallsBackAndPropagatesCancellation() = runBlocking {
        val database = buildDatabase()
        val file = File.createTempFile("musicapp-lyrics-", ".preferences_pb").apply { delete() }
        try {
            seedDatabase(database)
            database.metadataDao().upsertLyrics(
                listOf(
                    LyricsEntity(
                        trackId = 1,
                        format = "plain",
                        language = null,
                        synchronized = false,
                        content = "embedded fallback",
                        sourcePath = "/music/song.lrc",
                        updatedAt = 1,
                        sourceKind = "EmbeddedPlain",
                    )
                )
            )
            val dataStore = createAppDataStore { file.absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            val gateway = ResolverGateway()
            val resolver = NavidromeLyricsResolver(
                database.metadataDao(),
                database.trackSourceRefDao(),
                database.sourceItemDao(),
                database.sourceAccountDao(),
                gateway,
            )
            val prefetcher = TrackMetadataPrefetcher(
                database.metadataDao(),
                database.trackDao(),
                object : MetadataRefreshController {
                    override suspend fun refresh(request: MetadataRefreshRequest) =
                        error("fallback was already persisted; prefetch must not run")
                },
            )
            val repository = LyricsRepositoryImpl(
                database.metadataDao(), database.trackDao(), prefetcher, settings, resolver,
            )

            val server = repository.loadLyrics(1)
            assertTrue(server.lines.joinToString("\n").contains("server"))
            val persisted = database.metadataDao().getLyricsCandidates(1)
                .first { it.sourceKind == "Navidrome" }
            assertTrue("token=secret" !in persisted.sourcePath.orEmpty())
            assertTrue("token=secret" !in persisted.content)
            assertTrue("password" !in persisted.sourcePath.orEmpty())
            assertTrue("https://server.test" !in persisted.sourcePath.orEmpty())
            assertTrue("https://server.test" !in persisted.content)

            database.metadataDao().deleteLyricsForTracksBySource(listOf(1), "Navidrome")
            gateway.lyricsResult = Result.failure(IllegalStateException("unavailable"))
            val fallback = repository.loadLyrics(1)
            assertTrue(fallback.lines.joinToString("\n").contains("embedded fallback"))

            gateway.throwCancellation = true
            assertFailsWith<CancellationException> { repository.loadLyrics(1) }
            Unit
        } finally {
            database.close()
            file.delete()
        }
    }

    @Test
    fun invalidArtworkAndUnavailableRefDoNotPersistOrFetchFurther() = runBlocking {
        val database = buildDatabase()
        try {
            seedDatabase(database)
            val gateway = ResolverGateway().also { it.imageBytes = byteArrayOf(1, 2, 3) }
            var fetches = 0
            val resolver = NavidromeArtworkResolver(
                database.trackDao(), database.metadataDao(), database.trackSourceRefDao(),
                database.sourceItemDao(), database.sourceAccountDao(), gateway,
                fetchRemoteBytes = { _, _ -> fetches++; gateway.imageBytes },
            )
            assertNull(resolver.load(Artwork.LibraryTrack(1)))
            assertEquals(1, gateway.coverArtCalls)
            assertEquals(1, fetches)
            assertNull(database.metadataDao().getArtworkForTrack(1))

            database.trackSourceRefDao().upsertAll(
                listOf(database.trackSourceRefDao().findByTrackId(1).single().copy(isAvailable = false))
            )
            assertNull(resolver.load(Artwork.LibraryTrack(1)))
            assertEquals(1, gateway.coverArtCalls)
            assertEquals(1, fetches)
        } finally {
            database.close()
        }
    }

    @Test
    fun embyArtworkUsesImageTagAndTagChangeInvalidatesSafeCache() = runBlocking {
        val database = buildDatabase()
        try {
            seedDatabase(database, ProviderTypes.Emby)
            database.sourceItemDao().deletePropertyForItems(listOf(1), "coverArtId")
            database.sourceItemDao().upsertProperties(
                listOf(SourceItemPropertyEntity(1, "imageTag", "tag-1", null, null, null))
            )
            val gateway = ResolverGateway()
            var fetches = 0
            val resolver = NavidromeArtworkResolver(
                database.trackDao(), database.metadataDao(), database.trackSourceRefDao(),
                database.sourceItemDao(), database.sourceAccountDao(), gateway,
                fetchRemoteBytes = { _, _ -> fetches++; gateway.imageBytes },
            )
            assertTrue(resolver.load(Artwork.LibraryTrack(1))!!.isNotEmpty())
            assertEquals(1, gateway.coverArtCalls)
            assertEquals(512, gateway.lastCoverSize)
            assertEquals("tag-1", gateway.lastImageTag)
            val firstKey = database.metadataDao().getArtworkForTrack(1)?.contentHash
            database.sourceItemDao().deletePropertyForItems(listOf(1), "imageTag")
            database.sourceItemDao().upsertProperties(
                listOf(SourceItemPropertyEntity(1, "imageTag", "tag-2", null, null, null))
            )
            assertTrue(resolver.load(Artwork.LibraryTrack(1))!!.isNotEmpty())
            assertEquals(2, gateway.coverArtCalls)
            assertEquals("tag-2", gateway.lastImageTag)
            assertTrue(firstKey != database.metadataDao().getArtworkForTrack(1)?.contentHash)
            assertEquals(2, fetches)
        } finally {
            database.close()
        }
    }

    @Test
    fun legacyArtworkRepositoryRechecksRemoteTagInsteadOfArtworkOnlyCache() = runBlocking {
        val database = buildDatabase()
        val tempDir = Files.createTempDirectory("musicapp-legacy-artwork-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            seedDatabase(database, ProviderTypes.Emby)
            database.sourceItemDao().deletePropertyForItems(listOf(1), "coverArtId")
            database.sourceItemDao().upsertProperties(
                listOf(SourceItemPropertyEntity(1, "imageTag", "tag-1", null, null, null))
            )
            val gateway = ResolverGateway().also {
                it.imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)
            }
            val resolver = NavidromeArtworkResolver(
                database.trackDao(), database.metadataDao(), database.trackSourceRefDao(),
                database.sourceItemDao(), database.sourceAccountDao(), gateway,
                fetchRemoteBytes = { _, _ -> gateway.imageBytes },
            )
            val bridge = Bridge(
                appDocumentDir = tempDir.absolutePath,
                appCacheDir = tempDir.absolutePath,
                toastRepository = ToastRepositoryImpl(scope),
            )
            val storage = StorageRepositoryImpl(
                bridge = bridge,
                scope = scope,
                sourceAccountDao = database.sourceAccountDao(),
                credentialStore = NoopCredentialStore(),
            )
            val roomStore = RoomLibraryStore(
                database = database,
                trackDao = database.trackDao(),
                sourceItemDao = database.sourceItemDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                playlistDao = database.playlistDao(),
                metadataDao = database.metadataDao(),
            )
            val repository = LegacyArtworkRepository(
                bridge = bridge,
                storageRepository = storage,
                roomLibraryStore = roomStore,
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
                pluginArtworkResolver = null,
                navidromeArtworkResolver = resolver,
            )
            assertTrue(repository.load(Artwork.LibraryTrack(1))!!.contentEquals(gateway.imageBytes))
            assertEquals(1, gateway.coverArtCalls)
            database.sourceItemDao().deletePropertyForItems(listOf(1), "imageTag")
            database.sourceItemDao().upsertProperties(
                listOf(SourceItemPropertyEntity(1, "imageTag", "tag-2", null, null, null))
            )
            gateway.imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x02)
            assertTrue(repository.load(Artwork.LibraryTrack(1))!!.contentEquals(gateway.imageBytes))
            assertEquals(2, gateway.coverArtCalls)
            assertTrue(repository.cached(Artwork.LibraryTrack(1)) == null)
        } finally {
            scope.cancel()
            database.close()
            tempDir.deleteRecursively()
        }
    }

    private fun buildDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase> {
        AppDatabaseConstructor.initialize()
    }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()

    private suspend fun seedDatabase(database: AppDatabase, providerType: String = ProviderTypes.Navidrome) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = 7, providerType = providerType, displayName = providerType,
                endpoint = "https://server.test", externalAccountId = null,
                credentialRef = "credential-7", priority = 0, enabled = true,
                createdAt = 1, updatedAt = 1,
                providerConfig = NavidromeProviderConfigurationCodec.encode(
                    NavidromeProviderConfiguration(coverArtSize = 768)
                ),
            )
        )
        database.sourceItemDao().upsertAll(
            listOf(
                SourceItemEntity(
                    id = 1, sourceAccountId = 7, libraryRootId = null,
                    itemType = SourceItemTypes.Track, providerItemId = "song/opaque",
                    parentProviderItemId = null, canonicalPath = null, displayPath = null,
                    displayName = "Song", mimeType = "audio/flac", sizeBytes = null,
                    etag = null, revision = null, createdAtRemote = null, modifiedAtRemote = null,
                    contentHash = null, audioFingerprint = null, isDeleted = false,
                    firstSyncedAt = 1, lastSyncedAt = 1, lastSeenScanId = "scan",
                )
            )
        )
        database.sourceItemDao().upsertProperties(
            listOf(SourceItemPropertyEntity(1, "coverArtId", "cover/id", null, null, null))
        )
        database.trackDao().upsertAll(listOf(TrackEntity(
            id = 1, title = "Song", sortTitle = null, albumId = null, albumArtist = null,
            composer = null, comment = null, grouping = null, durationMs = 1000,
            discNumber = null, discTotal = null, trackNumber = null, trackTotal = null,
            year = null, date = null, sampleRate = null, bitRate = null, bitsPerSample = null,
            channels = null, channelLayout = null, codec = null, container = null, lossless = null,
            createdAt = 1, updatedAt = 1, artist = "Artist",
        )))
        database.trackSourceRefDao().upsertAll(listOf(TrackSourceRefEntity(
            trackId = 1, sourceItemId = 1, role = "PRIMARY", matchMethod = "SOURCE",
            matchConfidence = 100, isPreferred = true, isAvailable = true,
            isDownloaded = false, playable = true, downloadable = true, codec = "flac",
            container = "flac", bitRate = null, sampleRate = null, bitsPerSample = null,
            channels = null, lossless = true, createdAt = 1, updatedAt = 1,
        )))
    }
}

private class ResolverGateway : RemoteServerGateway {
    var coverArtCalls = 0
    var lastCoverSize: Int? = null
    var lastImageTag: String? = null
    var lyricsCalls = 0
    var lyricsResult: Result<RemoteServerLyrics> =
        Result.success(RemoteServerLyrics("[00:01.00]server", "lrc", synchronized = true))
    var throwCancellation = false
    var imageBytes: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    var structuredDocument: OpenSubsonicStructuredLyricsDocument? = null
    var structuredCalls = 0
    var structuredError: Throwable? = null
    var throwOpenCancellation = false

    override suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult =
        SourceAuthResult.Success

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ): Flow<Result<RemoteServerTrackPage>> = emptyFlow()

    override suspend fun playback(kind: RemoteServerKind, encodedRemoteId: String): SourcePlaybackResult =
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)

    override suspend fun coverArt(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        coverArtId: String,
        size: Int,
        imageTag: String?,
    ): SourcePlaybackResult {
        coverArtCalls++
        lastCoverSize = size
        lastImageTag = imageTag
        return SourcePlaybackResult.Success(PlaybackResource("https://server.test/cover?token=secret"))
    }

    override suspend fun lyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        artist: String,
        title: String,
    ): Result<RemoteServerLyrics> {
        lyricsCalls++
        if (throwCancellation) throw CancellationException("test cancellation")
        return lyricsResult
    }

    override suspend fun openSubsonicLyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteId: String,
    ): Result<OpenSubsonicStructuredLyricsDocument> {
        structuredCalls++
        if (throwOpenCancellation) throw CancellationException("test cancellation")
        structuredError?.let { return Result.failure(it) }
        val document = structuredDocument
        return if (document != null) Result.success(document)
        else Result.failure(IllegalStateException("structured lyrics unavailable"))
    }
}

private class NoopCredentialStore : CredentialStore {
    override suspend fun load(storageId: Long): StoredCredential? = null
    override suspend fun save(storageId: Long, credential: StoredCredential) = Unit
    override suspend fun delete(storageId: Long) = Unit
    override suspend fun clear() = Unit
}
