package io.github.julystar.musicapp.core.data.media

import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.platform.byteArrayToImageBitmap

class RepositoryArtworkImageLoader(
    private val artworkRepository: ArtworkRepository,
) : ArtworkImageLoader {
    private val bitmapCache = HashMap<Artwork, ImageBitmap>()
    private val remoteArtwork = HashSet<Artwork>()

    override fun cachedBitmap(artwork: Artwork): ImageBitmap? {
        if (artwork in remoteArtwork) return null
        bitmapCache[artwork]?.let { return it }
        val bytes = artworkRepository.cached(artwork) ?: return null
        return bytes.toCachedBitmap(artwork)
    }

    override suspend fun loadBitmap(artwork: Artwork): ImageBitmap? {
        if ((artworkRepository as? RemoteArtworkCacheAware)?.isRemoteArtwork(artwork) == true) {
            remoteArtwork += artwork
            val bytes = artworkRepository.load(artwork) ?: return null
            return byteArrayToImageBitmap(bytes)
        }
        cachedBitmap(artwork)?.let { return it }
        val bytes = artworkRepository.load(artwork) ?: return null
        return bytes.toCachedBitmap(artwork)
    }

    private fun ByteArray.toCachedBitmap(artwork: Artwork): ImageBitmap? {
        val bitmap = byteArrayToImageBitmap(this) ?: return null
        bitmapCache[artwork] = bitmap
        return bitmap
    }
}
