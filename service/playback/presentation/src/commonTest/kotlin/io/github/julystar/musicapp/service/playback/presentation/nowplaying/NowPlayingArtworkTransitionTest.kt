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

    @Test
    fun `immersive lyrics align with classic lyrics across viewport widths`() {
        listOf(360.dp, (1080f / 2.75f).dp, 420.dp).forEach { viewportWidth ->
            val classicLyricsStart = viewportWidth * 0.06f + 8.dp
            val immersiveLyricsStart = 28.dp + immersiveLyricsLineHorizontalPadding(viewportWidth)

            assertEquals(
                classicLyricsStart.value,
                immersiveLyricsStart.value,
                absoluteTolerance = 0.001f,
            )
        }
    }

    @Test
    fun `status bar uses light icons only while player covers its top edge`() {
        assertTrue(
            doesPlayerCoverStatusBar(
                playerTopInWindowPx = 0f,
                dragOffsetPx = 0f,
                statusBarBottomInWindowPx = 96f,
            ),
        )
        assertTrue(
            doesPlayerCoverStatusBar(
                playerTopInWindowPx = -120f,
                dragOffsetPx = 80f,
                statusBarBottomInWindowPx = 96f,
            ),
        )
        assertTrue(
            doesPlayerCoverStatusBar(
                playerTopInWindowPx = 0f,
                dragOffsetPx = 95f,
                statusBarBottomInWindowPx = 96f,
            ),
        )
        assertTrue(
            !doesPlayerCoverStatusBar(
                playerTopInWindowPx = 0f,
                dragOffsetPx = 96f,
                statusBarBottomInWindowPx = 96f,
            ),
        )
    }

    @Test
    fun `status bar falls back to the app theme when player does not cover it`() {
        assertTrue(
            shouldUseLightStatusBarIcons(
                playerCoversStatusBar = true,
                appUsesDarkTheme = false,
            ),
        )
        assertTrue(
            !shouldUseLightStatusBarIcons(
                playerCoversStatusBar = false,
                appUsesDarkTheme = false,
            ),
        )
        assertTrue(
            shouldUseLightStatusBarIcons(
                playerCoversStatusBar = false,
                appUsesDarkTheme = true,
            ),
        )
    }
}
