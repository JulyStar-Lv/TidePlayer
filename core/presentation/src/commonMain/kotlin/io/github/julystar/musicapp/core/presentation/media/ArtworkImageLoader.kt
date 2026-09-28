package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.core.domain.model.Artwork

interface ArtworkImageLoader {
    val revision: kotlinx.coroutines.flow.StateFlow<Long>? get() = null

    fun cachedBitmap(artwork: Artwork): ImageBitmap?

    suspend fun loadBitmap(artwork: Artwork): ImageBitmap?
}
