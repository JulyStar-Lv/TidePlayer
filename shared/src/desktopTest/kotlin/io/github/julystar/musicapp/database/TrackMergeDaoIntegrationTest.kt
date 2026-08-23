package io.github.julystar.musicapp.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.execSQL
import androidx.room.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.metadata.RoomTrackIdentityReconciler
import io.github.julystar.musicapp.metadata.TrackIdentityChangeReason
import io.github.julystar.musicapp.metadata.TrackIdentityResult
import okio.Path.Companion.toPath
import java.io.File

class TrackMergeDaoIntegrationTest {
    @Test
    fun selectingPlaybackSourceKeepsExactlyOnePreferredRef() = withDatabase { database ->
        seedSource(database, accountId = 10, rootId = 100, itemId = 1)
        seedSource(database, accountId = 20, rootId = 200, itemId = 2)
        database.trackDao().upsertAll(listOf(track(1, lastPlayedAt = null)))
        database.trackSourceRefDao().upsertAll(
            listOf(sourceRef(1, 1), sourceRef(1, 2))
        )

        assertEquals(
            true,
            database.trackSourceRefDao().selectPreferredSource(
                trackId = 1,
                sourceItemId = 2,
                now = 20,
            ),
        )

        val refs = database.trackSourceRefDao().findByTrackId(1)
        assertEquals(listOf(2L), refs.filter { it.isPreferred }.map { it.sourceItemId })
    }

    @Test
    fun mergeMovesLibraryAndUserReferencesToSurvivingTrack() = withDatabase { database ->
        seedSource(database, accountId = 10, rootId = 100, itemId = 1)
        seedSource(database, accountId = 20, rootId = 200, itemId = 2)
        seedSource(database, accountId = 30, rootId = 300, itemId = 3)
        database.trackDao().upsertAll(
            listOf(track(1, lastPlayedAt = 10), track(2, lastPlayedAt = 20), track(3, lastPlayedAt = 30))
        )
        database.trackSourceRefDao().upsertAll(
            listOf(
                sourceRef(1, 1).copy(isPreferred = false),
                sourceRef(2, 2).copy(isPreferred = true, sampleRate = 96_000, bitsPerSample = 24),
                sourceRef(3, 3).copy(isPreferred = false),
            )
        )
        database.playlistDao().upsert(
            PlaylistEntity(
                id = 7,
                title = "Playlist",
                artworkId = null,
                createdAt = 1,
                updatedAt = 1,
                sortOrder = 0,
            )
        )
        database.playlistDao().upsertTracks(
            listOf(
                PlaylistTrackCrossRef(7, 1, sortOrder = 2, addedAt = 2),
                PlaylistTrackCrossRef(7, 2, sortOrder = 1, addedAt = 1),
                PlaylistTrackCrossRef(7, 3, sortOrder = 3, addedAt = 3),
            )
        )
        database.metadataDao().upsertLyrics(
            listOf(
                LyricsEntity(
                    trackId = 1,
                    format = "TEXT",
                    language = null,
                    synchronized = false,
                    content = "plain lyrics",
                    sourcePath = null,
                    updatedAt = 10,
                    sourceKind = "Plugin",
                ),
                LyricsEntity(
                    trackId = 2,
                    format = "TTML",
                    language = null,
                    synchronized = true,
                    content = "<tt><p begin=\"0s\">timed lyrics</p></tt>",
                    sourcePath = null,
                    updatedAt = 2,
                    sourceKind = "Plugin",
                )
            )
        )
        database.metadataDao().upsertRawMetadata(
            listOf(RawMetadataEntity(trackId = 2, tagKey = "tag", value = "value", locale = null, description = null))
        )
        database.metadataDao().upsertArtwork(
            listOf(
                ArtworkEntity(
                    trackId = 1,
                    albumId = null,
                    contentHash = "artwork-1",
                    localPath = "/artwork-1.jpg",
                    thumbnailPath = null,
                    width = 1_000,
                    height = 1_000,
                    mimeType = "image/jpeg",
                    pictureType = "CoverFront",
                ),
                ArtworkEntity(
                    trackId = 2,
                    albumId = null,
                    contentHash = "artwork-2",
                    localPath = "/artwork-2.jpg",
                    thumbnailPath = null,
                    width = 300,
                    height = 300,
                    mimeType = "image/jpeg",
                    pictureType = "CoverFront",
                )
            )
        )
        repeat(8) { index ->
            database.listeningStatisticsDao().insertHistory(
                ListeningHistoryEntity(
                trackId = if (index < 3) 1 else 2,
                title = "Song",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000,
                listenedMs = 60_000,
                playedAtEpochMs = index.toLong(),
            )
            )
        }

        assertEquals(true, database.trackMergeDao().mergeTracks(
            targetTrackId = 1,
            sourceTrackIds = listOf(2, 3),
            matchMethod = "strict_metadata",
            matchConfidence = 80,
            lastPlayedAt = 30,
            now = 40,
        ))

        assertNull(database.trackDao().get(2))
        assertNull(database.trackDao().get(3))
        val mergedTrack = assertNotNull(database.trackDao().get(1))
        assertEquals(30L, mergedTrack.lastPlayedAt)
        assertEquals(96_000, mergedTrack.sampleRate)
        assertEquals(24, mergedTrack.bitsPerSample)
        val sourceRefs = database.trackSourceRefDao().findByTrackId(1)
        assertEquals(setOf(1L, 2L, 3L), sourceRefs.mapTo(mutableSetOf()) { it.sourceItemId })
        assertEquals(listOf(2L), sourceRefs.filter { it.isPreferred }.map { it.sourceItemId })
        assertEquals("alternate", sourceRefs.single { it.sourceItemId == 2L }.role)
        val playlistTracks = database.playlistDao().observeTracks(7).first()
        assertEquals(listOf(1L), playlistTracks.map { it.trackId })
        assertEquals(1L, playlistTracks.single().sortOrder)
        assertEquals("TTML", database.metadataDao().getLyricsCandidates(1).single().format)
        assertEquals(1, database.metadataDao().rawMetadataForTrack(1).size)
        assertEquals(2, database.trackMergeDao().listArtwork(1).size)
        assertEquals("artwork-1", database.metadataDao().getArtworkForTrack(1)?.contentHash)
        val history = database.listeningStatisticsDao().observeHistory().first()
        assertEquals(8, history.size)
        assertEquals(setOf(1L), history.mapTo(mutableSetOf(), ListeningHistoryEntity::trackId))
        assertEquals(listOf(1L), database.trackFtsDao().searchFts("Song", 10).map(TrackEntity::id))
    }

    @Test
    fun mergeConsolidatesLockedMetadataAndFillsMissingFields() = withDatabase { database ->
        seedSource(database, 10, 100, 1)
        seedSource(database, 20, 200, 2)
        database.trackDao().upsertAll(listOf(
            track(1, null).copy(
                title = "Manual title",
                composer = "Manual composer",
                comment = null,
                metadataSource = TrackMetadataSources.Plugin,
                metadataLocked = true,
                metadataSourceId = "manual-plugin",
                metadataExternalId = "manual-id",
            ),
            track(2, null).copy(
                title = "File title",
                composer = "File composer",
                comment = "filled comment",
                metadataSource = TrackMetadataSources.File,
            ),
        ))
        database.trackSourceRefDao().upsertAll(listOf(
            sourceRef(1, 1).copy(isPreferred = false),
            sourceRef(2, 2).copy(isPreferred = true, codec = "AAC", sampleRate = 48_000, lossless = false),
        ))

        assertEquals(true, database.trackMergeDao().mergeTracks(1, listOf(2), "content_hash", 100, null, 10))

        val merged = assertNotNull(database.trackDao().get(1))
        assertEquals("Manual title", merged.title)
        assertEquals("Manual composer", merged.composer)
        assertEquals("filled comment", merged.comment)
        assertEquals(true, merged.metadataLocked)
        assertEquals("manual-id", merged.metadataExternalId)
        assertEquals("AAC", merged.codec)
        assertEquals(48_000, merged.sampleRate)
    }

    @Test
    fun mergeTransactionAbortsWhenEvidenceOrReleaseChanged() = withDatabase { database ->
        seedSource(database, 10, 100, 1, contentHash = "hash-a")
        seedSource(database, 20, 200, 2, contentHash = "hash-b")
        database.trackDao().upsertAll(listOf(track(1, null), track(2, null)))
        database.trackSourceRefDao().upsertAll(listOf(sourceRef(1, 1), sourceRef(2, 2)))

        assertEquals(false, database.trackMergeDao().mergeTracks(1, listOf(2), "stale_hash", 100, null, 10))
        assertNotNull(database.trackDao().get(1))
        assertNotNull(database.trackDao().get(2))

        val albumIds = database.metadataDao().upsertAlbums(listOf(
            AlbumEntity(name = "Release A", normalizedName = "release a", sortName = null, year = null, artworkId = null),
            AlbumEntity(name = "Release B", normalizedName = "release b", sortName = null, year = null, artworkId = null),
        ))
        database.trackDao().upsertAll(listOf(
            track(1, null).copy(albumId = albumIds[0], musicBrainzRecordingId = "recording"),
            track(2, null).copy(albumId = albumIds[1], musicBrainzRecordingId = "recording"),
        ))
        database.sourceItemDao().upsertAll(listOf(
            assertNotNull(database.sourceItemDao().get(1)).copy(contentHash = "same"),
            assertNotNull(database.sourceItemDao().get(2)).copy(contentHash = "same"),
        ))

        assertEquals(false, database.trackMergeDao().mergeTracks(1, listOf(2), "recording", 98, null, 20))
        assertNotNull(database.trackDao().get(2))
    }

    @Test
    fun incrementalReconcilerReturnsMergedCanonicalTrack() = withDatabase { database ->
        seedSource(database, 10, 100, 1)
        seedSource(database, 20, 200, 2)
        database.trackDao().upsertAll(listOf(track(1, null), track(2, null)))
        database.trackSourceRefDao().upsertAll(listOf(sourceRef(1, 1), sourceRef(2, 2)))
        val file = File.createTempFile("identity-reconciler-", ".preferences_pb").apply { delete() }
        try {
            val preferences = AppPreferencesRepository(createAppDataStore { file.absolutePath.toPath() })
            preferences.toggleFavoriteTrack(2)
            val result = RoomTrackIdentityReconciler(database, preferences).reconcile(
                changedTrackId = 2,
                reason = TrackIdentityChangeReason.MetadataChanged,
            )

            val merged = result as TrackIdentityResult.Merged
            assertEquals(2L, merged.canonicalTrackId)
            assertEquals(listOf(1L), merged.mergedTrackIds)
            assertNull(database.trackDao().get(1))
            assertNotNull(database.trackDao().get(2))
            assertEquals(setOf(2L), preferences.favoriteTrackIds.first())
        } finally {
            file.delete()
        }
    }

    @Test
    fun sourceRefPrimaryKeyCollisionIsResolvedWithoutDeletingSourceItem() = withDatabase { database ->
        seedSource(database, 10, 100, 1)
        database.trackDao().upsertAll(listOf(track(1, null), track(2, null)))
        database.trackSourceRefDao().upsertAll(listOf(sourceRef(1, 1)))
        database.useWriterConnection { connection ->
            connection.execSQL("DROP INDEX index_track_source_ref_sourceItemId")
        }
        database.trackSourceRefDao().upsertAll(listOf(sourceRef(2, 1)))

        assertEquals(true, database.trackMergeDao().mergeTracks(1, listOf(2), "content_hash", 100, null, 10))

        assertEquals(listOf(1L), database.trackSourceRefDao().findByTrackId(1).map { it.sourceItemId })
        assertNotNull(database.sourceItemDao().get(1))
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

    private suspend fun seedSource(
        database: AppDatabase,
        accountId: Long,
        rootId: Long,
        itemId: Long,
        contentHash: String = "same-track-content",
    ) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = accountId,
                providerType = ProviderTypes.WebDav,
                displayName = "Source $accountId",
                endpoint = "https://example.invalid/$accountId",
                externalAccountId = null,
                credentialRef = "credential-$accountId",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.libraryRootDao().upsert(
            LibraryRootEntity(
                id = rootId,
                sourceAccountId = accountId,
                providerRootId = "root-$rootId",
                canonicalPath = "/Music",
                displayName = "Music",
                syncStatus = "SYNCED",
                syncCursor = null,
                lastSyncAt = 1,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.sourceItemDao().upsertAll(
            listOf(
                SourceItemEntity(
                    id = itemId,
                    sourceAccountId = accountId,
                    libraryRootId = rootId,
                    itemType = SourceItemTypes.Track,
                    providerItemId = "item-$itemId",
                    parentProviderItemId = null,
                    canonicalPath = "/Music/song-$itemId.flac",
                    displayPath = "/Music/song-$itemId.flac",
                    displayName = "song-$itemId.flac",
                    mimeType = "audio/flac",
                    sizeBytes = 1_000,
                    etag = "etag-$itemId",
                    revision = null,
                    createdAtRemote = 1,
                    modifiedAtRemote = 1,
                    contentHash = contentHash,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = 1,
                    lastSyncedAt = 1,
                    lastSeenScanId = "scan",
                )
            )
        )
    }

    private fun track(id: Long, lastPlayedAt: Long?) = TrackEntity(
        id = id,
        title = "Song",
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
        codec = "FLAC",
        container = "FLAC",
        lossless = true,
        createdAt = id,
        updatedAt = id,
        lastPlayedAt = lastPlayedAt,
        artist = "Artist",
    )

    private fun sourceRef(trackId: Long, sourceItemId: Long) = TrackSourceRefEntity(
        trackId = trackId,
        sourceItemId = sourceItemId,
        role = "primary",
        matchMethod = "source_identity",
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
        channels = 2,
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
    )
}
