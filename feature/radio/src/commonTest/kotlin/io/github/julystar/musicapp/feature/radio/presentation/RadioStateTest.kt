package io.github.julystar.musicapp.feature.radio.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RadioStateTest {

    @Test
    fun `default state is loading with empty tracks`() {
        val state = RadioState()

        assertTrue(state.isLoading)
        assertEquals(persistentListOf(), state.tracks)
        assertEquals(null, state.error)
    }

    @Test
    fun `loaded state carries track data`() {
        val tracks = persistentListOf(
            RadioTrackItem(
                id = 1, title = "Track 1", artist = "A", albumName = "Album",
                durationMs = 240_000, mediaId = null, canDownload = false,
            ),
        )
        val state = RadioState(isLoading = false, tracks = tracks)

        assertFalse(state.isLoading)
        assertEquals(1, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].title)
    }

    @Test
    fun `error state preserves previous tracks`() {
        val tracks = persistentListOf(RadioTrackItem(1, "T", null, null, null, null, false))
        val state = RadioState(isLoading = false, tracks = tracks, error = UiMessage.Text("boom"))

        assertEquals(UiMessage.Text("boom"), state.error)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `play track action carries track id`() {
        val action = RadioAction.PlayTrack(42)
        assertEquals(42, action.trackId)
    }

    @Test
    fun `download track action carries track item`() {
        val item = RadioTrackItem(9, "DL", "Me", null, 1000, null, true)
        val action = RadioAction.DownloadTrack(item)

        assertEquals("DL", action.track.title)
        assertTrue(action.track.canDownload)
    }

    @Test
    fun `row keys stay unique when track ids repeat`() {
        val track = RadioTrackItem(42, "T", null, null, null, null, false)

        assertNotEquals(track.lazyListKey(0), track.copy(title = "T2").lazyListKey(1))
    }

    @Test
    fun `refresh and play all are singletons`() {
        assertEquals(RadioAction.Refresh, RadioAction.Refresh)
        assertEquals(RadioAction.PlayAll, RadioAction.PlayAll)
    }
}
