package io.github.julystar.musicapp.core.lyrics.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterpolatedPlaybackPositionTest {
    @Test
    fun ignoresSmallClockJitter() {
        assertEquals(
            expected = 1_000.0,
            actual = correctInterpolatedPlaybackPosition(
                externalPositionMs = 980.0,
                renderedPositionMs = 1_000.0,
            ),
        )
    }

    @Test
    fun easesMediumClockDrift() {
        assertEquals(
            expected = 1_025.0,
            actual = correctInterpolatedPlaybackPosition(
                externalPositionMs = 1_100.0,
                renderedPositionMs = 1_000.0,
            ),
        )
    }

    @Test
    fun snapsAfterSeekOrLargeDrift() {
        assertEquals(
            expected = 2_000.0,
            actual = correctInterpolatedPlaybackPosition(
                externalPositionMs = 2_000.0,
                renderedPositionMs = 1_000.0,
            ),
        )
    }

    @Test
    fun snapsAfterBackwardSeek() {
        assertEquals(
            expected = 1_000.0,
            actual = correctInterpolatedPlaybackPosition(
                externalPositionMs = 1_000.0,
                renderedPositionMs = 20_000.0,
            ),
        )
    }

    @Test
    fun snapsLyricsScrollAfterSeekAcrossLines() {
        assertEquals(true, shouldSnapLyricsScroll(previousIndex = 8, currentIndex = 21))
        assertEquals(true, shouldSnapLyricsScroll(previousIndex = 21, currentIndex = 4))
    }

    @Test
    fun keepsAnimationForNormalAdjacentLineChanges() {
        assertEquals(false, shouldSnapLyricsScroll(previousIndex = 8, currentIndex = 9))
    }

    @Test
    fun keepsPreviousLineAsTheActiveLyricScrollAnchor() {
        assertEquals(0, lyricsScrollTargetIndex(currentIndex = 0, contextLinesBeforeActive = 1))
        assertEquals(0, lyricsScrollTargetIndex(currentIndex = 1, contextLinesBeforeActive = 1))
        assertEquals(8, lyricsScrollTargetIndex(currentIndex = 9, contextLinesBeforeActive = 1))
    }

    @Test
    fun lightsPlaceholderDotsSequentiallyAcrossTimeline() {
        assertEquals(listOf(0f, 0f, 0f), placeholderProgress(positionMs = 1_000))
        assertEquals(listOf(1f, 0f, 0f), placeholderProgress(positionMs = 2_000))
        assertEquals(listOf(1f, 0.5f, 0f), placeholderProgress(positionMs = 2_500))
        assertEquals(listOf(1f, 1f, 1f), placeholderProgress(positionMs = 4_000))
    }

    @Test
    fun clampsPlaceholderDotProgressOutsideTimeline() {
        assertEquals(listOf(0f, 0f, 0f), placeholderProgress(positionMs = 0))
        assertEquals(listOf(1f, 1f, 1f), placeholderProgress(positionMs = 5_000))
    }

    @Test
    fun breathesPlaceholderDotsOnlyDuringTheirTimeline() {
        assertEquals(1f, placeholderBreathingScale(positionMs = 0))
        assertEquals(1f, placeholderBreathingScale(positionMs = 4_000))

        val activeScales = (1_000 until 4_000 step 250).map(::placeholderBreathingScale)
        assertTrue(activeScales.all { scale -> scale in 0.82f..1f })
        assertTrue(activeScales.min() < activeScales.max())
    }

    private fun placeholderProgress(positionMs: Int): List<Float> =
        List(3) { dotIndex ->
            lyricPlaceholderDotProgress(
                positionMs = positionMs,
                startMs = 1_000,
                endMs = 4_000,
                dotIndex = dotIndex,
            )
        }

    private fun placeholderBreathingScale(positionMs: Int): Float =
        lyricPlaceholderBreathingScale(
            positionMs = positionMs,
            startMs = 1_000,
            endMs = 4_000,
        )
}
