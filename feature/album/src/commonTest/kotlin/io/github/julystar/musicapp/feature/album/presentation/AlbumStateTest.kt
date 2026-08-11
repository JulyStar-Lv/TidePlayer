package io.github.julystar.musicapp.feature.album.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumStateTest {

    @Test
    fun `default state is loading with empty tracks`() {
        val state = AlbumState()

        assertTrue(state.isLoading)
        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertEquals(persistentListOf(), state.tracks)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries album data`() {
        val tracks = persistentListOf(
            AlbumTrackItem(id = 1, title = "Track 1", trackNumber = 1, discNumber = 1, durationMs = 240_000, mediaId = null, canDownload = false),
        )
        val state = AlbumState(isLoading = false, title = "Album", artist = "Artist", tracks = tracks)

        assertFalse(state.isLoading)
        assertEquals("Album", state.title)
        assertEquals("Artist", state.artist)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `error state preserves album metadata`() {
        val state = AlbumState(
            isLoading = false,
            title = "Album",
            artist = "Artist",
            error = UiMessage.Text("Not found"),
        )

        assertEquals("Album", state.title)
        assertEquals(UiMessage.Text("Not found"), state.error)
    }

    @Test
    fun `play track action carries track id`() {
        val action = AlbumAction.PlayTrack(42)
        assertEquals(42, action.trackId)
    }

    @Test
    fun `navigate back, retry, play all are singletons`() {
        assertEquals(AlbumAction.NavigateBack, AlbumAction.NavigateBack)
        assertEquals(AlbumAction.Retry, AlbumAction.Retry)
        assertEquals(AlbumAction.PlayAll, AlbumAction.PlayAll)
    }
}
