package io.github.julystar.musicapp.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl.Companion.toPlaylistSummary
import io.github.julystar.musicapp.core.data.media.resolveRoomArtworkCacheKey
import io.github.julystar.musicapp.core.data.CreatePlaylistRequest
import io.github.julystar.musicapp.core.data.UpdatePlaylistRequest
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaylistCoverPersistenceTest {
    @Test
    fun generatedCoversSurviveDatabaseReopenAndRename() = runBlocking {
        val directory = Files.createTempDirectory("playlist-cover-test").toFile()
        val bytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aFZkAAAAASUVORK5CYII=")
        fun openDatabase() = Room.databaseBuilder<AppDatabase>(File(directory, "library.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        fun store(database: AppDatabase) = RoomLibraryStore(
            database, database.trackDao(), database.sourceItemDao(), database.trackSourceRefDao(),
            database.playlistDao(), database.metadataDao(),
            playlistCoverDirectory = File(directory, "covers").absolutePath,
        )
        var database = openDatabase()
        try {
            val libraryStore = store(database)
            val created = assertNotNull(libraryStore.createPlaylist(CreatePlaylistRequest(
                title = "Saved cover", cover = null, entries = emptyList(), coverImage = bytes,
            )))
            val playlistId = created.abstr.meta.id.value
            val artworkId = assertNotNull(database.playlistDao().get(playlistId)?.artworkId)
            val artwork = assertNotNull(database.metadataDao().getArtworkForPlaylist(playlistId))
            assertEquals(artworkId, artwork.id)
            assertContentEquals(bytes, File(artwork.localPath).readBytes())
            database.trackDao().upsertAll(listOf(TrackEntity(
                id = 912, title = "Song", albumId = null, durationMs = null, trackNumber = null,
                discNumber = null, year = null, date = null, createdAt = 1, updatedAt = 1,
                sortTitle = null, albumArtist = null, composer = null, comment = null, grouping = null,
                discTotal = null, trackTotal = null, sampleRate = null, bitRate = null, bitsPerSample = null,
                channels = null, channelLayout = null, codec = null, container = null, lossless = null,
            )))
            assertTrue(libraryStore.createPlaylistWithMusic("With current track", 912, bytes))
            val withMusic = database.playlistDao().observeSummaries().first().single { it.title == "With current track" }
            assertEquals(artworkId, withMusic.artworkId)
            assertEquals(912L, database.playlistDao().observeTracks(withMusic.id).first().single().trackId)
            libraryStore.updatePlaylist(UpdatePlaylistRequest(playlistId, "Renamed", null))
            database.close()
            database = openDatabase()
            assertEquals("Renamed", database.playlistDao().get(playlistId)?.title)
            val reloaded = assertNotNull(database.metadataDao().getArtworkForPlaylist(playlistId))
            assertEquals(artworkId, reloaded.id)
            assertContentEquals(bytes, File(reloaded.localPath).readBytes())
            val row = database.playlistDao().observeSummaries().first().single { it.id == playlistId }
            val summary = store(database).mapPlaylistSummary(row).toPlaylistSummary(row.firstTrackId, row.artworkId)
            assertEquals(Artwork.LibraryPlaylist(playlistId, revision = artworkId), summary.coverArtwork)
            val cacheKey = assertNotNull(summary.coverArtwork?.resolveRoomArtworkCacheKey(
                findTrack = { error("Saved cover should not query tracks") },
                findTrackArtwork = { error("Saved cover should not query track artwork") },
                findAlbumArtwork = { error("Saved cover should not query album artwork") },
                findPlaylistArtwork = database.metadataDao()::getArtworkForPlaylist,
            ))
            assertContentEquals(bytes, File(cacheKey.localPath).readBytes())
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun editedGeneratedCoverSurvivesReopenAndChangesTheArtworkCacheIdentity() = runBlocking {
        val directory = Files.createTempDirectory("playlist-edit-cover-test").toFile()
        fun png(color: Int): ByteArray {
            val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, color)
            return java.io.ByteArrayOutputStream().use { output ->
                javax.imageio.ImageIO.write(image, "png", output)
                output.toByteArray()
            }
        }
        val original = png(0xFFFF0000.toInt())
        val replacement = png(0xFF00FF00.toInt())
        fun openDatabase() = Room.databaseBuilder<AppDatabase>(File(directory, "library.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        fun store(database: AppDatabase) = RoomLibraryStore(
            database, database.trackDao(), database.sourceItemDao(), database.trackSourceRefDao(),
            database.playlistDao(), database.metadataDao(),
            playlistCoverDirectory = File(directory, "covers").absolutePath,
        )
        var database = openDatabase()
        try {
            val libraryStore = store(database)
            val playlist = assertNotNull(libraryStore.createPlaylist(CreatePlaylistRequest("Original", null, emptyList(), original)))
            val id = playlist.abstr.meta.id.value
            val before = database.playlistDao().observeSummaries().first().single { it.id == id }
            val beforeArtwork = libraryStore.mapPlaylistSummary(before).toPlaylistSummary(before.firstTrackId, before.artworkId).coverArtwork
            libraryStore.updatePlaylist(UpdatePlaylistRequest(id, "Edited", null, replacement))
            val after = database.playlistDao().observeSummaries().first().single { it.id == id }
            val afterArtwork = libraryStore.mapPlaylistSummary(after).toPlaylistSummary(after.firstTrackId, after.artworkId).coverArtwork
            assertTrue(beforeArtwork != afterArtwork, "An edited cover must reload in the details page and sidebar")
            libraryStore.updatePlaylist(UpdatePlaylistRequest(id, "Renamed again", null))
            database.close()
            database = openDatabase()
            assertEquals("Renamed again", database.playlistDao().get(id)?.title)
            val saved = assertNotNull(database.metadataDao().getArtworkForPlaylist(id))
            assertContentEquals(replacement, File(saved.localPath).readBytes())
            assertEquals(after.artworkId, saved.id)
        } finally {
            database.close()
            directory.deleteRecursively()
        }
    }
}
