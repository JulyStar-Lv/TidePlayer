package io.github.julystar.musicapp.car.presentation.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.car.presentation.theme.LocalCarTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun CarArtwork(
    artwork: Artwork?,
    repository: ArtworkRepository,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    fillBounds: Boolean = false,
) {
    var bitmap by remember(artwork) {
        mutableStateOf(artwork?.let(decodedArtworkCache::get))
    }
    LaunchedEffect(artwork) {
        val target = artwork
        if (target == null) {
            bitmap = null
            return@LaunchedEffect
        }
        decodedArtworkCache.get(target)?.let { cached ->
            bitmap = cached
            return@LaunchedEffect
        }
        val decoded = try {
            withContext(Dispatchers.IO) {
                val bytes = repository.cached(target) ?: repository.load(target)
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (decoded != null) {
            decodedArtworkCache.put(target, decoded)
        }
        bitmap = decoded
    }
    val colors = LocalCarColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (fillBounds) Modifier.fillMaxSize() else Modifier.size(size))
            .clip(shape)
            .background(colors.surfaceContainerHighest),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: BasicText(
            text = "♪",
            style = LocalCarTypography.current.title.copy(color = colors.textSummary),
        )
    }
}

internal class CarArtworkBitmapCache<T>(
    private val maxBytes: Long,
    private val sizeOf: (T) -> Long,
) {
    private data class Entry<T>(
        val value: T,
        val sizeBytes: Long,
    )

    private val values = LinkedHashMap<Artwork, Entry<T>>()
    private var sizeBytes = 0L

    init {
        require(maxBytes > 0L)
    }

    @Synchronized
    fun get(artwork: Artwork): T? {
        val entry = values.remove(artwork) ?: return null
        values[artwork] = entry
        return entry.value
    }

    @Synchronized
    fun put(artwork: Artwork, value: T) {
        values.remove(artwork)?.let { sizeBytes -= it.sizeBytes }
        val entrySizeBytes = sizeOf(value)
        if (entrySizeBytes > maxBytes) return

        values[artwork] = Entry(value, entrySizeBytes)
        sizeBytes += entrySizeBytes
        while (sizeBytes > maxBytes) {
            val oldestArtwork = values.keys.first()
            sizeBytes -= values.remove(oldestArtwork)?.sizeBytes ?: 0L
        }
    }
}

private val decodedArtworkCache = CarArtworkBitmapCache<ImageBitmap>(
    maxBytes = 48L * 1024L * 1024L,
    sizeOf = { bitmap -> bitmap.width.toLong() * bitmap.height.toLong() * 4L },
)
