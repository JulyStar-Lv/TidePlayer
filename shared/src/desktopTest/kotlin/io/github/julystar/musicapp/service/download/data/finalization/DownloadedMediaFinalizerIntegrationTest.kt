package io.github.julystar.musicapp.service.download.data.finalization

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.core.data.settings.DataStoreSettingsRepository
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.ArtworkEntity
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationError
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationRequest
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationResult
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadedMediaFinalizerIntegrationTest {
    @Test
    fun missingStableMediaReturnsStructuredFailure() {
        runBlocking {
            val root = Files.createTempDirectory("download-finalizer-missing-").toFile()
            val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
                AppDatabaseConstructor.initialize()
            }
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
            try {
                val dataStore = createAppDataStore {
                    root.resolve("settings.preferences_pb").absolutePath.toPath()
                }
                val finalizer = DownloadedMediaFinalizer(
                    database = database,
                    settingsRepository = DataStoreSettingsRepository(dataStore, applyLanguageMode = {}),
                )

                val result = finalizer.finalize(
                    DownloadFinalizationRequest(
                        mediaId = legacyStorageTrackMediaId(
                            sourceId = BuiltInSourceIds.WebDav,
                            accountId = storageSourceAccountId(REMOTE_ACCOUNT_ID),
                            path = REMOTE_PATH,
                        ),
                        localPath = root.resolve("missing.flac").absolutePath,
                        mimeType = "audio/flac",
                        fallbackTitle = "Missing",
                    )
                )

                assertEquals(
                    DownloadFinalizationError.MissingFile,
                    assertIs<DownloadFinalizationResult.Failure>(result).error,
                )
            } finally {
                database.close()
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun finalizationWritesMediaAndRefreshesOnlyTheResolvedLibraryTrack() {
        runBlocking {
            val root = Files.createTempDirectory("download-finalizer-test-").toFile()
            val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
                AppDatabaseConstructor.initialize()
            }
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
            try {
                val audio = fixture("minimal.flac").copyTo(root.resolve("download.flac"))
                val cover = fixture("cover.png")
                val ttml = """
                    <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
                        <p begin="00:01.000" end="00:02.000">主歌词</p>
                    </div></body></tt>
                """.trimIndent()
                seedLibrary(database, cover, ttml)
                val dataStore = createAppDataStore {
                    root.resolve("settings.preferences_pb").absolutePath.toPath()
                }
                val finalizer = DownloadedMediaFinalizer(
                    database = database,
                    settingsRepository = DataStoreSettingsRepository(dataStore, applyLanguageMode = {}),
                    nowEpochMs = { 10L },
                )

                val result = finalizer.finalize(
                    DownloadFinalizationRequest(
                        mediaId = legacyStorageTrackMediaId(
                            sourceId = BuiltInSourceIds.WebDav,
                            accountId = storageSourceAccountId(REMOTE_ACCOUNT_ID),
                            path = REMOTE_PATH,
                        ),
                        localPath = audio.absolutePath,
                        mimeType = "audio/flac",
                        fallbackTitle = "Fallback",
                        fallbackArtist = null,
                        fallbackAlbum = null,
                        expectedDurationMs = null,
                        expectedBytes = audio.length(),
                    )
                )

                assertIs<DownloadFinalizationResult.Success>(result)
                assertEquals(TRACK_ID, result.libraryTrackId)
                val refreshed = assertNotNull(database.trackDao().get(TRACK_ID))
                assertEquals("Resolved title", refreshed.title)
                assertEquals("Resolved artist", refreshed.artist)
                assertEquals("Resolved album", database.metadataDao().getAlbum(refreshed.albumId!!)?.name)
                assertEquals(cover.absolutePath, database.metadataDao().getArtworkForTrack(TRACK_ID)?.localPath)
                assertEquals(ttml, database.metadataDao().getLyricsCandidates(TRACK_ID).single().content)
                assertTrue(root.resolve("download.ttml").isFile)

                val localAccount = database.sourceAccountDao().listAll()
                    .single { account -> account.providerType == ProviderTypes.Local }
                val localItem = assertNotNull(
                    database.sourceItemDao().findByPath(localAccount.id, audio.absolutePath)
                )
                val localRef = database.trackSourceRefDao().findByTrackId(TRACK_ID)
                    .single { ref -> ref.sourceItemId == localItem.id }
                assertTrue(localRef.isDownloaded)
                assertTrue(localRef.isPreferred)
                assertEquals(true, localRef.hasEmbeddedArtwork)
                assertNotNull(localRef.embeddedLyricsKind)
            } finally {
                database.close()
                root.deleteRecursively()
            }
        }
    }

    private suspend fun seedLibrary(database: AppDatabase, cover: File, ttml: String) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = REMOTE_ACCOUNT_ID,
                providerType = ProviderTypes.WebDav,
                displayName = "Remote",
                endpoint = "https://example.invalid/dav",
                externalAccountId = null,
                credentialRef = "remote-credential",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        database.sourceItemDao().upsertAll(
            listOf(
                SourceItemEntity(
                    id = REMOTE_ITEM_ID,
                    sourceAccountId = REMOTE_ACCOUNT_ID,
                    libraryRootId = null,
                    itemType = SourceItemTypes.Track,
                    providerItemId = "remote-track",
                    parentProviderItemId = null,
                    canonicalPath = REMOTE_PATH,
                    displayPath = REMOTE_PATH,
                    displayName = "song.flac",
                    mimeType = "audio/flac",
                    sizeBytes = null,
                    etag = null,
                    revision = null,
                    createdAtRemote = 1,
                    modifiedAtRemote = 1,
                    contentHash = null,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = 1,
                    lastSyncedAt = 1,
                    lastSeenScanId = null,
                )
            )
        )
        database.metadataDao().insertAlbums(
            listOf(
                AlbumEntity(
                    id = ALBUM_ID,
                    name = "Resolved album",
                    normalizedName = "resolved album",
                    sortName = null,
                    year = null,
                    artworkId = null,
                )
            )
        )
        database.trackDao().upsertAll(listOf(track()))
        database.trackSourceRefDao().upsertAll(
            listOf(
                TrackSourceRefEntity(
                    trackId = TRACK_ID,
                    sourceItemId = REMOTE_ITEM_ID,
                    role = "primary",
                    matchMethod = "scan",
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
        database.metadataDao().upsertArtwork(
            listOf(
                ArtworkEntity(
                    trackId = TRACK_ID,
                    albumId = null,
                    contentHash = "cover-hash",
                    localPath = cover.absolutePath,
                    thumbnailPath = null,
                    width = 1,
                    height = 1,
                    mimeType = "image/png",
                    pictureType = "FrontCover",
                )
            )
        )
        database.metadataDao().upsertLyrics(
            listOf(
                LyricsEntity(
                    trackId = TRACK_ID,
                    format = "TTML",
                    language = "zh-CN",
                    synchronized = true,
                    content = ttml,
                    sourcePath = "external:test",
                    updatedAt = 2,
                    sourceKind = "ExternalTtml",
                )
            )
        )
    }

    private fun track() = TrackEntity(
        id = TRACK_ID,
        title = "Resolved title",
        sortTitle = null,
        albumId = ALBUM_ID,
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
        artist = "Resolved artist",
        metadataSource = TrackMetadataSources.Plugin,
    )

    private fun fixture(name: String): File {
        return generateSequence(File(System.getProperty("user.dir"))) { directory ->
            directory.parentFile
        }
            .map { directory -> directory.resolve("rust-libs/audio-metadata/tests/fixtures/$name") }
            .firstOrNull(File::isFile)
            ?: error("Unable to locate audio-metadata test fixture: $name")
    }

    private companion object {
        const val REMOTE_ACCOUNT_ID = 7L
        const val REMOTE_ITEM_ID = 70L
        const val TRACK_ID = 10L
        const val ALBUM_ID = 20L
        const val REMOTE_PATH = "/Music/song.flac"
    }
}
