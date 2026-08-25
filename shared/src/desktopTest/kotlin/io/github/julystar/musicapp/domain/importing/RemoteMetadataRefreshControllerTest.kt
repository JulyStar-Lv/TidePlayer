package io.github.julystar.musicapp.domain.importing

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.MetadataScanOptions
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.metadata.UnifiedMetadataRepository
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshScope
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteArtwork
import uniffi.app_backend.RemoteEmbeddedLyrics
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.RemoteMetadataResult
import uniffi.app_backend.RemoteRawMetadataEntry
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageEntryLoc

class RemoteMetadataRefreshControllerTest {
    @Test
    fun allRefreshPersistsSourceMetadataWithOneReaderCallAndProtectsLockedTrack() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.Local)
            database.trackDao().upsertAll(
                listOf(track().copy(title = "Filename title", metadataSource = TrackMetadataSources.Filename)),
            )
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(
                database = database,
                metadataRepository = reader,
                unifiedMetadataRepository = UnifiedMetadataRepository(
                    database,
                    database.trackDao(),
                    database.metadataDao(),
                ),
            )

            controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.All,
                    allowNetwork = false,
                ),
            )

            assertEquals(1, reader.options.size)
            assertEquals(MetadataScanOptions(true, true, true), reader.options.single())
            assertEquals("Song", database.trackDao().get(1)?.title)
            assertEquals(TrackMetadataSources.File, database.trackDao().get(1)?.metadataSource)

            database.trackDao().upsertAll(
                listOf(database.trackDao().get(1)!!.copy(title = "Locked", metadataLocked = true)),
            )
            controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.All,
                ),
            )
            assertEquals("Locked", database.trackDao().get(1)?.title)
            assertEquals(2, reader.options.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun allRefreshUsesServerPolicyForRemoteFilenameMetadata() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.WebDav)
            database.trackDao().upsertAll(
                listOf(track().copy(title = "Filename title", metadataSource = TrackMetadataSources.Filename)),
            )
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(
                database,
                reader,
                UnifiedMetadataRepository(database, database.trackDao(), database.metadataDao()),
            )

            controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.All,
                ),
            )

            assertEquals("Song", database.trackDao().get(1)?.title)
            assertEquals(TrackMetadataSources.Server, database.trackDao().get(1)?.metadataSource)
            assertEquals(1, reader.options.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun localOnlyRefreshSkipsRemoteCandidateBeforeReaderIo() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.WebDav)
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(database, reader)

            val result = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.All,
                    allowNetwork = false,
                ),
            )

            assertEquals(0, result.requestedCount)
            assertTrue(reader.options.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun nonAllRefreshDoesNotChangeSemanticMetadata() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.Local)
            database.trackDao().upsertAll(
                listOf(track().copy(title = "Filename title", metadataSource = TrackMetadataSources.Filename)),
            )
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(
                database,
                reader,
                UnifiedMetadataRepository(database, database.trackDao(), database.metadataDao()),
            )

            controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.Artwork,
                ),
            )

            assertEquals("Filename title", database.trackDao().get(1)?.title)
            assertEquals(TrackMetadataSources.Filename, database.trackDao().get(1)?.metadataSource)
            assertEquals(MetadataScanOptions(true, false, false), reader.options.single())
        } finally {
            database.close()
        }
    }

    @Test
    fun trackBackfillReadsPreferredLocalSource() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.Local)
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(database, reader)

            val result = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.Artwork,
                )
            )

            assertEquals(1, result.refreshedCount)
            assertEquals(MetadataScanOptions(true, false, false), reader.options.single())
            assertEquals("art-hash", database.metadataDao().getArtworkForAlbum(7)?.contentHash)
        } finally {
            database.close()
        }
    }

    @Test
    fun missingBackfillReadsUnchangedFilesWithMinimumOptions() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            seedTrack(database, ProviderTypes.WebDav)
            assertEquals(
                1L,
                database.trackSourceRefDao().metadataResetCandidateForTrack(1)?.sourceItemId,
            )
            assertEquals(
                7L,
                database.trackSourceRefDao()
                    .missingWebDavMetadataCandidates(MetadataRefreshTarget.Artwork.name)
                    .single()
                    .albumId,
            )
            val reader = FakeRemoteMetadataReader()
            val controller = RemoteMetadataRefreshController(database, reader)

            val artworkResult = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.MissingWebDavTracks,
                    target = MetadataRefreshTarget.Artwork,
                )
            )

            assertEquals(MetadataScanOptions(true, false, false), reader.options.single())
            assertEquals(1, artworkResult.refreshedCount)
            assertEquals(256, artworkResult.metadataFetchedBytes)
            assertNotNull(database.metadataDao().getArtworkForAlbum(7))
            assertEquals(
                true,
                database.trackSourceRefDao().findByTrackId(1).single().hasEmbeddedArtwork,
            )
            assertNull(database.metadataDao().getLyrics(1))
            assertTrue(database.metadataDao().rawMetadataForTrack(1).isEmpty())

            val lyricsResult = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.MissingWebDavTracks,
                    target = MetadataRefreshTarget.Lyrics,
                )
            )

            assertEquals(MetadataScanOptions(false, true, false), reader.options.last())
            assertEquals(1, lyricsResult.refreshedCount)
            assertEquals("Backfilled lyrics", database.metadataDao().getLyrics(1)?.content)
            assertTrue(database.metadataDao().rawMetadataForTrack(1).isEmpty())

            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    database.metadataDao().updateOptionalMetadata(
                        updates = listOf(
                            OptionalMetadataUpdate(
                                trackId = 1,
                                albumId = 7,
                                metadata = reader.metadataForTest(),
                            )
                        ),
                        options = MetadataScanMode.Fast.toOptions(),
                        now = 20,
                    )
                }
            }
            assertEquals("art-hash", database.metadataDao().getArtworkForAlbum(7)?.contentHash)
            assertEquals("Backfilled lyrics", database.metadataDao().getLyrics(1)?.content)
            assertTrue(database.metadataDao().rawMetadataForTrack(1).isEmpty())

            val noLongerMissing = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.MissingWebDavTracks,
                    target = MetadataRefreshTarget.Lyrics,
                )
            )
            assertEquals(0, noLongerMissing.requestedCount)
            assertEquals(2, reader.options.size)

            val albumResult = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Album(7),
                    target = MetadataRefreshTarget.RawMetadata,
                )
            )
            assertEquals(1, albumResult.refreshedCount)
            assertEquals(MetadataScanOptions(false, false, true), reader.options.last())
            assertEquals("Composer", database.metadataDao().rawMetadataForTrack(1).single().value)

            val trackResult = controller.refresh(
                MetadataRefreshRequest(
                    scope = MetadataRefreshScope.Track(1),
                    target = MetadataRefreshTarget.Lyrics,
                )
            )
            assertEquals(1, trackResult.refreshedCount)
        } finally {
            database.close()
        }
    }

    private suspend fun seedTrack(database: AppDatabase, providerType: String) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = 1,
                providerType = providerType,
                displayName = providerType,
                endpoint = if (providerType == ProviderTypes.WebDav) {
                    "https://example.invalid/dav"
                } else {
                    null
                },
                externalAccountId = null,
                credentialRef = "credential",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.libraryRootDao().upsert(
            LibraryRootEntity(
                id = 1,
                sourceAccountId = 1,
                providerRootId = null,
                canonicalPath = "/Music",
                displayName = "Music",
                syncStatus = "SYNCED",
                syncCursor = null,
                lastSyncAt = 10,
                createdAt = 1,
                updatedAt = 10,
            )
        )
        database.sourceItemDao().upsertAll(
            listOf(
                SourceItemEntity(
                    id = 1,
                    sourceAccountId = 1,
                    libraryRootId = 1,
                    itemType = SourceItemTypes.Track,
                    providerItemId = "remote-1",
                    parentProviderItemId = null,
                    canonicalPath = "/Music/Song.flac",
                    displayPath = "/Music/Song.flac",
                    displayName = "Song.flac",
                    mimeType = "audio/flac",
                    sizeBytes = 1024,
                    etag = "unchanged-etag",
                    revision = null,
                    createdAtRemote = 1,
                    modifiedAtRemote = 1,
                    contentHash = null,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = 1,
                    lastSyncedAt = 10,
                    lastSeenScanId = "previous-scan",
                )
            )
        )
        database.trackDao().upsertAll(listOf(track()))
        database.trackSourceRefDao().upsertAll(
            listOf(
                TrackSourceRefEntity(
                    trackId = 1,
                    sourceItemId = 1,
                    role = "PRIMARY",
                    matchMethod = "SOURCE",
                    matchConfidence = 100,
                    isPreferred = true,
                    isAvailable = true,
                    isDownloaded = false,
                    playable = true,
                    downloadable = true,
                    codec = "FLAC",
                    container = "FLAC",
                    bitRate = null,
                    sampleRate = null,
                    bitsPerSample = null,
                    channels = null,
                    lossless = true,
                    createdAt = 1,
                    updatedAt = 10,
                )
            )
        )
    }

    private fun track() = TrackEntity(
        id = 1,
        title = "Song",
        sortTitle = null,
        albumId = 7,
        albumArtist = null,
        composer = null,
        comment = null,
        grouping = null,
        durationMs = 1000,
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
        codec = "FLAC",
        container = "FLAC",
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
    )
}

private class FakeRemoteMetadataReader : RemoteMetadataReader {
    val options = mutableListOf<MetadataScanOptions>()

    override suspend fun read(entry: StorageEntry, options: MetadataScanOptions): RemoteMetadata {
        this.options += options
        return metadataForTest()
    }

    override suspend fun readBatch(
        entries: List<StorageEntry>,
        concurrency: UInt,
        options: MetadataScanOptions,
    ): List<RemoteMetadataResult> {
        this.options += options
        return entries.mapIndexed { index, entry ->
            RemoteMetadataResult(
                requestIndex = index.toULong(),
                entry = StorageEntryLoc(entry.storageId, entry.path),
                metadata = metadataForTest(),
                error = null,
            )
        }
    }

    fun metadataForTest() = RemoteMetadata(
        title = "Song",
        artist = null,
        artists = emptyList(),
        albumArtist = null,
        album = null,
        composer = null,
        lyricist = null,
        conductor = null,
        genre = null,
        grouping = null,
        comment = null,
        copyright = null,
        publisher = null,
        date = null,
        originalReleaseDate = null,
        trackNumber = null,
        trackTotal = null,
        discNumber = null,
        discTotal = null,
        bpm = null,
        musicalKey = null,
        isrc = null,
        musicbrainzRecordingId = null,
        musicbrainzTrackId = null,
        musicbrainzReleaseId = null,
        musicbrainzReleaseGroupId = null,
        musicbrainzArtistId = null,
        musicbrainzReleaseArtistId = null,
        musicbrainzWorkId = null,
        replayGainTrackGain = null,
        replayGainTrackPeak = null,
        replayGainAlbumGain = null,
        replayGainAlbumPeak = null,
        lyrics = RemoteEmbeddedLyrics("Backfilled lyrics", false, null, null),
        embeddedLyricsKind = "Plain",
        artwork = RemoteArtwork("art-hash", "/cache/art.jpg", null, null, null, "image/jpeg", "CoverFront"),
        hasEmbeddedArtwork = true,
        rawMetadata = listOf(RemoteRawMetadataEntry("Composer", "Composer", null, null)),
        durationMs = 1u,
        sampleRate = null,
        bitDepth = null,
        channels = null,
        channelLayout = null,
        overallBitrate = null,
        audioBitrate = null,
        codec = null,
        container = null,
        lossless = null,
        metadataRequestCount = 2u,
        metadataFetchedBytes = 256u,
        metadataElapsedMs = 3u,
        artworkCachedBytes = 64u,
    )
}
