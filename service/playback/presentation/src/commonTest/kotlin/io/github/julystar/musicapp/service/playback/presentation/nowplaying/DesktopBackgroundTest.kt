package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopBackgroundTest {
    @Test
    fun monochromeArtworkStaysNeutralIncludingBlackAndWhite() {
        for (level in listOf(0f, 0.15f, 0.5f, 1f)) {
            val gray = Color(level, level, level)
            desktopBackgroundColors(ArtworkPalette(gray, gray, gray)).forEach { color ->
                assertEquals(color.red, color.green, 0.001f)
                assertEquals(color.green, color.blue, 0.001f)
                assertEquals(1f, color.alpha)
                assertTrue(color.red <= 0.401f)
            }
        }
    }

    @Test
    fun regionalHuesSurviveDarkeningWithoutAUniversalWarmTint() {
        val red = Color(0.85f, 0.12f, 0.08f)
        val blue = Color(0.08f, 0.25f, 0.85f)
        val gray = Color(0.4f, 0.4f, 0.4f)
        val colors = desktopBackgroundColors(
            ArtworkPalette(red, blue, gray, listOf(red, blue, gray, red, blue, gray, red, blue, gray)),
        )
        assertEquals(9, colors.size)
        assertTrue(colors[0].red > colors[0].blue * 2f)
        assertTrue(colors[1].blue > colors[1].red * 2f)
        assertEquals(colors[2].red, colors[2].blue, 0.001f)
    }

    @Test
    fun midtoneArtworkDoesNotAcquireABrightGrayVeil() {
        val brown = Color(0.52f, 0.37f, 0.29f)
        val colors = desktopBackgroundColors(ArtworkPalette(brown, brown, brown))
        colors.forEach { color ->
            assertTrue(color.red in 0.33f..0.35f)
            assertTrue(color.green in 0.22f..0.24f)
            assertTrue(color.blue in 0.16f..0.18f)
        }
    }

    @Test
    fun shadowMappingIsContinuousAndHighlightsRemainBounded() {
        val values = (0..1000).map { index ->
            val gray = Color(index / 1000f, index / 1000f, index / 1000f)
            desktopBackgroundColors(ArtworkPalette(gray, gray, gray)).first().red
        }
        // Compose's sRGB Color quantizes each channel to eight bits.
        val channelStep = 1f / 255f
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a && b - a <= channelStep + 0.0001f })
        assertTrue(values.all { it in 0.054f..0.361f })
    }

    @Test
    fun anIncompleteSpatialPaletteStillProducesNineOpaqueRegions() {
        val gray = Color.Gray
        for (count in listOf(0, 1, 8, 10)) {
            val colors = desktopBackgroundColors(ArtworkPalette(gray, gray, gray, List(count) { Color.Red }))
            assertEquals(9, colors.size)
            colors.forEach {
                assertEquals(1f, it.alpha)
                assertEquals(it.red, it.blue, 0.001f)
            }
        }
    }
}
