package io.github.julystar.musicapp.database

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.CreatePlaylistRequest
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.domain.importing.OptionalMetadataUpdate
import io.github.julystar.musicapp.domain.importing.updateOptionalMetadata
import io.github.julystar.musicapp.plugin.management.resolveManualMetadataAlbum
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uniffi.app_backend.LyricLoadState
import uniffi.app_backend.RemoteArtwork
import uniffi.app_backend.RemoteEmbeddedLyrics
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.RemoteRawMetadataEntry
import uniffi.app_backend.MusicId
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomLibraryIntegrationTest {
    @Test
    fun migrationNineteenToTwentyAddsEmbeddedLyricsKind() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                "CREATE TABLE track_source_ref (trackId INTEGER NOT NULL PRIMARY KEY)"
            )
            connection.execute("INSERT INTO track_source_ref(trackId) VALUES (1)")

            MIGRATION_19_20.migrate(connection)

            assertTrue("embeddedLyricsKind" in columns(connection, "track_source_ref"))
            connection.prepare(
                "SELECT embeddedLyricsKind FROM track_source_ref WHERE trackId = 1"
            ).use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationEighteenToNineteenAddsEmbeddedArtworkPresence() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                "CREATE TABLE track_source_ref (trackId INTEGER NOT NULL PRIMARY KEY)"
            )
            connection.execute("INSERT INTO track_source_ref(trackId) VALUES (1)")

            MIGRATION_18_19.migrate(connection)

            assertTrue("hasEmbeddedArtwork" in columns(connection, "track_source_ref"))
            connection.prepare(
                "SELECT hasEmbeddedArtwork FROM track_source_ref WHERE trackId = 1"
            ).use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationSeventeenToEighteenCreatesListeningHistory() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            MIGRATION_17_18.migrate(connection)

            val columns = columns(connection, "listening_history")
            assertTrue("id" in columns)
            assertTrue("trackId" in columns)
            assertTrue("listenedMs" in columns)
            assertTrue("playedAtEpochMs" in columns)
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationSixteenToSeventeenAddsProviderConfiguration() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE source_account (
                    id INTEGER NOT NULL PRIMARY KEY,
                    providerType TEXT NOT NULL
                )
                """.trimIndent()
            )
            connection.execute("INSERT INTO source_account VALUES (7, 'smb')")

            MIGRATION_16_17.migrate(connection)

            assertTrue("providerConfig" in columns(connection, "source_account"))
            connection.prepare(
                "SELECT providerConfig FROM source_account WHERE id = 7"
            ).use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.isNull(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationFourteenToFifteenAddsMetadataResetState() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE track (
                    id INTEGER NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL
                )
                """.trimIndent()
            )
            connection.execute("INSERT INTO track VALUES (1, 'Song')")

            MIGRATION_14_15.migrate(connection)

            val columns = columns(connection, "track")
            assertTrue("metadataSource" in columns)
            assertTrue("metadataLocked" in columns)
            assertTrue("metadataSourceId" in columns)
            assertTrue("metadataExternalId" in columns)
            assertTrue("metadataAppliedAt" in columns)
            connection.prepare(
                "SELECT metadataSource, metadataLocked, metadataSourceId FROM track WHERE id = 1"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals(TrackMetadataSources.File, statement.getText(0))
                assertEquals(0, statement.getLong(1))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationThirteenToFourteenAddsWebDavScanPerformanceMetrics() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE import_job (
                    id TEXT NOT NULL PRIMARY KEY,
                    libraryRootId INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
                """.trimIndent()
            )
            connection.execute("INSERT INTO import_job VALUES ('job', 1, 'PAUSED')")

            MIGRATION_13_14.migrate(connection)

            val columns = columns(connection, "import_job")
            assertTrue("syncMode" in columns)
            assertTrue("directoryRequestCount" in columns)
            assertTrue("addedCount" in columns)
            assertTrue("databaseWriteElapsedMs" in columns)
            assertTrue("totalElapsedMs" in columns)
            connection.prepare(
                "SELECT syncMode, directoryConcurrency, totalElapsedMs FROM import_job"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("LEGACY_FULL_SCAN_FALLBACK", statement.getText(0))
                assertEquals(4, statement.getLong(1))
                assertEquals(0, statement.getLong(2))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationElevenToTwelveAddsMetadataScanSnapshotAndStatistics() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE import_job (
                    id TEXT NOT NULL PRIMARY KEY,
                    libraryRootId INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    scannedCount INTEGER NOT NULL,
                    importedCount INTEGER NOT NULL,
                    skippedCount INTEGER NOT NULL,
                    failedCount INTEGER NOT NULL,
                    checkpoint TEXT,
                    errorMessage TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execute(
                "INSERT INTO import_job VALUES ('job', 1, 'PAUSED', 0, 0, 0, 0, NULL, NULL, 1, 1)"
            )

            MIGRATION_11_12.migrate(connection)

            val columns = columns(connection, "import_job")
            assertTrue("metadataScanMode" in columns)
            assertTrue("metadataConcurrency" in columns)
            assertTrue("scanSubdirectories" in columns)
            assertTrue("metadataFetchedBytes" in columns)
            connection.prepare(
                "SELECT metadataScanMode, metadataConcurrency, importBatchSize FROM import_job"
            ).use { statement ->
                assertTrue(statement.step())
                assertEquals("Full", statement.getText(0))
                assertEquals(8, statement.getLong(1))
                assertEquals(200, statement.getLong(2))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationOneToTwoAddsExtendedTrackMetadataColumns() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute("CREATE TABLE track (id INTEGER NOT NULL PRIMARY KEY)")
            MIGRATION_1_2.migrate(connection)

            val columns = buildSet {
                connection.prepare("PRAGMA table_info(track)").use { statement ->
                    while (statement.step()) {
                        add(statement.getText(1))
                    }
                }
            }
            assertTrue("artist" in columns)
            assertTrue("lyricist" in columns)
            assertTrue("musicBrainzRecordingId" in columns)
            assertTrue("replayGainAlbumPeak" in columns)
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationTwoToThreeAddsRoomOnlyPlaybackColumns() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute("CREATE TABLE track (id INTEGER NOT NULL PRIMARY KEY)")
            connection.execute("CREATE TABLE playlist (id INTEGER NOT NULL PRIMARY KEY)")
            MIGRATION_2_3.migrate(connection)

            val trackColumns = columns(connection, "track")
            val playlistColumns = columns(connection, "playlist")
            assertTrue("sourceStorageId" in trackColumns)
            assertTrue("sourcePath" in trackColumns)
            assertTrue("coverStorageId" in playlistColumns)
            assertTrue("coverPath" in playlistColumns)
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationFiveToSixCreatesFtsTableWithRoomExpectedContentOption() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE track (
                    id INTEGER NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT,
                    albumArtist TEXT,
                    composer TEXT
                )
                """.trimIndent()
            )
            MIGRATION_5_6.migrate(connection)

            val createSql = connection.prepare(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'track_fts'"
            ).use { statement ->
                assertTrue(statement.step())
                statement.getText(0)
            }

            assertTrue("content=`track`" in createSql)
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationSixToSevenMovesLegacySourceTablesToUnifiedSourceSchema() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createLegacyV6SourceSchema(connection)
            MIGRATION_6_7.migrate(connection)

            val trackColumns = columns(connection, "track")
            assertTrue("remoteFileId" !in trackColumns)
            assertTrue("sourceStorageId" !in trackColumns)
            assertTrue("sourcePath" !in trackColumns)

            val tableNames = tableNames(connection)
            assertTrue("storage" !in tableNames)
            assertTrue("selected_folder" !in tableNames)
            assertTrue("remote_file" !in tableNames)
            assertTrue("sync_cursor" !in tableNames)

            assertEquals("webdav", singleText(connection, "SELECT providerType FROM source_account WHERE id = 1"))
            assertEquals("/Music", singleText(connection, "SELECT canonicalPath FROM library_root WHERE id = 11"))
            assertEquals(
                "/Music/Song.flac",
                singleText(connection, "SELECT canonicalPath FROM source_item WHERE id = 20"),
            )
            assertEquals(
                "1",
                singleText(
                    connection,
                    """
                    SELECT CAST(COUNT(*) AS TEXT)
                    FROM track_source_ref
                    WHERE trackId = 10
                      AND sourceItemId = 20
                      AND isAvailable = 1
                    """.trimIndent(),
                ),
            )
            assertEquals(
                "11",
                singleText(connection, "SELECT CAST(libraryRootId AS TEXT) FROM import_job WHERE id = 'job-1'"),
            )
            assertEquals(
                "cursor-1",
                singleText(connection, "SELECT cursorValue FROM source_sync_cursor WHERE libraryRootId = 11"),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun migrationSevenToEightAddsImportJobIdToSourceErrors() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execute(
                """
                CREATE TABLE source_error (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceAccountId INTEGER NOT NULL,
                    libraryRootId INTEGER,
                    sourceItemId INTEGER,
                    errorType TEXT NOT NULL,
                    message TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    resolvedAt INTEGER
                )
                """.trimIndent(),
            )
            MIGRATION_7_8.migrate(connection)

            val columns = columns(connection, "source_error")
            assertTrue("importJobId" in columns)
            assertEquals(
                "1",
                singleText(
                    connection,
                    """
                    SELECT CAST(COUNT(*) AS TEXT)
                    FROM sqlite_master
                    WHERE type = 'index'
                      AND name = 'index_source_error_importJobId'
                    """.trimIndent(),
                ),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun roomLibraryStoreCreatesPlaylistTracksAndRemoteLocWithoutLegacyDatabase() =
        withDatabase { database ->
            seedStorageAndFolder(database)
            val store = roomLibraryStore(database)
            val cover = StorageEntryLoc(StorageId(1), "/Music/cover.jpg")

            val playlist = assertNotNull(
                store.createPlaylist(
                    CreatePlaylistRequest(
                        title = "Room Playlist",
                        cover = sourceSelection(path = "/Music/cover.jpg", name = "cover.jpg", type = SourceNodeType.Image),
                        entries = listOf(
                            sourceSelection(path = "/Music/Track.flac", name = "Display Title", type = SourceNodeType.Track)
                        ),
                    )
                )
            )

            assertEquals("Room Playlist", playlist.abstr.meta.title)
            assertEquals(cover, playlist.abstr.meta.cover)
            assertEquals(1uL, playlist.abstr.musicCount)

            val musicId = playlist.musics.single().meta.id
            val track = assertNotNull(database.trackDao().get(musicId.value))
            val ref = database.trackSourceRefDao().findByTrackId(track.id).single()
            val sourceItem = assertNotNull(database.sourceItemDao().get(ref.sourceItemId))
            assertEquals(1, sourceItem.sourceAccountId)
            assertEquals("/Music/Track.flac", sourceItem.canonicalPath)
            assertTrue(ref.isAvailable)

            val music = assertNotNull(store.getMusic(musicId))
            assertEquals(StorageEntryLoc(StorageId(1), "/Music/Track.flac"), music.loc)
            assertEquals("Display Title", music.meta.title)
        }

    @Test
    fun sourceErrorsCanBeScopedToImportJob() = withDatabase { database ->
        seedStorageAndFolder(database)
        database.sourceErrorDao().insertAll(
            listOf(
                SourceErrorEntity(
                    sourceAccountId = 1,
                    libraryRootId = 1,
                    sourceItemId = null,
                    importJobId = "scan-current",
                    errorType = "METADATA_READ_FAILED",
                    message = "/Music/Broken.flac：元数据读取失败",
                    createdAt = 100,
                    resolvedAt = null,
                ),
                SourceErrorEntity(
                    sourceAccountId = 1,
                    libraryRootId = 1,
                    sourceItemId = null,
                    importJobId = "scan-old",
                    errorType = "METADATA_READ_FAILED",
                    message = "/Music/Old.flac：元数据读取失败",
                    createdAt = 90,
                    resolvedAt = null,
                ),
            )
        )

        val failures = database.sourceErrorDao().observeByImportJob("scan-current").first()

        assertEquals(1, failures.size)
        assertEquals("/Music/Broken.flac：元数据读取失败", failures.single().message)
    }

    @Test
    fun roomLibraryStoreUpdatesDurationAndRemovesLyricsInRoom() = withDatabase { database ->
        val rootId = seedStorageAndFolder(database)
        val store = roomLibraryStore(database)
        database.trackDao().upsertAll(
            listOf(
                track(id = 201)
            )
        )
        val item = seedSourceItem(database, rootId = rootId, id = 201, path = "/Music/song.flac")
        database.trackSourceRefDao().upsertAll(listOf(trackSourceRef(trackId = 201, sourceItemId = item.id)))
        database.metadataDao().upsertLyrics(
            listOf(
                LyricsEntity(
                    trackId = 201,
                    format = "LRC",
                    language = "eng",
                    synchronized = true,
                    content = "[00:01.00]Line",
                    sourcePath = null,
                    updatedAt = 1,
                )
            )
        )

        assertEquals(LyricLoadState.LOADED, assertNotNull(store.getMusic(MusicId(201))).lyric?.loadedState)

        store.removeLyric(MusicId(201))
        store.updateDuration(MusicId(201), -10)

        assertEquals(LyricLoadState.MISSING, assertNotNull(store.getMusic(MusicId(201))).lyric?.loadedState)
        assertEquals(0, database.trackDao().get(201)?.durationMs)
        assertNull(database.metadataDao().getLyrics(201))
    }

    @Test
    fun upsertMoveDeleteAndRestoreStayConsistent() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        val sourceItemDao = database.sourceItemDao()
        val trackSourceRefDao = database.trackSourceRefDao()
        val trackDao = database.trackDao()
        val insertedId = sourceItemDao.upsertAll(
            listOf(sourceItem(rootId = folderId, path = "/Music/Old/song.flac")),
        ).single()
        val inserted = assertNotNull(sourceItemDao.findByPath(1, "/Music/Old/song.flac"))
        assertEquals(insertedId, inserted.id)

        trackDao.upsertAll(listOf(track(id = 101)))
        trackSourceRefDao.upsertAll(listOf(trackSourceRef(trackId = 101, sourceItemId = inserted.id)))
        assertEquals(1, trackDao.count())

        sourceItemDao.upsertAll(
            listOf(
                inserted.copy(
                    canonicalPath = "/Music/New/song.flac",
                    displayPath = "/Music/New/song.flac",
                    lastSeenScanId = "scan-move",
                ),
            ),
        )

        assertNull(sourceItemDao.findByPath(1, "/Music/Old/song.flac"))
        val moved = assertNotNull(sourceItemDao.findByPath(1, "/Music/New/song.flac"))
        assertEquals(inserted.id, moved.id)
        assertEquals(101, trackDao.findBySourceItemIds(listOf(moved.id)).single().id)

        sourceItemDao.markMissingDeleted(folderId, "scan-other", now = 2)
        trackSourceRefDao.markUnavailableForDeletedSourceItems(folderId, now = 2)
        assertTrue(trackDao.page(limit = 10, offset = 0).isEmpty())

        sourceItemDao.markSeen(listOf(moved.id), "scan-restored", now = 3)
        trackSourceRefDao.markAvailableBySourceItemIds(listOf(moved.id), now = 3)
        assertEquals(listOf(101L), trackDao.page(limit = 10, offset = 0).map { it.id })
    }

    @Test
    fun zeroChangeBatchDoesNotTouchSourceItemTimestamps() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        val original = seedSourceItem(
            database = database,
            rootId = folderId,
            id = 301,
            path = "/Music/unchanged.flac",
        )

        database.sourceItemDao().applyScanBatch(changedItems = emptyList())

        val unchanged = assertNotNull(database.sourceItemDao().get(original.id))
        assertEquals(original.lastSyncedAt, unchanged.lastSyncedAt)
        assertEquals(original.lastSeenScanId, unchanged.lastSeenScanId)
        assertEquals(original.etag, unchanged.etag)
    }

    @Test
    fun directoryTombstoneLookupIsRootScopedAndEscapesLikeWildcards() =
        withDatabase { database ->
            val folderId = seedStorageAndFolder(database)
            seedSourceItem(
                database,
                folderId,
                id = 401,
                path = "/Music/100%_Hits/Disc-1/song.flac",
            )
            seedSourceItem(
                database,
                folderId,
                id = 402,
                path = "/Music/100AAHits/other.flac",
            )

            val descendants = database.sourceItemDao().findLiveAtOrBelowPath(
                libraryRootId = folderId,
                canonicalPath = "/Music/100%_Hits",
                descendantPattern = "/Music/100\\%\\_Hits/%",
            )

            assertEquals(listOf(401L), descendants.map { it.id })
        }

    @Test
    fun deltaDeletionUsesStableRemoteIdAndCursorAdvancesTransactionally() =
        withDatabase { database ->
            val folderId = seedStorageAndFolder(database)
            val sourceItemDao = database.sourceItemDao()
            val trackSourceRefDao = database.trackSourceRefDao()
            val syncDao = database.syncDao()
            val fileId = sourceItemDao.upsertAll(
                listOf(
                    sourceItem(
                        rootId = folderId,
                        path = "/Music/Before.flac",
                        remoteId = "drive-item-1",
                    ),
                ),
            ).single()
            database.trackDao().upsertAll(listOf(track(id = 101)))
            trackSourceRefDao.upsertAll(listOf(trackSourceRef(trackId = 101, sourceItemId = fileId)))

            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    assertEquals(
                        1,
                        sourceItemDao.markDeletedByProviderItemIds(
                            sourceAccountId = 1,
                            providerItemIds = listOf("drive-item-1"),
                            now = 100,
                        ),
                    )
                    trackSourceRefDao.markUnavailableForDeletedSourceItems(folderId, now = 100)
                    syncDao.upsertCursor(
                        SourceSyncCursorEntity(
                            sourceAccountId = 1,
                            libraryRootId = folderId,
                            cursorType = "delta",
                            cursorValue = "https://graph.microsoft.com/delta/final",
                            lastScanId = "delta-1",
                            lastSyncAt = 100,
                        ),
                    )
                }
            }

            assertTrue(database.trackDao().page(limit = 10, offset = 0).isEmpty())
            assertEquals(
                "https://graph.microsoft.com/delta/final",
                syncDao.getCursor(folderId)?.cursorValue,
            )
        }

    @Test
    fun webDavSyncTokenDoesNotAdvanceWhenCompletionTransactionFails() =
        withDatabase { database ->
            val folderId = seedStorageAndFolder(database)
            val syncDao = database.syncDao()
            syncDao.upsertCursor(
                SourceSyncCursorEntity(
                    sourceAccountId = 1,
                    libraryRootId = folderId,
                    cursorType = "webdav_sync_token",
                    cursorValue = "token-before",
                    lastScanId = "scan-before",
                    lastSyncAt = 1,
                ),
            )

            val failure = runCatching {
                database.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        syncDao.upsertCursor(
                            assertNotNull(
                                syncDao.getCursor(folderId, "webdav_sync_token"),
                            ).copy(
                                cursorValue = "token-after",
                                lastScanId = "scan-after",
                                lastSyncAt = 2,
                            ),
                        )
                        error("simulate final Room write failure")
                    }
                }
            }.exceptionOrNull()

            assertIs<IllegalStateException>(failure)
            val cursor = assertNotNull(syncDao.getCursor(folderId, "webdav_sync_token"))
            assertEquals("token-before", cursor.cursorValue)
            assertEquals("scan-before", cursor.lastScanId)
        }

    @Test
    fun writerTransactionRollsBackOnFailure() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        val failure = runCatching {
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    database.sourceItemDao().upsertAll(
                        listOf(sourceItem(rootId = folderId, path = "/Music/rollback.flac")),
                    )
                    error("force rollback")
                }
            }
        }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertNull(database.sourceItemDao().findByPath(1, "/Music/rollback.flac"))
        assertEquals(0, database.sourceItemDao().countForLibraryRoot(folderId))
    }

    @Test
    fun lyricsAndRawMetadataCanBeReplacedTransactionally() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        database.trackDao().upsertAll(listOf(track(id = 1)))
        val item = seedSourceItem(database, rootId = folderId, id = 1, path = "/Music/song.flac")
        database.trackSourceRefDao().upsertAll(listOf(trackSourceRef(trackId = 1, sourceItemId = item.id)))
        val metadataDao = database.metadataDao()

        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                metadataDao.upsertLyrics(
                    listOf(
                        LyricsEntity(
                            trackId = 1,
                            format = "TEXT",
                            language = "eng",
                            synchronized = false,
                            content = "Old",
                            sourcePath = null,
                            updatedAt = 1,
                        ),
                    ),
                )
                metadataDao.upsertRawMetadata(
                    listOf(
                        RawMetadataEntity(
                            trackId = 1,
                            tagKey = "Composer",
                            value = "Old",
                            locale = null,
                            description = null,
                        ),
                    ),
                )
            }
        }
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                metadataDao.deleteLyricsForTracks(listOf(1))
                metadataDao.deleteRawMetadataForTracks(listOf(1))
                metadataDao.upsertLyrics(
                    listOf(
                        LyricsEntity(
                            trackId = 1,
                            format = "LRC",
                            language = "zho",
                            synchronized = true,
                            content = "[00:01.00]New",
                            sourcePath = null,
                            updatedAt = 2,
                        ),
                    ),
                )
                metadataDao.upsertRawMetadata(
                    listOf(
                        RawMetadataEntity(
                            trackId = 1,
                            tagKey = "Composer",
                            value = "New",
                            locale = "zho",
                            description = "main",
                        ),
                    ),
                )
            }
        }

        assertEquals("[00:01.00]New", metadataDao.getLyrics(1)?.content)
        assertEquals("New", metadataDao.rawMetadataForTrack(1).single().value)
    }

    @Test
    fun upsertLyricsOverwritesExistingLyricsWithTheSameTrackId() =
        withDatabase { database ->
            database.trackDao().upsertAll(listOf(track(id = 1)))
            val metadataDao = database.metadataDao()
            metadataDao.upsertLyrics(
                listOf(
                    LyricsEntity(
                        trackId = 1,
                        format = "LRC",
                        language = null,
                        synchronized = true,
                        content = "[00:01.00]Old",
                        sourcePath = null,
                        updatedAt = 1,
                    ),
                ),
            )

            metadataDao.upsertLyrics(
                listOf(
                    LyricsEntity(
                        trackId = 1,
                        format = "LRC",
                        language = null,
                        synchronized = true,
                        content = "[00:01.00]<00:01.000>New<00:02.000>",
                        sourcePath = null,
                        updatedAt = 2,
                    ),
                ),
            )

            val stored = assertNotNull(metadataDao.getLyrics(1))
            assertEquals("[00:01.00]<00:01.000>New<00:02.000>", stored.content)
            assertEquals(2, stored.updatedAt)
        }

    @Test
    fun fastStandardAndFullModesPreserveOrUpdateRequestedMetadata() =
        withDatabase { database ->
            database.trackDao().upsertAll(listOf(track(id = 1)))
            val metadataDao = database.metadataDao()
            metadataDao.upsertLyrics(
                listOf(
                    LyricsEntity(
                        trackId = 1,
                        format = "TEXT",
                        language = null,
                        synchronized = false,
                        content = "Old lyrics",
                        sourcePath = null,
                        updatedAt = 1,
                    )
                )
            )
            metadataDao.upsertRawMetadata(
                listOf(RawMetadataEntity(0, 1, "Composer", "Old raw", null, null))
            )
            metadataDao.upsertArtwork(
                listOf(
                    ArtworkEntity(
                        trackId = 1,
                        albumId = null,
                        contentHash = "old-art",
                        localPath = "/cache/old.jpg",
                        thumbnailPath = null,
                        width = null,
                        height = null,
                        mimeType = "image/jpeg",
                        pictureType = "CoverFront",
                    )
                )
            )

            val update = OptionalMetadataUpdate(
                trackId = 1,
                albumId = null,
                metadata = optionalMetadata("New lyrics", "New raw", "new-art"),
            )
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    metadataDao.updateOptionalMetadata(
                        updates = listOf(update),
                        options = MetadataScanMode.Fast.toOptions(),
                        now = 2,
                    )
                }
            }
            assertEquals("Old lyrics", metadataDao.getLyrics(1)?.content)
            assertEquals("Old raw", metadataDao.rawMetadataForTrack(1).single().value)
            assertEquals("old-art", metadataDao.getArtworkForTrack(1)?.contentHash)

            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    metadataDao.updateOptionalMetadata(
                        updates = listOf(update),
                        options = MetadataScanMode.Standard.toOptions(),
                        now = 3,
                    )
                }
            }
            assertEquals("New lyrics", metadataDao.getLyrics(1)?.content)
            assertEquals("Old raw", metadataDao.rawMetadataForTrack(1).single().value)
            assertEquals("old-art", metadataDao.getArtworkForTrack(1)?.contentHash)

            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    metadataDao.updateOptionalMetadata(
                        updates = listOf(update),
                        options = MetadataScanMode.Full.toOptions(),
                        now = 4,
                    )
                }
            }
            assertEquals("New lyrics", metadataDao.getLyrics(1)?.content)
            assertEquals("New raw", metadataDao.rawMetadataForTrack(1).single().value)
            assertEquals("new-art", metadataDao.getArtworkForTrack(1)?.contentHash)

            val artworkId = metadataDao.getArtworkByContentHash("new-art")?.id
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    metadataDao.updateOptionalMetadata(
                        updates = listOf(update),
                        options = MetadataScanMode.Full.toOptions(),
                        now = 5,
                    )
                }
            }
            assertEquals(artworkId, metadataDao.getArtworkByContentHash("new-art")?.id)
        }

    @Test
    fun artworkCacheKeysCanBeReadByTrackAlbumAndHash() = withDatabase { database ->
        val metadataDao = database.metadataDao()
        metadataDao.upsertArtwork(
            listOf(
                ArtworkEntity(
                    trackId = 10,
                    albumId = null,
                    contentHash = "track-hash",
                    localPath = "/cache/artwork/track-hash.jpg",
                    thumbnailPath = "/cache/artwork/track-hash-thumb.jpg",
                    width = 600,
                    height = 600,
                    mimeType = "image/jpeg",
                    pictureType = "CoverFront",
                ),
                ArtworkEntity(
                    trackId = null,
                    albumId = 20,
                    contentHash = "album-hash",
                    localPath = "/cache/artwork/album-hash.png",
                    thumbnailPath = null,
                    width = 1200,
                    height = 1200,
                    mimeType = "image/png",
                    pictureType = "CoverFront",
                ),
            ),
        )

        assertEquals("/cache/artwork/track-hash.jpg", metadataDao.getArtworkForTrack(10)?.localPath)
        assertEquals("/cache/artwork/album-hash.png", metadataDao.getArtworkForAlbum(20)?.localPath)
        assertEquals("image/jpeg", metadataDao.getArtworkByContentHash("track-hash")?.mimeType)
        assertNull(metadataDao.getArtworkForTrack(999))
    }

    @Test
    fun manualAlbumMetadataChangePreservesExistingAlbumArtwork() = withDatabase { database ->
        val metadataDao = database.metadataDao()
        metadataDao.upsertAlbums(
            listOf(
                AlbumEntity(
                    id = 20,
                    name = "Original Album",
                    normalizedName = "original album",
                    sortName = null,
                    year = 2025,
                    artworkId = null,
                ),
            ),
        )
        metadataDao.upsertArtwork(
            listOf(
                ArtworkEntity(
                    albumId = 20,
                    trackId = null,
                    contentHash = "preserved-art",
                    localPath = "/cache/artwork/preserved.jpg",
                    thumbnailPath = null,
                    width = 800,
                    height = 800,
                    mimeType = "image/jpeg",
                    pictureType = "CoverFront",
                ),
            ),
        )

        val newAlbumId = metadataDao.resolveManualMetadataAlbum(
            name = "Matched Album",
            date = "2026-07-16",
            currentAlbumId = 20,
        )

        assertEquals("preserved-art", metadataDao.getArtworkForAlbum(newAlbumId)?.contentHash)
        assertEquals("preserved-art", metadataDao.getArtworkForAlbum(20)?.contentHash)
    }

    @Test
    fun normalizedDimensionsAndRelationshipsPersist() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        val metadataDao = database.metadataDao()
        metadataDao.insertAlbums(
            listOf(
                AlbumEntity(
                    name = "Album",
                    normalizedName = "album",
                    sortName = null,
                    year = 2026,
                    artworkId = null,
                ),
            ),
        )
        metadataDao.insertArtists(
            listOf(
                ArtistEntity(name = "Primary", normalizedName = "primary", sortName = null),
                ArtistEntity(name = "Guest", normalizedName = "guest", sortName = null),
                ArtistEntity(name = "Album Artist", normalizedName = "album artist", sortName = null),
            ),
        )
        metadataDao.insertGenres(
            listOf(GenreEntity(name = "Jazz", normalizedName = "jazz")),
        )
        val album = metadataDao.findAlbumsByNormalizedNames(listOf("album")).single()
        val artists = metadataDao.findArtistsByNormalizedNames(
            listOf("primary", "guest", "album artist"),
        ).associateBy { it.normalizedName }
        val genre = metadataDao.findGenresByNormalizedNames(listOf("jazz")).single()
        database.trackDao().upsertAll(
            listOf(track(id = 1).copy(albumId = album.id)),
        )
        val item = seedSourceItem(database, rootId = folderId, id = 1, path = "/Music/song.flac")
        database.trackSourceRefDao().upsertAll(listOf(trackSourceRef(trackId = 1, sourceItemId = item.id)))
        metadataDao.upsertTrackArtists(
            listOf(
                TrackArtistCrossRef(1, artists.getValue("primary").id, 0),
                TrackArtistCrossRef(1, artists.getValue("guest").id, 1),
            ),
        )
        metadataDao.upsertAlbumArtists(
            listOf(AlbumArtistCrossRef(album.id, artists.getValue("album artist").id, 0)),
        )
        metadataDao.upsertTrackGenres(
            listOf(TrackGenreCrossRef(1, genre.id)),
        )

        assertEquals(listOf("Primary", "Guest"), metadataDao.artistNamesForTrack(1))
        assertEquals(listOf("Album Artist"), metadataDao.artistNamesForAlbum(album.id))
        assertEquals(listOf("Jazz"), metadataDao.genreNamesForTrack(1))
        val libraryAlbum = metadataDao.observeAlbumsWithTracks().first().single()
        assertEquals(album.id, libraryAlbum.album.id)
        assertEquals("Album Artist", libraryAlbum.artistName)
    }

    @Test
    fun importsAndPagesFiftyThousandTracksWithinBoundedTime() = withDatabase { database ->
        val folderId = seedStorageAndFolder(database)
        val total = 50_000
        val batchSize = 500
        val startedAt = System.nanoTime()

        repeat(total / batchSize) { batchIndex ->
            val first = batchIndex * batchSize + 1
            val files = (first until first + batchSize).map { index ->
                sourceItem(
                    id = index.toLong(),
                    rootId = folderId,
                    path = "/Music/track-${index.toString().padStart(5, '0')}.flac",
                    remoteId = "item-$index",
                )
            }
            val tracks = files.map { file ->
                track(id = file.id)
            }
            val refs = files.map { file ->
                trackSourceRef(trackId = file.id, sourceItemId = file.id)
            }
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    database.sourceItemDao().upsertAll(files)
                    database.trackDao().upsertAll(tracks)
                    database.trackSourceRefDao().upsertAll(refs)
                }
            }
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertEquals(total.toLong(), database.sourceItemDao().countForLibraryRoot(folderId))
        assertEquals(total.toLong(), database.trackDao().count())
        val lastPage = database.trackDao().page(limit = 200, offset = total - 200)
        assertEquals(200, lastPage.size)
        assertTrue(elapsedMs < 60_000, "50,000-track import took ${elapsedMs}ms")
        println("room_50000_import_ms=$elapsedMs")
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

    private fun roomLibraryStore(database: AppDatabase) = RoomLibraryStore(
        database = database,
        trackDao = database.trackDao(),
        sourceItemDao = database.sourceItemDao(),
        trackSourceRefDao = database.trackSourceRefDao(),
        playlistDao = database.playlistDao(),
        metadataDao = database.metadataDao(),
    )

    private suspend fun seedStorageAndFolder(database: AppDatabase): Long {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = 1,
                providerType = ProviderTypes.WebDav,
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
        return assertNotNull(database.libraryRootDao().findByPath(1, "/Music")).id
    }

    private suspend fun seedSourceItem(
        database: AppDatabase,
        rootId: Long,
        id: Long,
        path: String,
        remoteId: String = "item-$id",
        isDeleted: Boolean = false,
    ): SourceItemEntity {
        val item = sourceItem(rootId = rootId, path = path, id = id, remoteId = remoteId, isDeleted = isDeleted)
        database.sourceItemDao().upsertAll(listOf(item))
        return assertNotNull(database.sourceItemDao().get(id))
    }

    private fun sourceItem(
        rootId: Long,
        path: String,
        id: Long = 0,
        remoteId: String = "item-1",
        isDeleted: Boolean = false,
    ) = SourceItemEntity(
        id = id,
        sourceAccountId = 1,
        libraryRootId = rootId,
        itemType = SourceItemTypes.Track,
        providerItemId = remoteId,
        parentProviderItemId = "folder-1",
        canonicalPath = path,
        displayPath = path,
        displayName = path.substringAfterLast('/'),
        mimeType = "audio/flac",
        sizeBytes = 1_000,
        etag = "\"etag-$remoteId\"",
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

    private fun track(id: Long) = TrackEntity(
        id = id,
        title = "Track ${id.toString().padStart(5, '0')}",
        sortTitle = null,
        albumId = null,
        albumArtist = null,
        composer = null,
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
        bitRate = 900_000,
        bitsPerSample = 24,
        channels = 2,
        channelLayout = null,
        codec = "FLAC",
        container = "FLAC",
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun trackSourceRef(
        trackId: Long,
        sourceItemId: Long,
        isAvailable: Boolean = true,
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
        bitRate = 900_000,
        sampleRate = 48_000,
        bitsPerSample = 24,
        channels = 2,
        lossless = true,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun sourceSelection(
        path: String,
        name: String,
        type: SourceNodeType,
    ) = SourceNodeSelection(
        sourceId = BuiltInSourceIds.Local,
        accountId = SourceAccountId("storage:1"),
        node = SourceNode(
            accountId = SourceAccountId("storage:1"),
            nodeId = "entry-$name",
            remoteId = "entry-$name",
            parentNodeId = "folder-1",
            name = name,
            path = path,
            type = type,
            sizeBytes = 1_000uL,
            mimeType = if (type == SourceNodeType.Image) "image/jpeg" else "audio/flac",
            etag = "\"etag-$name\"",
            createdAtEpochMs = 1,
            modifiedAtEpochMs = 1,
        ),
    )

    private fun columns(connection: SQLiteConnection, table: String): Set<String> = buildSet {
        connection.prepare("PRAGMA table_info($table)").use { statement ->
            while (statement.step()) {
                add(statement.getText(1))
            }
        }
    }

    private fun tableNames(connection: SQLiteConnection): Set<String> = buildSet {
        connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { statement ->
            while (statement.step()) {
                add(statement.getText(0))
            }
        }
    }

    private fun singleText(connection: SQLiteConnection, sql: String): String {
        return connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.getText(0)
        }
    }

    private fun createLegacyV6SourceSchema(connection: SQLiteConnection) {
        listOf(
            """
            CREATE TABLE storage (
                id INTEGER NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                displayName TEXT NOT NULL,
                driveId TEXT,
                baseUrl TEXT,
                credentialRef TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE selected_folder (
                id INTEGER NOT NULL PRIMARY KEY,
                storageId INTEGER NOT NULL,
                remoteId TEXT,
                canonicalPath TEXT,
                displayPath TEXT,
                syncStatus TEXT NOT NULL,
                deltaLink TEXT,
                lastSyncAt INTEGER
            )
            """.trimIndent(),
            """
            CREATE TABLE remote_file (
                id INTEGER NOT NULL PRIMARY KEY,
                storageId INTEGER NOT NULL,
                selectedFolderId INTEGER,
                remoteId TEXT,
                parentRemoteId TEXT,
                canonicalPath TEXT,
                displayPath TEXT,
                fileName TEXT NOT NULL,
                mimeType TEXT,
                size INTEGER,
                etag TEXT,
                ctag TEXT,
                createdAt INTEGER,
                modifiedAt INTEGER,
                contentHash TEXT,
                isDeleted INTEGER NOT NULL,
                lastSeenScanId TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE sync_cursor (
                id INTEGER NOT NULL PRIMARY KEY,
                selectedFolderId INTEGER NOT NULL,
                deltaLink TEXT,
                continuationToken TEXT,
                lastScanId TEXT,
                lastSyncAt INTEGER
            )
            """.trimIndent(),
            """
            CREATE TABLE import_job (
                id TEXT NOT NULL PRIMARY KEY,
                selectedFolderId INTEGER NOT NULL,
                status TEXT NOT NULL,
                scannedCount INTEGER NOT NULL,
                importedCount INTEGER NOT NULL,
                skippedCount INTEGER NOT NULL,
                failedCount INTEGER NOT NULL,
                checkpoint TEXT,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE track (
                id INTEGER NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                sortTitle TEXT,
                albumId INTEGER,
                albumArtist TEXT,
                composer TEXT,
                comment TEXT,
                grouping TEXT,
                durationMs INTEGER,
                discNumber INTEGER,
                discTotal INTEGER,
                trackNumber INTEGER,
                trackTotal INTEGER,
                year INTEGER,
                date TEXT,
                sampleRate INTEGER,
                bitRate INTEGER,
                bitsPerSample INTEGER,
                channels INTEGER,
                channelLayout TEXT,
                codec TEXT,
                container TEXT,
                lossless INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                lastPlayedAt INTEGER,
                artist TEXT,
                lyricist TEXT,
                conductor TEXT,
                copyright TEXT,
                publisher TEXT,
                originalReleaseDate TEXT,
                bpm REAL,
                musicalKey TEXT,
                isrc TEXT,
                musicBrainzRecordingId TEXT,
                musicBrainzTrackId TEXT,
                musicBrainzReleaseId TEXT,
                musicBrainzReleaseGroupId TEXT,
                musicBrainzArtistId TEXT,
                musicBrainzReleaseArtistId TEXT,
                musicBrainzWorkId TEXT,
                replayGainTrackGain REAL,
                replayGainTrackPeak REAL,
                replayGainAlbumGain REAL,
                replayGainAlbumPeak REAL,
                remoteFileId INTEGER,
                sourceStorageId INTEGER,
                sourcePath TEXT
            )
            """.trimIndent(),
            """
            INSERT INTO storage(
                id, type, displayName, driveId, baseUrl, credentialRef, createdAt, updatedAt
            ) VALUES (
                1, 'WEBDAV', 'Archive', NULL, 'https://example.invalid/dav', 'cred-1', 1, 2
            )
            """.trimIndent(),
            """
            INSERT INTO selected_folder(
                id, storageId, remoteId, canonicalPath, displayPath, syncStatus, deltaLink, lastSyncAt
            ) VALUES (
                11, 1, 'folder-1', '/Music', 'Music', 'COMPLETED', 'delta-1', 3
            )
            """.trimIndent(),
            """
            INSERT INTO remote_file(
                id, storageId, selectedFolderId, remoteId, parentRemoteId, canonicalPath,
                displayPath, fileName, mimeType, size, etag, ctag, createdAt, modifiedAt,
                contentHash, isDeleted, lastSeenScanId
            ) VALUES (
                20, 1, 11, 'file-1', 'folder-1', '/Music/Song.flac',
                '/Music/Song.flac', 'Song.flac', 'audio/flac', 1000, 'etag-1', 'rev-1',
                4, 5, 'hash-1', 0, 'scan-1'
            )
            """.trimIndent(),
            """
            INSERT INTO sync_cursor(
                id, selectedFolderId, deltaLink, continuationToken, lastScanId, lastSyncAt
            ) VALUES (
                30, 11, 'cursor-1', NULL, 'scan-1', 6
            )
            """.trimIndent(),
            """
            INSERT INTO import_job(
                id, selectedFolderId, status, scannedCount, importedCount, skippedCount,
                failedCount, checkpoint, errorMessage, createdAt, updatedAt
            ) VALUES (
                'job-1', 11, 'COMPLETED', 1, 1, 0, 0, NULL, NULL, 7, 8
            )
            """.trimIndent(),
            """
            INSERT INTO track(
                id, title, sortTitle, albumId, albumArtist, composer, comment, grouping,
                durationMs, discNumber, discTotal, trackNumber, trackTotal, year, date,
                sampleRate, bitRate, bitsPerSample, channels, channelLayout, codec,
                container, lossless, createdAt, updatedAt, lastPlayedAt, artist,
                lyricist, conductor, copyright, publisher, originalReleaseDate, bpm,
                musicalKey, isrc, musicBrainzRecordingId, musicBrainzTrackId,
                musicBrainzReleaseId, musicBrainzReleaseGroupId, musicBrainzArtistId,
                musicBrainzReleaseArtistId, musicBrainzWorkId, replayGainTrackGain,
                replayGainTrackPeak, replayGainAlbumGain, replayGainAlbumPeak,
                remoteFileId, sourceStorageId, sourcePath
            ) VALUES (
                10, 'Song', NULL, NULL, 'Album Artist', 'Composer', NULL, NULL,
                180000, 1, 1, 1, 10, 2026, '2026', 48000, 900000, 24,
                2, NULL, 'FLAC', 'FLAC', 1, 9, 10, NULL, 'Artist',
                NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'ISRC1',
                'mbid-1', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                NULL, NULL, NULL, 20, 1, '/Music/Song.flac'
            )
            """.trimIndent(),
        ).forEach { sql ->
            connection.execute(sql)
        }
    }

    private fun optionalMetadata(
        lyrics: String,
        rawValue: String,
        artworkHash: String,
    ) = RemoteMetadata(
        title = "Song",
        artist = "Artist",
        artists = listOf("Artist"),
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
        lyrics = RemoteEmbeddedLyrics(lyrics, false, null, null),
        embeddedLyricsKind = if (lyrics.startsWith("[")) "LineTimed" else "Plain",
        artwork = RemoteArtwork(
            contentHash = artworkHash,
            localPath = "/cache/$artworkHash.jpg",
            thumbnailPath = null,
            width = null,
            height = null,
            mimeType = "image/jpeg",
            pictureType = "CoverFront",
        ),
        hasEmbeddedArtwork = true,
        rawMetadata = listOf(
            RemoteRawMetadataEntry("Composer", rawValue, null, null)
        ),
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
        metadataRequestCount = 1u,
        metadataFetchedBytes = 128u,
        metadataElapsedMs = 2u,
        artworkCachedBytes = 64u,
    )

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement ->
            statement.step()
        }
    }
}
