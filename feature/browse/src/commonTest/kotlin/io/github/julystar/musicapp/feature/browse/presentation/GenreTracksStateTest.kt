package io.github.julystar.musicapp.feature.browse.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenreTracksStateTest {

    @Test
    fun `default state is loading with empty tracks`() {
        val state = GenreTracksState()

        assertTrue(state.isLoading)
        assertEquals("", state.genre)
        assertEquals(persistentListOf(), state.tracks)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries genre and tracks`() {
        val tracks = persistentListOf(GenreTrackItem(id = 1, title = "T", artist = "A", albumName = "B", durationMs = 1000, mediaId = null, canDownload = false))
        val state = GenreTracksState(isLoading = false, genre = "Rock", tracks = tracks)

        assertFalse(state.isLoading)
        assertEquals("Rock", state.genre)
        assertEquals(1, state.tracks.size)
        assertEquals("T", state.tracks[0].title)
    }

    @Test
    fun `error state preserves genre`() {
        val state = GenreTracksState(
            isLoading = false,
            genre = "Jazz",
            error = UiMessage.Text("Failed"),
        )

        assertEquals("Jazz", state.genre)
        assertEquals(UiMessage.Text("Failed"), state.error)
    }

    @Test
    fun `row keys stay unique when track ids repeat`() {
        val track = GenreTrackItem(42, "T", null, null, null, null, false)

        assertNotEquals(track.lazyListKey(0), track.copy(title = "T2").lazyListKey(1))
    }
}
