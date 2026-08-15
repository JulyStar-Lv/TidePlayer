package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun palette(argb: Long): ArtworkPalette {
        val color = Color(argb)
        return ArtworkPalette(
            vibrant = color,
            muted = color,
            darkMuted = color,
        )
    }
}
