package io.github.julystar.musicapp.service.playback.domain

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackUiActionTest {
    private val items = listOf(
        playable(id = 10, title = "One"),
        playable(id = 20, title = "Two"),
        playable(id = 30, title = "Three"),
    )

    @Test
    fun `play all keeps the complete queue and starts at zero`() {
        assertEquals(PlaybackUiRequest(items, 0), playbackUiRequest(items))
    }

    @Test
    fun `play track keeps the complete queue and selects its index`() {
        assertEquals(PlaybackUiRequest(items, 1), playbackUiRequest(items, selectedTrackId = 20))
    }

    @Test
    fun `empty queue and unknown track do not launch playback`() {
        assertNull(playbackUiRequest(emptyList()))
        assertNull(playbackUiRequest(items, selectedTrackId = 999))
    }

    @Test
    fun `cancellation is propagated without user failure`() = runTest {
        var failed = false
        val job = launchPlaybackUiAction(onFailure = { failed = true }) {
            delay(Long.MAX_VALUE)
        }

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertTrue(!failed)
    }

    @Test
    fun `playback failure reaches the lifecycle scoped failure handler`() = runTest {
        var failure: Throwable? = null

        launchPlaybackUiAction(onFailure = { failure = it }) {
            error("playback failed")
        }
        advanceUntilIdle()

        assertEquals("playback failed", failure?.message)
    }

    private fun playable(id: Long, title: String) = PlayableItem(
        mediaId = MediaId(SourceId("library"), MediaType.Track, id.toString()),
        title = title,
        libraryTrackId = id,
    )
}
