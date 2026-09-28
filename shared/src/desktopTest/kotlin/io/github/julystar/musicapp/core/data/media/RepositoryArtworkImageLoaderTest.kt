package io.github.julystar.musicapp.core.data.media

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RepositoryArtworkImageLoaderTest {
    @Test
    fun artworkRevisionReplacesDecodedBitmapForTheSameTrack() = runBlocking {
        val repository = object : ArtworkRepository {
            override val revision = MutableStateFlow(0L)
            var bytes = png(width = 1)
            override fun cached(artwork: Artwork): ByteArray = bytes
            override suspend fun cacheKey(artwork: Artwork): ArtworkCacheKey? = null
            override suspend fun load(artwork: Artwork): ByteArray = bytes
            override fun invalidate() { revision.value += 1 }
        }
        val loader = RepositoryArtworkImageLoader(repository)
        val artwork = Artwork.LibraryTrack(1)
        val first = requireNotNull(loader.loadBitmap(artwork))
        assertEquals(1, first.width)
        assertSame(first, loader.cachedBitmap(artwork))
        repository.bytes = png(width = 2)
        repository.invalidate()
        val replacement = requireNotNull(loader.cachedBitmap(artwork))
        assertEquals(2, replacement.width)
        assertSame(replacement, loader.loadBitmap(artwork))
    }

    private fun png(width: Int): ByteArray = ByteArrayOutputStream().use { stream ->
        ImageIO.write(BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB), "png", stream)
        stream.toByteArray()
    }
}
