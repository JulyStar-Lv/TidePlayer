package io.github.julystar.musicapp.feature.playlist.presentation

import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesPlaylistStateTest {

    @Test
    fun `favorite tracks map to playlist detail state`() {
        val state = listOf(
            LibraryTrackItem(
                id = 7L,
                title = "First",
                artist = "Artist",
                durationMs = 60_000L,
            ),
            LibraryTrackItem(
                id = 9L,
                title = "Second",
                artist = null,
                durationMs = 30_000L,
            ),
        ).toFavoritesPlaylistState("My Favorites")

        assertEquals(LIBRARY_PLAYBACK_PLAYLIST_ID, state.playlistId)
        assertEquals("My Favorites", state.title)
        assertTrue(state.isFavorites)
        assertEquals(90_000L, state.durationMs)
        assertEquals(listOf(7L, 9L), state.tracks.map { track -> track.id })
        assertEquals(listOf(0L, 1L), state.tracks.map { track -> track.sortOrder })
    }
}
