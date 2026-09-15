package io.github.julystar.musicapp.car.presentation.component

import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class CarArtworkBitmapCacheTest {
    @Test
    fun recentlyReadArtworkSurvivesEviction() {
        val cache = CarArtworkBitmapCache<Any>(maxBytes = 8L, sizeOf = { 4L })
        val first = Any()
        val second = Any()
        val third = Any()
        val firstArtwork = Artwork.LibraryCover(1L)
        val secondArtwork = Artwork.LibraryCover(2L)
        val thirdArtwork = Artwork.LibraryCover(3L)

        cache.put(firstArtwork, first)
        cache.put(secondArtwork, second)
        assertSame(first, cache.get(firstArtwork))

        cache.put(thirdArtwork, third)

        assertSame(first, cache.get(firstArtwork))
        assertNull(cache.get(secondArtwork))
        assertSame(third, cache.get(thirdArtwork))
    }

    @Test
    fun oversizedArtworkIsNotCached() {
        val cache = CarArtworkBitmapCache<Any>(maxBytes = 4L, sizeOf = { 8L })
        val artwork = Artwork.LibraryCover(1L)

        cache.put(artwork, Any())

        assertNull(cache.get(artwork))
    }
}
