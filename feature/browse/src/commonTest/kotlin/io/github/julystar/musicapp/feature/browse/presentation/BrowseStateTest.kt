package io.github.julystar.musicapp.feature.browse.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseStateTest {

    @Test
    fun `default state is loading with empty collections`() {
        val state = BrowseState()

        assertTrue(state.isLoading)
        assertEquals(persistentListOf(), state.albums)
        assertEquals(persistentListOf(), state.artists)
        assertEquals(persistentListOf(), state.genres)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries browse data`() {
        val albums = persistentListOf(BrowseAlbumItem(id = 1, name = "Album", year = 2024, artwork = null, trackCount = 12))
        val artists = persistentListOf(BrowseArtistItem(id = 1, name = "Artist", trackCount = 5))
        val genres = persistentListOf("Rock", "Jazz")
        val state = BrowseState(isLoading = false, albums = albums, artists = artists, genres = genres)

        assertFalse(state.isLoading)
        assertEquals(1, state.albums.size)
        assertEquals(1, state.artists.size)
        assertEquals(2, state.genres.size)
    }

    @Test
    fun `error state preserves collections`() {
        val state = BrowseState(
            isLoading = false,
            albums = persistentListOf(BrowseAlbumItem(1, "A", null, null, 1)),
            error = UiMessage.Text("err"),
        )

        assertEquals(UiMessage.Text("err"), state.error)
        assertEquals(1, state.albums.size)
    }

    @Test
    fun `navigate actions carry ids`() {
        assertEquals(5, (BrowseAction.NavigateToAlbum(5) as BrowseAction.NavigateToAlbum).albumId)
        assertEquals(3, (BrowseAction.NavigateToArtist(3) as BrowseAction.NavigateToArtist).artistId)
        assertEquals("Rock", (BrowseAction.NavigateToGenre("Rock") as BrowseAction.NavigateToGenre).genre)
    }

    @Test
    fun `row keys stay unique when album and artist ids repeat`() {
        val album = BrowseAlbumItem(id = 42, name = "Album", year = null, artwork = null, trackCount = 1)
        val artist = BrowseArtistItem(id = 42, name = "Artist", trackCount = 1)

        assertNotEquals(album.lazyListKey(0), album.copy(name = "Album 2").lazyListKey(1))
        assertNotEquals(artist.lazyListKey(0), artist.copy(name = "Artist 2").lazyListKey(1))
    }

    @Test
    fun `navigate back and retry are singletons`() {
        assertEquals(BrowseAction.NavigateBack, BrowseAction.NavigateBack)
        assertEquals(BrowseAction.Retry, BrowseAction.Retry)
    }
}
