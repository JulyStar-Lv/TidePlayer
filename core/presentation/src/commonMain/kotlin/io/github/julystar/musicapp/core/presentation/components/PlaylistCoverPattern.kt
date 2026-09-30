package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette

internal val DefaultPlaylistCoverPalette = ArtworkPalette(
    vibrant = Color(0xFF96D877),
    muted = Color(0xFFC8B76C),
    darkMuted = Color(0xFF362621),
    flowingLightColors = listOf(Color(0xFF96D877), Color(0xFFA52919), Color(0xFFC8B76C)),
)

/** Resolution independent patterns, colored from the same palette as the player background. */
@Composable
internal fun PlaylistCoverPattern(index: Int, palette: ArtworkPalette, modifier: Modifier = Modifier) {
    val colors = remember(palette) { playlistCoverColors(palette) }
    Canvas(modifier) { drawPlaylistCoverPattern(index, colors) }
}

internal fun DrawScope.drawPlaylistCoverPattern(index: Int, colors: List<Color>) {
    val light = colors[0]
    val main = colors[1]
    val accent = colors[2]
    val dark = colors[3]
    val w = size.width
    val h = size.height
    clipRect {
        drawRect(Brush.linearGradient(listOf(light, accent, dark), Offset.Zero, Offset(w, h)))
        when (index) {
            0 -> drawRect(Brush.sweepGradient(listOf(dark, main, light, accent, dark, main, light, dark), center))
            1 -> drawRect(Brush.linearGradient(listOf(light, main, accent, dark), Offset(w * 0.2f, 0f), Offset(w * 0.8f, h)))
            2 -> drawCircle(Brush.radialGradient(listOf(main, dark, accent, light),
                center = Offset(w * 0.9f, h * 0.4f), radius = w * 0.9f),
                radius = w * 0.9f, center = Offset(w * 0.9f, h * 0.4f))
            3 -> repeat(8) { band ->
                val x = band * w / 8
                drawRect(Brush.verticalGradient(listOf(if (band % 2 == 0) light else accent, dark)),
                    topLeft = Offset(x, 0f), size = Size(w / 8 + 1, h))
            }
            4 -> repeat(6) { band ->
                val y = h * (band - 1) / 5
                val ribbon = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.35f, y - h * 0.25f, w * 0.65f, y + h * 0.35f, w, y)
                    lineTo(w, y + h * 0.3f)
                    cubicTo(w * 0.65f, y + h * 0.6f, w * 0.35f, y + h * 0.1f, 0f, y + h * 0.3f)
                    close()
                }
                drawPath(ribbon, Brush.linearGradient(listOf(if (band % 2 == 0) light else accent, dark)))
            }
            5 -> rotate(-45f) {
                drawRect(Brush.sweepGradient(
                    0f to light, 0.24f to main, 0.25f to dark, 0.49f to accent,
                    0.5f to light, 0.74f to main, 0.75f to dark, 1f to light,
                    center = center,
                ), topLeft = Offset(-w, -h), size = Size(w * 3, h * 3))
            }
            6 -> repeat(5) { ring ->
                val radius = w * (5 - ring) / 6
                drawCircle(Brush.radialGradient(listOf(if (ring % 2 == 0) light else accent, dark),
                    center = Offset(w * 0.65f, h * 0.65f), radius = radius),
                    radius = radius, center = Offset(w * 0.65f, h * 0.65f))
            }
            7 -> rotate(45f) {
                repeat(5) { square ->
                    val side = w * (1.8f - square * 0.3f)
                    drawRect(Brush.linearGradient(listOf(if (square % 2 == 0) light else accent, dark)),
                        topLeft = center - Offset(side / 2, side / 2), size = Size(side, side))
                }
            }
            8 -> repeat(4) { row ->
                repeat(4) { column ->
                    drawRect(Brush.linearGradient(listOf(if ((row + column) % 2 == 0) light else accent, dark)),
                        topLeft = Offset(column * w / 4, row * h / 4), size = Size(w / 4 + 1, h / 4 + 1))
                }
            }
        }
    }
}

internal fun playlistCoverColors(palette: ArtworkPalette): List<Color> {
    val samples = palette.flowingLightColors.ifEmpty { listOf(palette.vibrant, palette.muted, palette.darkMuted) }
    val main = samples.maxBy { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) }
    val contrast = samples.maxBy {
        val r = it.red - main.red
        val g = it.green - main.green
        val b = it.blue - main.blue
        r * r + g * g + b * b
    }
    return listOf(lerp(main.copy(alpha = 1f), Color.White, 0.35f), main.copy(alpha = 1f),
        contrast.copy(alpha = 1f), lerp(contrast.copy(alpha = 1f), Color.Black, 0.65f))
}

internal expect fun encodePlaylistCover(index: Int, palette: ArtworkPalette): ByteArray
