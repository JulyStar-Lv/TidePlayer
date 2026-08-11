package io.github.julystar.musicapp.feature.recentlyplayed.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentlyPlayedStateTest {

    @Test
    fun `default state is loading with empty tracks`() {
        val state = RecentlyPlayedState()

        assertTrue(state.isLoading)
        assertEquals(persistentListOf(), state.tracks)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries track data`() {
        val tracks = persistentListOf(
            RecentlyPlayedTrackItem(
                id = 1, title = "Track 1", artist = "A", albumName = "Album",
                durationMs = 240_000, mediaId = null, canDownload = false,
            ),
        )
        val state = RecentlyPlayedState(isLoading = false, tracks = tracks)

        assertFalse(state.isLoading)
        assertEquals(1, state.tracks.size)
        assertEquals("Track 1", state.tracks[0].title)
    }

    @Test
    fun `error state preserves previous tracks`() {
        val tracks = persistentListOf(
            RecentlyPlayedTrackItem(1, "T", null, null, null, null, false),
        )
        val state = RecentlyPlayedState(
            isLoading = false,
            tracks = tracks,
            error = UiMessage.Text("boom"),
        )

        assertEquals(UiMessage.Text("boom"), state.error)
        assertEquals(1, state.tracks.size)
    }

    @Test
    fun `play track action carries track id`() {
        val action = RecentlyPlayedAction.PlayTrack(42)
        assertEquals(42, action.trackId)
    }

    @Test
    fun `row keys stay unique when track ids repeat`() {
        val track = RecentlyPlayedTrackItem(42, "T", null, null, null, null, false)

        assertNotEquals(track.lazyListKey(0), track.copy(title = "T2").lazyListKey(1))
    }

    @Test
    fun `play all and retry are singletons`() {
        assertEquals(RecentlyPlayedAction.PlayAll, RecentlyPlayedAction.PlayAll)
        assertEquals(RecentlyPlayedAction.Retry, RecentlyPlayedAction.Retry)
    }

    @Test
    fun `navigate back is a singleton`() {
        assertEquals(RecentlyPlayedAction.NavigateBack, RecentlyPlayedAction.NavigateBack)
    }
}
