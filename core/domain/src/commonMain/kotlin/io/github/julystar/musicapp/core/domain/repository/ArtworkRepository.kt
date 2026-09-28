package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey

interface ArtworkRepository {
    val revision: kotlinx.coroutines.flow.StateFlow<Long>? get() = null

    fun invalidate() {}

    fun cached(artwork: Artwork): ByteArray?

    suspend fun cacheKey(artwork: Artwork): ArtworkCacheKey?

    suspend fun load(artwork: Artwork): ByteArray?
}

interface RemoteArtworkCacheAware {
    suspend fun isRemoteArtwork(artwork: Artwork): Boolean
}
