package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NowPlayingCapsuleHighlightTest {
    @Test
    fun capsuleRimsStayContinuousAtDifferentDisplayScales() {
        for (density in listOf(1f, 1.25f, 1.5f, 2f)) {
            for (background in listOf(Color(0xFF301A1A), Color(0xFF142F58))) {
                verifyCapsuleRims(density, background)
            }
        }
    }

    private fun verifyCapsuleRims(density: Float, background: Color) = runComposeUiTest {
        setContent {
            val windowInfo = LocalWindowInfo.current
            CompositionLocalProvider(
                LocalDensity provides Density(density),
                LocalWindowInfo provides object : WindowInfo by windowInfo {
                    override val isWindowFocused = true
                },
            ) {
                Row(
                    modifier = Modifier.background(background).padding(16.dp).testTag("preview"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (width in listOf(75, 180, 72, 36)) {
                        Box(
                            Modifier.size(width.dp, 36.dp)
                                .appleMusicDesktopCapsule(background)
                                .testTag("capsule-$width"),
                        )
                    }
                    Box(
                        Modifier.size(36.dp)
                            .appleMusicDesktopCapsule(Color.White)
                            .testTag("neutral-rim"),
                    )
                }
            }
        }
        waitForIdle()
        val preview = onNodeWithTag("preview").captureToImage()
        Image.makeFromBitmap(preview.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                File(System.getProperty("java.io.tmpdir"), "tide-capsule-highlight-preview-$density-${background.red}.png").writeBytes(data.bytes)
            }
        }
        for (width in listOf(75, 180, 72, 36)) {
            val pixels = onNodeWithTag("capsule-$width").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2].red
            val stroke = density.coerceAtLeast(1f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = pixels.width - stroke,
                        bottom = pixels.height - stroke,
                        cornerRadius = CornerRadius(18f * density - stroke / 2f),
                    ),
                )
            }
            val perimeter = PathMeasure().apply { setPath(path, false) }
            for (distance in 0..perimeter.length.toInt()) {
                val position = perimeter.getPosition(distance.toFloat())
                val x = (position.x + stroke / 2f).roundToInt()
                val y = (position.y + stroke / 2f).roundToInt()
                val rim = ((y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(pixels.height - 1)).maxOf { py ->
                    ((x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(pixels.width - 1)).maxOf { px ->
                        pixels[px, py].red
                    }
                }
                assertTrue(
                    rim > center + 0.04f,
                    "Broken rim on $width dp surface at density $density, perimeter distance $distance",
                )
            }
            if (width == 36) {
                val rimColor = pixels[pixels.width / 2, 0]
                val neutralPixels = onNodeWithTag("neutral-rim").captureToImage().toPixelMap()
                val neutralRim = neutralPixels[neutralPixels.width / 2, 0]
                val weakerChannel = if (background.red > background.blue) rimColor.blue else rimColor.red
                val neutralChannel = if (background.red > background.blue) neutralRim.blue else neutralRim.red
                assertTrue(weakerChannel < neutralChannel - 0.015f, "Rim should pick up the background tint")
            }
            val topLeft = pixels[pixels.width / 4, 0].red
            val topRight = pixels[3 * pixels.width / 4, 0].red
            if (width > 36) {
                assertTrue(topLeft > topRight + 0.03f, "Missing directional light on $width dp surface")
            }
        }
    }
}
