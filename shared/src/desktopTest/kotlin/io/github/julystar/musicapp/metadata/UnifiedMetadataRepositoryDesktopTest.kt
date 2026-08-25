package io.github.julystar.musicapp.metadata

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedMetadataRepositoryDesktopTest {
    @Test
    fun filenameFallbackPersistsParsedAlbumThroughUnifiedGraph() = withDatabase { database ->
        database.trackDao().upsertAll(
            listOf(track(id = 1, title = "04 - Song", metadataSource = TrackMetadataSources.Filename)),
        )

        val updated = requireNotNull(
            repository(database).replaceFilenameMetadata(
                trackId = 1,
                filename = "Artist - Album - 04 - Song.flac",
            ),
        )

        assertEquals("Artist", updated.artist)
        assertEquals(4, updated.trackNumber)
        assertEquals("Album", database.metadataDao().getAlbum(requireNotNull(updated.albumId))?.name)
    }

    @Test
    fun highPluginMatchReplacesFilenameMetadataAndWritesGraph() = withDatabase { database ->
        database.trackDao().upsertAll(
            listOf(track(id = 1, title = "01 - Song", metadataSource = TrackMetadataSources.Filename)),
        )
        val repository = repository(database)
        val result = repository.applyAutomaticPluginMatch(
            trackId = 1,
            match = highMatch(
                MetaSongCandidate(
                    id = "song-1",
                    title = "Song",
                    artist = "Artist",
                    album = "Album",
                    date = "2024",
                    sourceId = "plugin",
                    fields = mapOf("genre" to "Rock; Pop", "isrc" to "ISRC-1"),
                ),
            ),
        )

        val updated = requireNotNull(result.track)
        assertEquals("Song", updated.title)
        assertEquals("Artist", updated.artist)
        assertEquals(TrackMetadataSources.Plugin, updated.metadataSource)
        assertEquals("plugin", updated.metadataSourceId)
        assertEquals("song-1", updated.metadataExternalId)
        assertTrue(result.identityChanged)
        assertEquals(listOf("Artist"), database.metadataDao().artistNamesForTrack(1))
        assertEquals(listOf("Pop", "Rock"), database.metadataDao().genreNamesForTrack(1))
        assertEquals("Album", database.metadataDao().getAlbum(requireNotNull(updated.albumId))?.name)
    }

    @Test
    fun pluginOnlyFillsMissingFieldsForFileMetadata() = withDatabase { database ->
        val albumId = insertAlbum(database, "Original Album")
        database.trackDao().upsertAll(
            listOf(
                track(
                    id = 1,
                    title = "Original",
                    artist = "Original Artist",
                    albumId = albumId,
                    metadataSource = TrackMetadataSources.File,
                ),
            ),
        )
        MetadataGraphWriter(database.metadataDao()).replaceTrackArtists(1, listOf("Original Artist"))

        val updated = requireNotNull(
            repository(database).applyAutomaticPluginMatch(
                1,
                highMatch(
                    MetaSongCandidate(
                        id = "other",
                        title = "Replacement",
                        artist = "Other Artist",
                        album = "Other Album",
                        date = "2020",
                        sourceId = "plugin",
                    ),
                ),
            ).track,
        )

        assertEquals("Original", updated.title)
        assertEquals("Original Artist", updated.artist)
        assertEquals(albumId, updated.albumId)
        assertEquals("2020", updated.date)
        assertEquals(TrackMetadataSources.File, updated.metadataSource)
        assertNull(updated.metadataSourceId)
        assertNull(updated.metadataExternalId)
        assertEquals(listOf("Original Artist"), database.metadataDao().artistNamesForTrack(1))
    }

    @Test
    fun lockedMetadataIsExactNoOpWithoutGraphSideEffects() = withDatabase { database ->
        val original = track(
            id = 1,
            title = "Locked",
            artist = "User Artist",
            metadataSource = TrackMetadataSources.Plugin,
            metadataLocked = true,
        )
        database.trackDao().upsertAll(listOf(original))

        val result = repository(database).applyAutomaticPluginMatch(
            1,
            highMatch(
                MetaSongCandidate(
                    id = "new",
                    title = "Changed",
                    artist = "Changed Artist",
                    album = "Must Not Exist",
                    sourceId = "plugin",
                ),
            ),
        )

        assertEquals(original, result.track)
        assertTrue(result.changedFields.isEmpty())
        assertTrue(
            database.metadataDao().findAlbumsByNormalizedNames(listOf("must not exist")).isEmpty(),
        )
    }

    @Test
    fun sourceMetadataFillsTechnicalFieldsAndManualOverrideLocksAtomically() =
        withDatabase { database ->
            database.trackDao().upsertAll(
                listOf(track(id = 1, title = "Song", metadataSource = TrackMetadataSources.Filename)),
            )
            val repository = repository(database)
            val sourceResult = repository.apply(
                trackId = 1,
                metadata = ResolvedTrackMetadata(
                    title = "Different",
                    durationMs = 180_000,
                    sampleRate = 96_000,
                    bitsPerSample = 24,
                    codec = "FLAC",
                    lossless = true,
                ),
                policy = MetadataMergePolicy.FillMissingFileMetadata,
            )
            val sourceTrack = requireNotNull(sourceResult.track)
            assertEquals("Different", sourceTrack.title)
            assertEquals(180_000, sourceTrack.durationMs)
            assertEquals(96_000, sourceTrack.sampleRate)
            assertEquals(24, sourceTrack.bitsPerSample)
            assertEquals(TrackMetadataSources.File, sourceTrack.metadataSource)

            val manual = requireNotNull(
                repository.manualOverride(
                    1,
                    MetaSongCandidate("manual", "Manual Title", "Manual Artist", sourceId = "plugin"),
                ),
            )
            assertEquals("Manual Title", manual.title)
            assertTrue(manual.metadataLocked)
            assertEquals(TrackMetadataSources.Plugin, manual.metadataSource)
            assertFalse(manual.durationMs == null)
        }

    @Test
    fun technicalOnlySourceRefreshKeepsFilenameProvenanceForLaterPluginReplacement() =
        withDatabase { database ->
            database.trackDao().upsertAll(
                listOf(track(id = 1, title = "Artist - Song", metadataSource = TrackMetadataSources.Filename)),
            )
            val repository = repository(database)

            val sourceTrack = requireNotNull(
                repository.apply(
                    trackId = 1,
                    metadata = ResolvedTrackMetadata(durationMs = 180_000, codec = "FLAC"),
                    policy = MetadataMergePolicy.FillMissingFileMetadata,
                ).track,
            )

            assertEquals("Artist - Song", sourceTrack.title)
            assertEquals(180_000, sourceTrack.durationMs)
            assertEquals("FLAC", sourceTrack.codec)
            assertEquals(TrackMetadataSources.Filename, sourceTrack.metadataSource)

            val pluginTrack = requireNotNull(
                repository.applyAutomaticPluginMatch(
                    1,
                    highMatch(
                        MetaSongCandidate(
                            id = "song-1",
                            title = "Song",
                            artist = "Artist",
                            album = "Album",
                            sourceId = "plugin",
                        ),
                    ),
                ).track,
            )
            assertEquals("Song", pluginTrack.title)
            assertEquals("Artist", pluginTrack.artist)
            assertEquals(TrackMetadataSources.Plugin, pluginTrack.metadataSource)
        }

    @Test
    fun sourceMetadataWithoutTaggedTitleFillsFieldsButKeepsFilenameProvenance() =
        withDatabase { database ->
            database.trackDao().upsertAll(
                listOf(track(id = 1, title = "Artist - Song", metadataSource = TrackMetadataSources.Filename)),
            )

            val updated = requireNotNull(
                repository(database).apply(
                    trackId = 1,
                    metadata = ResolvedTrackMetadata(artist = "Tagged Artist", genre = "Rock"),
                    policy = MetadataMergePolicy.FillMissingServerMetadata,
                ).track,
            )

            assertEquals("Artist - Song", updated.title)
            assertEquals("Tagged Artist", updated.artist)
            assertEquals(TrackMetadataSources.Filename, updated.metadataSource)
            assertEquals(listOf("Rock"), database.metadataDao().genreNamesForTrack(1))
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

    private fun repository(database: AppDatabase) = UnifiedMetadataRepository(
        database = database,
        trackDao = database.trackDao(),
        metadataDao = database.metadataDao(),
    )

    private suspend fun insertAlbum(database: AppDatabase, name: String): Long {
        val normalized = name.lowercase()
        database.metadataDao().insertAlbums(
            listOf(AlbumEntity(name = name, normalizedName = normalized, sortName = null, year = null, artworkId = null)),
        )
        return database.metadataDao().findAlbumsByNormalizedNames(listOf(normalized)).single().id
    }

    private fun highMatch(candidate: MetaSongCandidate) = TrackCandidateMatch(
        candidate = candidate,
        confidence = TrackCandidateMatchConfidence.HIGH,
        score = 100,
    )

    private fun track(
        id: Long,
        title: String,
        artist: String? = null,
        albumId: Long? = null,
        metadataSource: String,
        metadataLocked: Boolean = false,
    ) = TrackEntity(
        id = id,
        title = title,
        sortTitle = null,
        albumId = albumId,
        albumArtist = null,
        composer = null,
        comment = null,
        grouping = null,
        durationMs = null,
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
        artist = artist,
        metadataSource = metadataSource,
        metadataLocked = metadataLocked,
    )
}
