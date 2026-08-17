package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingReactiveTest {
    @Test
    fun visualizationRequiresEnabledSettingAndBackground() {
        assertFalse(shouldRequestVisualization(settingEnabled = false, drawBackground = false))
        assertFalse(shouldRequestVisualization(settingEnabled = false, drawBackground = true))
        assertFalse(shouldRequestVisualization(settingEnabled = true, drawBackground = false))
        assertTrue(shouldRequestVisualization(settingEnabled = true, drawBackground = true))
    }

    @Test
    fun disabledScaleKeepsOriginalBaseScale() {
        assertEquals(
            2.90f,
            resolveReactiveScale(2.90f, level = 1f, beat = 1f, enabled = false),
        )
    }

    @Test
    fun reactiveScaleIsFiniteClampedAndOrdered() {
        val quiet = resolveReactiveScale(2.90f, level = 0f, beat = 0f, enabled = true)
        val beat = resolveReactiveScale(2.90f, level = 0f, beat = 1f, enabled = true)
        val loud = resolveReactiveScale(2.90f, level = 1f, beat = 1f, enabled = true)
        val invalid = resolveReactiveScale(
            baseScale = Float.NaN,
            level = Float.POSITIVE_INFINITY,
            beat = -2f,
            enabled = true,
        )

        assertTrue(quiet < beat)
        assertTrue(beat < loud)
        assertTrue(loud <= 2.90f * 1.05f)
        assertEquals(0f, invalid)
    }

    @Test
    fun blobExpansionSanitizesAndRespondsToBeat() {
        val quiet = resolveReactiveBlobExpansion(level = 0f, beat = 0f)
        val beat = resolveReactiveBlobExpansion(level = 0f, beat = 1f)
        val invalid = resolveReactiveBlobExpansion(
            level = Float.NaN,
            beat = Float.POSITIVE_INFINITY,
        )

        assertEquals(1f, quiet)
        assertTrue(beat > quiet)
        assertEquals(1f, invalid)
    }

    @Test
    fun smoothingUsesFastAttackAndPauseRelease() {
        assertEquals(
            60,
            resolveReactiveSmoothingDurationMillis(
                currentValue = 0f,
                targetValue = 1f,
                attackDurationMillis = 60,
                releaseDurationMillis = 240,
            ),
        )
        assertEquals(
            240,
            resolveReactiveSmoothingDurationMillis(
                currentValue = 1f,
                targetValue = 0f,
                attackDurationMillis = 60,
                releaseDurationMillis = 240,
            ),
        )
        assertEquals(
            420,
            resolveReactiveSmoothingDurationMillis(
                currentValue = 0.8f,
                targetValue = 0.4f,
                attackDurationMillis = 80,
                releaseDurationMillis = 420,
            ),
        )
        assertEquals(
            420,
            resolveReactiveSmoothingDurationMillis(
                currentValue = Float.NaN,
                targetValue = Float.NaN,
                attackDurationMillis = 80,
                releaseDurationMillis = 420,
            ),
        )
    }
}
