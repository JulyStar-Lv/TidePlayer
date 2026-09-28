package io.github.julystar.musicapp.core.data.media

import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.domain.repository.RemoteArtworkCacheAware
import io.github.julystar.musicapp.platform.byteArrayToImageBitmap

class RepositoryArtworkImageLoader(
    private val artworkRepository: ArtworkRepository,
) : ArtworkImageLoader {
    override val revision get() = artworkRepository.revision
    private var cachedRevision = revision?.value

    private val bitmapCache = HashMap<Artwork, ImageBitmap>()
    private val remoteArtwork = HashSet<Artwork>()

    override fun cachedBitmap(artwork: Artwork): ImageBitmap? {
        val currentRevision = revision?.value
        if (cachedRevision != currentRevision) {
            bitmapCache.clear()
            remoteArtwork.clear()
            cachedRevision = currentRevision
        }
        if (artwork in remoteArtwork) return null
        bitmapCache[artwork]?.let { return it }
        val bytes = artworkRepository.cached(artwork) ?: return null
        return bytes.toCachedBitmap(artwork)
    }

    override suspend fun loadBitmap(artwork: Artwork): ImageBitmap? {
        val loadRevision = revision?.value
        if ((artworkRepository as? RemoteArtworkCacheAware)?.isRemoteArtwork(artwork) == true) {
            remoteArtwork += artwork
            val bytes = artworkRepository.load(artwork) ?: return null
            return byteArrayToImageBitmap(bytes)
        }
        cachedBitmap(artwork)?.let { return it }
        val bytes = artworkRepository.load(artwork) ?: return null
        if (revision?.value != loadRevision) return loadBitmap(artwork)
        return bytes.toCachedBitmap(artwork)
    }

    private fun ByteArray.toCachedBitmap(artwork: Artwork): ImageBitmap? {
        val bitmap = byteArrayToImageBitmap(this) ?: return null
        bitmapCache[artwork] = bitmap
        return bitmap
    }
}
