package io.github.julystar.musicapp.source.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.SourceSearchFailureReason
import io.github.julystar.musicapp.source.api.SourceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RoomLegacyStorageSearchProviderIntegrationTest {
    @Test
    fun searchesIndexedTracksForExpectedStorageOnly() = withDatabase { database ->
        val webDavFolderId = seedStorageAndFolder(database, storageId = 1, type = StorageType.WEBDAV)
        val oneDriveFolderId = seedStorageAndFolder(database, storageId = 2, type = StorageType.ONE_DRIVE)
        val smbFolderId = seedStorageAndFolder(database, storageId = 3, type = StorageType.SMB)
        database.trackDao().upsertAll(
            listOf(
                track(id = 1, title = "Moonlight"),
                track(id = 2, title = "Sunrise"),
                track(id = 3, title = "Deleted Moon"),
                track(id = 4, title = "Moon Cloud"),
                track(id = 5, title = "SMB Moon"),
            )
        )
        seedSourceRef(
            database,
            trackId = 1,
            sourceItemId = 101,
            storageId = 1,
            rootId = webDavFolderId,
            path = "/Music/Moon.flac",
        )
        seedSourceRef(
            database,
            trackId = 2,
            sourceItemId = 102,
            storageId = 1,
            rootId = webDavFolderId,
            path = "/Music/Sun.flac",
        )
        seedSourceRef(
            database,
            trackId = 3,
            sourceItemId = 103,
            storageId = 1,
            rootId = webDavFolderId,
            path = "/Music/Deleted Moon.flac",
            isDeleted = true,
            isAvailable = false,
        )
        seedSourceRef(
            database,
            trackId = 4,
            sourceItemId = 201,
            storageId = 2,
            rootId = oneDriveFolderId,
            path = "/Cloud/Moon.flac",
        )
        seedSourceRef(
            database,
            trackId = 5,
            sourceItemId = 301,
            storageId = 3,
            rootId = smbFolderId,
            path = "/NAS/SMB Moon.flac",
        )
        val provider = RoomLegacyStorageSearchProvider(
            storageLookup = { storageId ->
                when (storageId.value) {
                    1L -> storage(storageId.value, StorageType.WEBDAV)
                    2L -> storage(storageId.value, StorageType.ONE_DRIVE)
                    3L -> storage(storageId.value, StorageType.SMB)
                    else -> null
                }
            },
            trackDao = database.trackDao(),
        )

        val result = provider.search(
            accountId = SourceAccountId("storage:1"),
            query = "moon",
            limit = 10,
            expectedStorageKind = LegacyStorageKind.WebDav,
            sourceId = BuiltInSourceIds.WebDav,
        )

        val items = assertIs<SourceSearchResult.Success>(result).items
        assertEquals(listOf("Moonlight"), items.map { it.title })
        val item = items.single()
        assertEquals(SourceAccountId("storage:1"), item.accountId)
        assertEquals(BuiltInSourceIds.WebDav, item.mediaId.sourceId)
        assertEquals(MediaType.Track, item.mediaId.mediaType)
        assertEquals("/Music/Moon.flac", item.path)
        assertEquals("Luna", item.artist)
        assertEquals(180_000, item.durationMs)

        val smbResult = provider.search(
            accountId = SourceAccountId("storage:3"),
            query = "moon",
            limit = 10,
            expectedStorageKind = LegacyStorageKind.Smb,
            sourceId = BuiltInSourceIds.Smb,
        )
        val smbItem = assertIs<SourceSearchResult.Success>(smbResult).items.single()
        assertEquals("SMB Moon", smbItem.title)
        assertEquals(BuiltInSourceIds.Smb, smbItem.mediaId.sourceId)
        assertEquals("/NAS/SMB Moon.flac", smbItem.path)
    }

    @Test
    fun rejectsAccountsWithUnexpectedStorageType() = withDatabase { database ->
        val provider = RoomLegacyStorageSearchProvider(
            storageLookup = { storage(1, StorageType.LOCAL) },
            trackDao = database.trackDao(),
        )

        assertEquals(
            SourceSearchResult.Failure(SourceSearchFailureReason.UnsupportedAccount),
            provider.search(
                accountId = SourceAccountId("storage:1"),
                query = "moon",
                limit = 10,
                expectedStorageKind = LegacyStorageKind.WebDav,
                sourceId = BuiltInSourceIds.WebDav,
            ),
        )
    }

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private suspend fun seedStorageAndFolder(
        database: AppDatabase,
        storageId: Long,
        type: StorageType,
    ): Long {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = storageId,
                providerType = type.toProviderType(),
                displayName = "Storage $storageId",
                endpoint = "",
                externalAccountId = null,
                credentialRef = "storage-$storageId",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        val path = "/Root-$storageId"
        database.libraryRootDao().upsert(
            LibraryRootEntity(
                id = storageId,
                sourceAccountId = storageId,
                providerRootId = "folder-$storageId",
                canonicalPath = path,
                displayName = path,
                syncStatus = "COMPLETED",
                syncCursor = null,
                lastSyncAt = null,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        return assertNotNull(database.libraryRootDao().findByPath(storageId, path)).id
    }

    private suspend fun seedSourceRef(
        database: AppDatabase,
        trackId: Long,
        sourceItemId: Long,
        storageId: Long,
        rootId: Long,
        path: String,
        isDeleted: Boolean = false,
        isAvailable: Boolean = !isDeleted,
    ) {
        database.sourceItemDao().upsertAll(
            listOf(sourceItem(sourceItemId, storageId, rootId, path, isDeleted)),
        )
        database.trackSourceRefDao().upsertAll(
            listOf(trackSourceRef(trackId, sourceItemId, isAvailable)),
        )
    }

    private fun sourceItem(
        id: Long,
        storageId: Long,
        rootId: Long,
        path: String,
        isDeleted: Boolean,
    ) = SourceItemEntity(
        id = id,
        sourceAccountId = storageId,
        libraryRootId = rootId,
        itemType = SourceItemTypes.Track,
        providerItemId = "item-$id",
        parentProviderItemId = "folder-$storageId",
        canonicalPath = path,
        displayPath = path,
        displayName = path.substringAfterLast('/'),
        mimeType = "audio/flac",
        sizeBytes = 1_000,
        etag = "\"etag-$id\"",
        revision = null,
        createdAtRemote = 1,
        modifiedAtRemote = 1,
        contentHash = null,
        audioFingerprint = null,
        isDeleted = isDeleted,
        firstSyncedAt = 1,
        lastSyncedAt = 1,
        lastSeenScanId = "scan-1",
    )

    private fun track(
        id: Long,
        title: String,
    ) = TrackEntity(
        id = id,
        title = title,
        sortTitle = null,
        albumId = null,
        albumArtist = null,
        composer = null,
        comment = null,
        grouping = null,
        durationMs = 180_000,
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

    private fun trackSourceRef(
        trackId: Long,
        sourceItemId: Long,
        isAvailable: Boolean,
    ) = TrackSourceRefEntity(
        trackId = trackId,
        sourceItemId = sourceItemId,
        role = "primary",
        matchMethod = "test",
        matchConfidence = 100,
        isPreferred = true,
        isAvailable = isAvailable,
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

    private fun StorageType.toProviderType(): String {
        return when (this) {
            StorageType.LOCAL -> ProviderTypes.Local
            StorageType.WEBDAV -> ProviderTypes.WebDav
            StorageType.ONE_DRIVE -> ProviderTypes.OneDrive
            StorageType.SMB -> ProviderTypes.Smb
            StorageType.OPEN_LIST -> ProviderTypes.OpenList
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
        musicCount = 0u,
    )
}
