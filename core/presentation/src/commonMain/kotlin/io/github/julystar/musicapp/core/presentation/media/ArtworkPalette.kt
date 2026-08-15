package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Palette colors extracted from artwork for dynamic backgrounds.
 */
data class ArtworkPalette(
    val vibrant: Color,
    val muted: Color,
    val darkMuted: Color,
) {
    companion object {
        val Default = ArtworkPalette(
            vibrant = Color(0xFF1A1A2E),
            muted = Color(0xFF16213E),
            darkMuted = Color(0xFF0F3460),
        )
    }
}

internal class ArtworkPaletteCache(
    private val maxEntries: Int = 48,
) {
    private val values = LinkedHashMap<Artwork, ArtworkPalette>()

    fun get(artwork: Artwork): ArtworkPalette? = values[artwork]

    fun put(artwork: Artwork, palette: ArtworkPalette) {
        values.remove(artwork)
        values[artwork] = palette
        while (values.size > maxEntries) {
            values.remove(values.keys.first())
        }
    }
}

private val artworkPaletteCache = ArtworkPaletteCache()

/**
 * Extracts a simple color palette from an artwork bitmap by sampling key regions.
 *
 * Samples three horizontal bands (top, middle, bottom) computing per-band averages.
 * Produces a top-band dark, a middle-band vibrant, and a bottom-band muted color
 * suitable for gradient backgrounds.
 */
internal fun extractPaletteFromBitmap(bitmap: ImageBitmap, sampleSize: Int = 16): ArtworkPalette {
    val width = bitmap.width
    val height = bitmap.height

    if (width < sampleSize || height < sampleSize) {
        return ArtworkPalette.Default
    }

    val stepX = width / sampleSize
    val stepY = height / sampleSize

    var topR = 0L; var topG = 0L; var topB = 0L; var topCount = 0
    var midR = 0L; var midG = 0L; var midB = 0L; var midCount = 0
    var botR = 0L; var botG = 0L; var botB = 0L; var botCount = 0

    val bandHeight = height / 3
    val pixel = IntArray(1)

    for (y in 0 until sampleSize) {
        val sy = minOf(y * stepY, height - 1)
        for (x in 0 until sampleSize) {
            val sx = minOf(x * stepX, width - 1)
            bitmap.readPixels(
                buffer = pixel,
                startX = sx,
                startY = sy,
                width = 1,
                height = 1,
                bufferOffset = 0,
                stride = 1,
            )
            val color = pixel[0]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            when {
                sy < bandHeight -> {
                    topR += r; topG += g; topB += b; topCount++
                }
                sy < bandHeight * 2 -> {
                    midR += r; midG += g; midB += b; midCount++
                }
                else -> {
                    botR += r; botG += g; botB += b; botCount++
                }
            }
        }
    }

    fun average(count: Int, r: Long, g: Long, b: Long): Color {
        if (count == 0) return Color.Unspecified
        val inv = 1.0 / count
        return Color(
            (r * inv).toInt().coerceIn(0, 255),
            (g * inv).toInt().coerceIn(0, 255),
            (b * inv).toInt().coerceIn(0, 255),
        )
    }

    val vibrant = average(midCount, midR, midG, midB).let { c ->
        if (c == Color.Unspecified) ArtworkPalette.Default.vibrant else c
    }
    val muted = average(botCount, botR, botG, botB).let { c ->
        if (c == Color.Unspecified) ArtworkPalette.Default.muted else c
    }
    val darkMuted = average(topCount, topR, topG, topB).let { c ->
        if (c == Color.Unspecified) ArtworkPalette.Default.darkMuted else c
    }

    return ArtworkPalette(
        vibrant = vibrant,
        muted = muted.copy(alpha = 0.85f),
        darkMuted = darkMuted.copy(alpha = 0.95f),
    )
}

/**
 * Remembers and loads an [ArtworkPalette] from the given [artwork].
 * Uses the [ArtworkImageLoader] to load the bitmap, then extracts dominant colors.
 */
@Composable
fun rememberArtworkPalette(artwork: Artwork?): ArtworkPalette {
    val loader = koinInject<ArtworkImageLoader>()
    var palette by remember(artwork) {
        mutableStateOf(
            artwork?.let(artworkPaletteCache::get) ?: ArtworkPalette.Default,
        )
    }

    LaunchedEffect(artwork) {
        if (artwork == null) {
            palette = ArtworkPalette.Default
            return@LaunchedEffect
        }
        artworkPaletteCache.get(artwork)?.let { cachedPalette ->
            palette = cachedPalette
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.Default) {
            loader.cachedBitmap(artwork) ?: loader.loadBitmap(artwork)
        }
        if (bitmap != null) {
            val extractedPalette = extractPaletteFromBitmap(bitmap)
            artworkPaletteCache.put(artwork, extractedPalette)
            palette = extractedPalette
        } else {
            palette = ArtworkPalette.Default
        }
    }

    return palette
}
