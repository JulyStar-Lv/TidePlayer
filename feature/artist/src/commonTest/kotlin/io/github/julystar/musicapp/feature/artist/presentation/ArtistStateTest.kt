package io.github.julystar.musicapp.feature.artist.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtistStateTest {

    @Test
    fun `default state is loading with empty collections`() {
        val state = ArtistState()

        assertTrue(state.isLoading)
        assertEquals("", state.name)
        assertEquals(persistentListOf(), state.albums)
        assertEquals(persistentListOf(), state.tracks)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries artist data`() {
        val albums = persistentListOf(ArtistAlbumItem(id = 1, name = "Album", year = 2024, artwork = null))
        val tracks = persistentListOf(ArtistTrackItem(id = 1, title = "Song", albumName = "Album", trackNumber = 1, discNumber = 1, durationMs = 200_000, mediaId = null, canDownload = false, albumId = 1L))
        val state = ArtistState(isLoading = false, name = "Artist", albums = albums, tracks = tracks)

        assertFalse(state.isLoading)
        assertEquals("Artist", state.name)
        assertEquals(1, state.albums.size)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `error state preserves name`() {
        val state = ArtistState(isLoading = false, name = "Artist", error = UiMessage.Text("Failed"))

        assertEquals("Artist", state.name)
        assertEquals(UiMessage.Text("Failed"), state.error)
    }

    @Test
    fun `play track action carries track id`() {
        val action = ArtistAction.PlayTrack(7)
        assertEquals(7, action.trackId)
    }

    @Test
    fun `navigate to album action carries album id`() {
        val action = ArtistAction.NavigateToAlbum(3)
        assertEquals(3, action.albumId)
    }

    @Test
    fun `row keys stay unique when track ids repeat`() {
        val track = ArtistTrackItem(
            id = 42,
            title = "T",
            albumName = null,
            trackNumber = null,
            discNumber = null,
            durationMs = null,
            mediaId = null,
            canDownload = false,
            albumId = null,
        )

        assertNotEquals(track.lazyListKey(0), track.copy(title = "T2").lazyListKey(1))
    }

    @Test
    fun `album row keys stay unique when album ids repeat`() {
        val album = ArtistAlbumItem(id = 42, name = "A", year = null, artwork = null)

        assertNotEquals(album.lazyListKey(0), album.copy(name = "A2").lazyListKey(1))
    }

    @Test
    fun `navigate back, retry, play all are singletons`() {
        assertEquals(ArtistAction.NavigateBack, ArtistAction.NavigateBack)
        assertEquals(ArtistAction.Retry, ArtistAction.Retry)
        assertEquals(ArtistAction.PlayAll, ArtistAction.PlayAll)
    }
}
