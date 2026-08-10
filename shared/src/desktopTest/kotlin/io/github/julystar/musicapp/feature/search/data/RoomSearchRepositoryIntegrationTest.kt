package io.github.julystar.musicapp.feature.search.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomSearchRepositoryIntegrationTest {
    @Test
    fun searchLocalLibraryMatchesTrackFieldsAndSkipsDeletedRemoteFiles() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        database.trackDao().upsertAll(
            listOf(
                track(id = 1, title = "Moon", artist = "Luna"),
                track(id = 2, title = "Moonlight Sonata", composer = "Beethoven"),
                track(id = 3, title = "Deleted Moon", artist = "Luna"),
            ),
        )
        seedSourceRef(database, trackId = 1, rootId = folderId, path = "/Music/moon.flac")
        seedSourceRef(database, trackId = 2, rootId = folderId, path = "/Music/moonlight.flac")
        seedSourceRef(
            database,
            trackId = 3,
            rootId = folderId,
            path = "/Music/deleted-moon.flac",
            isDeleted = true,
            isAvailable = false,
        )

        val results = repository(database).searchLocalLibrary("moon")

        assertEquals(listOf(1L, 2L), results.tracks.map { it.id })
        assertEquals(listOf("Moon", "Moonlight Sonata"), results.tracks.map { it.title })
        assertEquals(listOf("Luna", "Beethoven"), results.tracks.map { it.artist })
        assertEquals(
            legacyStorageTrackMediaId(
                sourceId = BuiltInSourceIds.WebDav,
                accountId = SourceAccountId("storage:1"),
                path = "/Music/moon.flac",
            ),
            results.tracks.first().mediaId,
        )
    }

    @Test
    fun searchLocalLibraryTreatsSqlWildcardCharactersLiterally() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        database.trackDao().upsertAll(
            listOf(
                track(id = 1, title = "100% Real"),
                track(id = 2, title = "100x Real"),
                track(id = 3, title = "100_ Real"),
            ),
        )
        seedSourceRef(database, trackId = 1, rootId = folderId, path = "/Music/percent.flac")
        seedSourceRef(database, trackId = 2, rootId = folderId, path = "/Music/x.flac")
        seedSourceRef(database, trackId = 3, rootId = folderId, path = "/Music/underscore.flac")

        val results = repository(database).searchLocalLibrary("100%")

        assertEquals(listOf(1L), results.tracks.map { it.id })
    }

    @Test
    fun searchLocalLibraryBuildsPlayableSmbMediaId() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database, providerType = ProviderTypes.Smb)
        database.trackDao().upsertAll(
            listOf(track(id = 1, title = "SMB Moon")),
        )
        seedSourceRef(database, trackId = 1, rootId = folderId, path = "/音乐/SMB Moon.flac")

        val result = repository(database).searchLocalLibrary("SMB Moon").tracks.single()

        assertEquals(
            legacyStorageTrackMediaId(
                sourceId = BuiltInSourceIds.Smb,
                accountId = SourceAccountId("storage:1"),
                path = "/音乐/SMB Moon.flac",
            ),
            result.mediaId,
        )
    }

    @Test
    fun suggestLocalLibraryUsesTrackFieldsAndSkipsDeletedRemoteFiles() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        database.trackDao().upsertAll(
            listOf(
                track(id = 1, title = "Moon", artist = "Luna"),
                track(id = 2, title = "Moonlight Sonata", composer = "Momo"),
                track(id = 3, title = "Moon Deleted"),
                track(id = 4, title = "Sun", artist = "Moon"),
            ),
        )
        seedSourceRef(database, trackId = 1, rootId = folderId, path = "/Music/moon.flac")
        seedSourceRef(database, trackId = 2, rootId = folderId, path = "/Music/moonlight.flac")
        seedSourceRef(
            database,
            trackId = 3,
            rootId = folderId,
            path = "/Music/deleted-moon.flac",
            isDeleted = true,
            isAvailable = false,
        )
        seedSourceRef(database, trackId = 4, rootId = folderId, path = "/Music/sun.flac")

        val suggestions = repository(database).suggestLocalLibrary("mo", limit = 10)

        assertEquals(listOf("Moon", "Moonlight Sonata", "Momo"), suggestions)
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

    private fun repository(database: AppDatabase): RoomSearchRepository {
        return RoomSearchRepository(
            trackDao = database.trackDao(),
            trackFtsDao = database.trackFtsDao(),
            trackSourceRefDao = database.trackSourceRefDao(),
            metadataDao = database.metadataDao(),
        )
    }

    private suspend fun seedStorageAndFolder(
        database: AppDatabase,
        providerType: String = ProviderTypes.WebDav,
    ): Long {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = 1,
                providerType = providerType,
                displayName = "Test",
                endpoint = "https://example.invalid/dav",
                externalAccountId = null,
                credentialRef = "test-credential",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        database.libraryRootDao().upsert(
            LibraryRootEntity(
                id = 1,
                sourceAccountId = 1,
                providerRootId = "folder-1",
                canonicalPath = "/Music",
                displayName = "/Music",
                syncStatus = "RUNNING",
                syncCursor = null,
                lastSyncAt = null,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        return requireNotNull(database.libraryRootDao().findByPath(1, "/Music")).id
    }

    private suspend fun seedSourceRef(
        database: AppDatabase,
        trackId: Long,
        rootId: Long,
        path: String,
        isDeleted: Boolean = false,
        isAvailable: Boolean = !isDeleted,
    ) {
        database.sourceItemDao().upsertAll(
            listOf(sourceItem(id = trackId, rootId = rootId, path = path, isDeleted = isDeleted)),
        )
        database.trackSourceRefDao().upsertAll(
            listOf(trackSourceRef(trackId = trackId, sourceItemId = trackId, isAvailable = isAvailable)),
        )
    }

    private fun sourceItem(
        id: Long,
        rootId: Long,
        path: String,
        isDeleted: Boolean,
    ) = SourceItemEntity(
        id = id,
        sourceAccountId = 1,
        libraryRootId = rootId,
        itemType = SourceItemTypes.Track,
        providerItemId = "item-$id",
        parentProviderItemId = "folder-1",
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
        artist: String? = null,
        composer: String? = null,
    ) = TrackEntity(
        id = id,
        title = title,
        sortTitle = null,
        albumId = null,
        albumArtist = null,
        composer = composer,
        comment = null,
        grouping = null,
        durationMs = 180_000,
        discNumber = 1,
        discTotal = 1,
        trackNumber = null,
        trackTotal = null,
        year = 2026,
        date = "2026",
        sampleRate = 48_000,
        bitRate = 900,
        bitsPerSample = 24,
        channels = 2,
        channelLayout = null,
        codec = "FLAC",
        container = "FLAC",
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
        artist = artist,
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
        codec = "FLAC",
        container = "FLAC",
        bitRate = 900,
        sampleRate = 48_000,
        bitsPerSample = 24,
        channels = 2,
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
    )
}
