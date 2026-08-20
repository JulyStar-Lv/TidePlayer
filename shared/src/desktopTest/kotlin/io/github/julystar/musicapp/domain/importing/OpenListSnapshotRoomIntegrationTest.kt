package io.github.julystar.musicapp.domain.importing

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.MetadataScanOptions
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import io.github.julystar.musicapp.source.storage.RemoteScannerRepository
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncScanRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteArtwork
import uniffi.app_backend.RemoteEmbeddedLyrics
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.RemoteMetadataResult
import uniffi.app_backend.RemoteRawMetadataEntry
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageEntryLoc

class OpenListSnapshotRoomIntegrationTest {
    @Test
    fun secondOpenListSnapshotReadsOnlyChangedEntriesAndMarksOnlyThatSourceDeleted() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tempDir = Files.createTempDirectory("openlist-snapshot").toFile()
        val bridge = Bridge(tempDir.absolutePath, tempDir.absolutePath, io.github.julystar.musicapp.core.data.ToastRepositoryImpl(scope))
        val credentials = MemoryCredentialStore()
        val accountId = 114L
        val rawRoot = "/音乐/%25 #? 😀\\folder"
        val reader = CountingMetadataReader()
        try {
            database.sourceAccountDao().upsert(
                SourceAccountEntity(
                    id = accountId,
                    providerType = ProviderTypes.OpenList,
                    displayName = "OpenList",
                    endpoint = "https://openlist.example",
                    externalAccountId = null,
                    credentialRef = "openlist-$accountId",
                    priority = 0,
                    enabled = true,
                    createdAt = 1,
                    updatedAt = 1,
                    rootPath = "/",
                ),
            )
            credentials.save(accountId, StoredCredential("alice", "password", false))
            val repository = StorageRepositoryImpl(
                bridge = bridge,
                scope = scope,
                sourceAccountDao = database.sourceAccountDao(),
                credentialStore = credentials,
            )
            val coordinator = RemoteLibraryImportCoordinator(
                database = database,
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
                syncDao = database.syncDao(),
                metadataRepository = reader,
                remoteScannerRepository = RemoteScannerRepository(bridge, repository),
                storageRepository = repository,
            )
            val first = (0 until 114).map { entry(accountId, it, "v1", rawRoot) }
            val firstResult = coordinator.importCompleteSnapshot(
                request(accountId, first, "openlist-114-first", rawRoot),
            )
            assertEquals(114L, firstResult.addedCount)
            assertEquals(114, reader.requestedEntries)
            val firstRoot = database.libraryRootDao().findByPath(accountId, rawRoot)
            assertEquals(rawRoot, firstRoot?.providerRootId)
            assertEquals(rawRoot, firstRoot?.canonicalPath)
            val firstItem = database.sourceItemDao()
                .findByProviderItemIds(accountId, listOf("$rawRoot/track-0.flac"))
                .single()
            assertEquals("$rawRoot/track-0.flac", firstItem.providerItemId)
            assertEquals("$rawRoot/track-0.flac", firstItem.canonicalPath)
            assertTrue(reader.requestedPaths.all { it.startsWith("$rawRoot/") })

            // Give one soon-to-be-deleted track a second provider ref. OpenList deletion
            // must only make its own ref unavailable.
            val deletedOpenListItem = database.sourceItemDao()
                .findByProviderItemIds(accountId, listOf("$rawRoot/track-110.flac")).single()
            val sharedTrackId = database.trackSourceRefDao()
                .findBySourceItemIds(listOf(deletedOpenListItem.id)).single().trackId
            val otherAccount = 115L
            database.sourceAccountDao().upsert(
                SourceAccountEntity(
                    id = otherAccount,
                    providerType = ProviderTypes.WebDav,
                    displayName = "Other provider",
                    endpoint = "https://other.example",
                    externalAccountId = null,
                    credentialRef = "other-$otherAccount",
                    priority = 0,
                    enabled = true,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            val otherRootId = database.libraryRootDao().upsert(
                LibraryRootEntity(
                    sourceAccountId = otherAccount,
                    providerRootId = "/",
                    canonicalPath = "/",
                    displayName = "Other",
                    syncStatus = "READY",
                    syncCursor = null,
                    lastSyncAt = 1,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            val otherItemId = database.sourceItemDao().upsertAll(
                listOf(
                    SourceItemEntity(
                        sourceAccountId = otherAccount,
                        libraryRootId = otherRootId,
                        itemType = "track",
                        providerItemId = "other-track-110",
                        parentProviderItemId = "/",
                        canonicalPath = "/other-track-110.flac",
                        displayPath = "/other-track-110.flac",
                        displayName = "other-track-110.flac",
                        mimeType = "audio/flac",
                        sizeBytes = 100,
                        etag = "other",
                        revision = null,
                        createdAtRemote = 1,
                        modifiedAtRemote = 1,
                        contentHash = null,
                        audioFingerprint = null,
                        isDeleted = false,
                        firstSyncedAt = 1,
                        lastSyncedAt = 1,
                        lastSeenScanId = "other",
                    ),
                ),
            ).single()
            val openListRef = database.trackSourceRefDao()
                .findBySourceItemIds(listOf(deletedOpenListItem.id)).single()
            database.trackSourceRefDao().upsertAll(
                listOf(openListRef.copy(sourceItemId = otherItemId, isAvailable = true, isPreferred = false)),
            )

            val second = buildList {
                repeat(100) { add(entry(accountId, it, "v1", rawRoot)) }
                repeat(10) { add(entry(accountId, 100 + it, "v2", rawRoot) ) }
                repeat(5) { add(entry(accountId, 114 + it, "v1", rawRoot)) }
            }
            reader.requestedEntries = 0
            reader.requestedPaths.clear()
            val secondResult = coordinator.importCompleteSnapshot(
                request(accountId, second, "openlist-115-second", rawRoot),
            )
            assertEquals(100L, secondResult.unchangedCount)
            assertEquals(5L, secondResult.addedCount)
            assertEquals(10L, secondResult.modifiedCount)
            assertEquals(4L, secondResult.deletedCount)
            assertEquals(15, reader.requestedEntries)
            assertTrue(reader.requestedPaths.all { it.startsWith("$rawRoot/") })
            assertEquals(115L, database.sourceItemDao().countLiveTracksForSourceAccount(accountId))
            val deletedItems = database.sourceItemDao().findByProviderItemIds(
                accountId,
                (110..113).map { "$rawRoot/track-$it.flac" },
            )
            assertEquals(4, deletedItems.count { it.isDeleted })
            assertTrue(deletedItems.all { item ->
                database.trackSourceRefDao().findBySourceItemIds(listOf(item.id)).all { !it.isAvailable }
            })
            assertTrue(
                database.trackSourceRefDao().findByTrackId(sharedTrackId)
                    .single { it.sourceItemId == otherItemId }
                    .isAvailable,
            )
            assertFalse(secondResult.syncMode.contains("IncrementalSync"))
        } finally {
            bridge.destroy()
            database.close()
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }

    private fun request(
        accountId: Long,
        entries: List<StorageEntry>,
        scanId: String,
        root: String,
    ) =
        RemoteLibraryImportRequest(
            storageId = accountId,
            selectedFolderRemoteId = root,
            selectedFolderCanonicalPath = root,
            selectedFolderDisplayPath = root,
            entries = entries,
            scanId = scanId,
            metadataScanMode = MetadataScanMode.Full,
            metadataConcurrency = 4u,
            importBatchSize = 50,
            scanRules = LibrarySyncScanRules(minDurationMs = 0L),
        )

    private fun entry(accountId: Long, index: Int, etag: String, root: String) = StorageEntry(
        storageId = uniffi.app_backend.StorageId(accountId),
        name = "track-$index.flac",
        path = "$root/track-$index.flac",
        size = 100u,
        isDir = false,
        remoteId = "$root/track-$index.flac",
        parentRemoteId = root,
        mimeType = "audio/flac",
        etag = etag,
        ctag = null,
        createdAt = 1,
        modifiedAt = 1,
    )
}

private class CountingMetadataReader : RemoteMetadataReader {
    var requestedEntries = 0
    val requestedPaths = mutableListOf<String>()

    override suspend fun read(entry: StorageEntry, options: MetadataScanOptions): RemoteMetadata = metadata(entry.name)

    override suspend fun readBatch(
        entries: List<StorageEntry>,
        concurrency: UInt,
        options: MetadataScanOptions,
    ): List<RemoteMetadataResult> = entries.mapIndexed { index, entry -> result(index, entry) }

    override suspend fun readBatchForAccount(
        accountId: io.github.julystar.musicapp.core.domain.model.SourceAccountId,
        entries: List<StorageEntry>,
        concurrency: UInt,
        options: MetadataScanOptions,
    ): List<RemoteMetadataResult> {
        requestedEntries += entries.size
        requestedPaths += entries.map { it.path }
        return entries.mapIndexed { index, entry -> result(index, entry) }
    }

    private fun result(index: Int, entry: StorageEntry) = RemoteMetadataResult(
        requestIndex = index.toULong(),
        entry = StorageEntryLoc(entry.storageId, entry.path),
        metadata = metadata(entry.name),
        error = null,
    )

    private fun metadata(title: String) = RemoteMetadata(
        title = title,
        artist = "OpenList Artist",
        artists = listOf("OpenList Artist"),
        albumArtist = "OpenList Artist",
        album = "OpenList Album",
        composer = null,
        lyricist = null,
        conductor = null,
        genre = "Other",
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
        lyrics = RemoteEmbeddedLyrics("", false, null, null),
        embeddedLyricsKind = "Plain",
        artwork = null,
        hasEmbeddedArtwork = false,
        rawMetadata = listOf(RemoteRawMetadataEntry("title", title, null, null)),
        durationMs = 1u,
        sampleRate = null,
        bitDepth = null,
        channels = null,
        channelLayout = null,
        overallBitrate = null,
        audioBitrate = null,
        codec = "FLAC",
        container = "flac",
        lossless = true,
        metadataRequestCount = 1u,
        metadataFetchedBytes = 1u,
        metadataElapsedMs = 1u,
        artworkCachedBytes = 0u,
    )
}

private class MemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()
    override suspend fun load(storageId: Long) = values[storageId]
    override suspend fun save(storageId: Long, credential: StoredCredential) { values[storageId] = credential }
    override suspend fun delete(storageId: Long) { values.remove(storageId) }
    override suspend fun clear() { values.clear() }
}
