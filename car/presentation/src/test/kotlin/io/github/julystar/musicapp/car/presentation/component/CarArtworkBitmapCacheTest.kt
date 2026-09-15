package io.github.julystar.musicapp.car.presentation.component

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
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

        cache.put(firstArtwork, cacheKey("first"), first)
        cache.put(secondArtwork, cacheKey("second"), second)
        assertSame(first, cache.get(firstArtwork, cacheKey("first")))

        cache.put(thirdArtwork, cacheKey("third"), third)

        assertSame(first, cache.get(firstArtwork, cacheKey("first")))
        assertNull(cache.get(secondArtwork, cacheKey("second")))
        assertSame(third, cache.get(thirdArtwork, cacheKey("third")))
    }

    @Test
    fun oversizedArtworkIsNotCached() {
        val cache = CarArtworkBitmapCache<Any>(maxBytes = 4L, sizeOf = { 8L })
        val artwork = Artwork.LibraryCover(1L)

        cache.put(artwork, cacheKey("large"), Any())

        assertNull(cache.get(artwork, cacheKey("large")))
    }

    @Test
    fun changedCacheKeyInvalidatesDecodedArtwork() {
        val cache = CarArtworkBitmapCache<Any>(maxBytes = 8L, sizeOf = { 4L })
        val artwork = Artwork.LibraryCover(1L)
        val decoded = Any()

        cache.put(artwork, cacheKey("old"), decoded)

        assertSame(decoded, cache.peek(artwork))
        assertNull(cache.get(artwork, cacheKey("new")))
        assertNull(cache.peek(artwork))
    }

    @Test
    fun artworkWithoutValidationKeyIsNotCached() {
        val cache = CarArtworkBitmapCache<Any>(maxBytes = 8L, sizeOf = { 4L })
        val artwork = Artwork.SourceMedia(
            MediaId(
                SourceId("source"),
                MediaType.Track,
                "track",
            ),
        )

        cache.put(artwork, null, Any())

        assertNull(cache.peek(artwork))
    }

    private fun cacheKey(hash: String) = ArtworkCacheKey(
        contentHash = hash,
        localPath = "/$hash",
        thumbnailPath = null,
        width = null,
        height = null,
        mimeType = null,
        pictureType = null,
    )
}
