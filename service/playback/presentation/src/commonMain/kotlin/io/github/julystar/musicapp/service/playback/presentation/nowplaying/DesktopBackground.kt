package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.lerp
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Layered artwork and blur, following the approach used by AMLL's Pixi renderer. */
@Composable
internal fun DesktopBackground(palette: ArtworkPalette) {
    if (palette.backgroundTexture != null) {
        Crossfade(
            targetState = palette.backgroundTexture,
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
            animationSpec = tween(900),
            label = "desktop-background-artwork",
        ) { texture ->
            if (texture != null) LayeredArtworkBackground(texture)
        }
        return
    }
    val targets = remember(palette) { desktopBackgroundColors(palette) }
    val colors = targets.mapIndexed { index, color ->
        animateColorAsState(color, tween(900), label = "desktop-background-color-$index")
    }
    val transition = rememberInfiniteTransition(label = "desktop-background-drift")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "desktop-background-phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val t = phase.value
        val base = lerp(colors[3].value, colors[5].value, 0.5f)
        drawRect(base)
        // Large overlapping color fields retain spatial hue variation without a fixed warm cast.
        for (index in colors.indices) {
            val column = index % 3
            val row = index / 3
            val center = Offset(
                size.width * (0.08f + column * 0.42f + sin(t + index * 1.7f) * 0.07f),
                size.height * (0.06f + row * 0.44f + cos(t + index * 1.1f) * 0.09f),
            )
            val color = colors[index].value
            val radius = maxOf(size.width, size.height) * 0.56f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to color.copy(alpha = 0.93f),
                        0.38f to color.copy(alpha = 0.62f),
                        1f to color.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = radius,
                ),
                center = center,
                radius = radius,
            )
        }
        // Contrast shaping is neutral, so blue and monochrome covers remain blue and neutral.
        val shadeCenter = Offset(size.width * 0.56f, size.height * 0.44f)
        val shadeRadius = size.width * 0.63f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.19f), Color.Transparent),
                center = shadeCenter,
                radius = shadeRadius,
            ),
            center = shadeCenter,
            radius = shadeRadius,
        )
    }
}

@Composable
internal fun LayeredArtworkBackground(texture: ImageBitmap, phaseOverride: Float? = null) {
    val transition = rememberInfiniteTransition(label = "desktop-artwork-flow")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "desktop-artwork-phase",
    )
    val filter = remember {
        val matrix = ColorMatrix().apply { setToSaturation(1.6f) }
        // Neutral brightness reduction preserves cool and monochrome artwork.
        for (row in 0..2) for (column in 0..4) matrix[row, column] *= 0.46f
        ColorFilter.colorMatrix(matrix)
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        // Render at quarter resolution; overscan keeps blurred edges outside the window.
        val blurRadius = minOf(maxWidth, maxHeight) * 0.05f
        val bleed = blurRadius * 3f
        Canvas(
            Modifier.requiredSize(maxWidth / 4f + bleed * 2f, maxHeight / 4f + bleed * 2f)
                .graphicsLayer { scaleX = 4f; scaleY = 4f }
                .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        ) {
            drawLayeredArtwork(texture, phaseOverride ?: phase.value, filter)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLayeredArtwork(
    texture: ImageBitmap,
    phase: Float,
    filter: ColorFilter,
) {
    drawRect(Color(0xFF121212))
    val extent = maxOf(size.width, size.height)
    val factors = floatArrayOf(1.5f, 0.8f, 0.5f, 0.25f)
    val speeds = floatArrayOf(1f, -2f, 1f, -1f)
    for (index in factors.indices) {
        val orbit = if (index >= 2) extent * 0.22f else 0f
        val center = Offset(
            size.width / 2f + cos(phase + index * 1.7f) * orbit,
            size.height / 2f + sin(phase + index * 1.7f) * orbit,
        )
        val side = (extent * factors[index]).toInt()
        rotate(phase * (180f / PI.toFloat()) * speeds[index] + index * 67f, center) {
            drawImage(
                image = texture,
                dstOffset = IntOffset((center.x - side / 2f).toInt(), (center.y - side / 2f).toInt()),
                dstSize = IntSize(side, side),
                colorFilter = filter,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

internal fun desktopBackgroundColors(palette: ArtworkPalette): List<Color> {
    val sources = palette.flowingLightColors.takeIf { it.size == 9 } ?: listOf(
        palette.darkMuted, palette.vibrant, palette.darkMuted,
        palette.muted, palette.vibrant, palette.muted,
        palette.darkMuted, palette.muted, palette.darkMuted,
    )
    return sources.map { color ->
        val peak = maxOf(color.red, color.green, color.blue)
        if (peak < 0.001f) {
            Color(0.055f, 0.055f, 0.055f)
        } else {
            // Compress highlights without lifting shadows into a gray veil. Apply chroma
            // shaping relative to the region's peak so grayscale and cool covers stay neutral/cool.
            // Lift darker artwork regions more than highlights while retaining their channel ratios.
            val value = (peak.pow(0.85f) * 0.54f + 0.035f).coerceIn(0.055f, 0.36f)
            Color(
                red = value * (color.red / peak).pow(1.18f),
                green = value * (color.green / peak).pow(1.18f),
                blue = value * (color.blue / peak).pow(1.18f),
            )
        }
    }
}
