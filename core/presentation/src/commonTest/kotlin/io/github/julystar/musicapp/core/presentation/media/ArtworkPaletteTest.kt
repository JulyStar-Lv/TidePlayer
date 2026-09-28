package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArtworkPaletteTest {

    @Test
    fun `cache reuses palettes and evicts the oldest artwork`() {
        val cache = ArtworkPaletteCache(maxEntries = 2)
        val first = Artwork.LibraryTrack(1)
        val second = Artwork.LibraryTrack(2)
        val third = Artwork.LibraryTrack(3)
        val firstPalette = palette(0xFF6D5860)
        val refreshedFirstPalette = palette(0xFF795E69)
        val secondPalette = palette(0xFF28364D)
        val thirdPalette = palette(0xFF51434A)

        cache.put(first, firstPalette)
        cache.put(second, secondPalette)
        cache.put(first, refreshedFirstPalette)
        cache.put(third, thirdPalette)

        assertEquals(refreshedFirstPalette, cache.get(first))
        assertNull(cache.get(second))
        assertEquals(thirdPalette, cache.get(third))
    }

    @Test
    fun `background texture preserves regional colors rather than averaging complementary hues`() {
        val cover = ImageBitmap(128, 128)
        val canvas = Canvas(cover)
        canvas.drawRect(Rect(0f, 0f, 64f, 128f), Paint().apply { color = Color.Red })
        canvas.drawRect(Rect(64f, 0f, 128f, 128f), Paint().apply { color = Color.Blue })
        val texture = assertNotNull(extractPaletteFromBitmap(cover).backgroundTexture)
        assertEquals(64, texture.width)
        assertEquals(64, texture.height)
        val pixels = texture.toPixelMap()
        assertTrue(pixels[8, 32].red > 0.95f && pixels[8, 32].blue < 0.05f)
        assertTrue(pixels[56, 32].blue > 0.95f && pixels[56, 32].red < 0.05f)
    }

    @Test
    fun `small covers still supply a background texture`() {
        val cover = ImageBitmap(8, 8)
        Canvas(cover).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Green })
        val texture = assertNotNull(extractPaletteFromBitmap(cover).backgroundTexture)
        assertTrue(texture.toPixelMap()[32, 32].green > 0.95f)
    }

    private fun palette(argb: Long): ArtworkPalette {
        val color = Color(argb)
        return ArtworkPalette(
            vibrant = color,
            muted = color,
            darkMuted = color,
        )
    }
}
