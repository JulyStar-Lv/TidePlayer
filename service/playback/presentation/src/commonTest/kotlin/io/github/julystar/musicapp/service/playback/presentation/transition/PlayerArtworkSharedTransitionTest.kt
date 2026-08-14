package io.github.julystar.musicapp.service.playback.presentation.transition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerArtworkSharedTransitionTest {

    @Test
    fun `artwork corner radius changes continuously between compact and expanded bounds`() {
        val compactRadius = playerArtworkCornerRadius(
            currentSize = 52f,
            compactSize = 52f,
            expandedSize = 356f,
            compactRadius = 13f,
            expandedRadius = 28f,
        )
        val middleRadius = playerArtworkCornerRadius(
            currentSize = 204f,
            compactSize = 52f,
            expandedSize = 356f,
            compactRadius = 13f,
            expandedRadius = 28f,
        )
        val expandedRadius = playerArtworkCornerRadius(
            currentSize = 356f,
            compactSize = 52f,
            expandedSize = 356f,
            compactRadius = 13f,
            expandedRadius = 28f,
        )

        assertEquals(13f, compactRadius)
        assertEquals(20.5f, middleRadius)
        assertEquals(28f, expandedRadius)
        assertTrue(compactRadius < middleRadius)
        assertTrue(middleRadius < expandedRadius)
    }
}
