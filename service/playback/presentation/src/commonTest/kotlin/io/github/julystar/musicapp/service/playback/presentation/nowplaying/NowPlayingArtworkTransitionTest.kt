package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NowPlayingArtworkTransitionTest {

    @Test
    fun `paused compact artwork uses its reduced shared element target size`() {
        val playingTargetSize = compactArtworkTargetSize(isPlaying = true)
        val pausedTargetSize = compactArtworkTargetSize(isPlaying = false)

        assertEquals(356.dp, playingTargetSize)
        assertEquals(356.dp * 0.96f, pausedTargetSize)
        assertTrue(pausedTargetSize < playingTargetSize)
    }
}
