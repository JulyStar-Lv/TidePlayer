package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette
import org.jetbrains.compose.resources.imageResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.sample_cover_1
import musicapp.core.presentation.generated.resources.sample_cover_2
import musicapp.core.presentation.generated.resources.sample_cover_3
import musicapp.core.presentation.generated.resources.sample_cover_4
import musicapp.core.presentation.generated.resources.sample_cover_5
import musicapp.core.presentation.generated.resources.sample_cover_6
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppleMusicArtworkBackgroundRenderTest {
    @Test
    fun layeredArtworkPreservesHuesFlowsAndHasNoHardEdges() = runComposeUiTest {
        val texture = ImageBitmap(64, 64)
        val canvas = Canvas(texture)
        val colors = listOf(Color(0xFFE64924), Color(0xFF244ECC), Color(0xFF843199), Color(0xFFE9A830))
        for (index in colors.indices) {
            val x = (index % 2) * 32f
            val y = (index / 2) * 32f
            canvas.drawRect(Rect(x, y, x + 32f, y + 32f), Paint().apply { color = colors[index] })
        }
        val phase = mutableFloatStateOf(0f)
        setContent {
            Box(Modifier.size(480.dp, 300.dp).testTag("background")) {
                LayeredArtworkBackground(texture, phase.floatValue)
            }
        }
        waitForIdle()
        val first = onNodeWithTag("background").captureToImage()
        savePreview(first, "tide-artwork-background-0.png")
        runOnIdle { phase.floatValue = 1.1f }
        waitForIdle()
        val second = onNodeWithTag("background").captureToImage()
        savePreview(second, "tide-artwork-background-1.png")
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        var warm = 0
        var cool = 0
        var movement = 0f
        var largestStep = 0f
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val color = a[x, y]
            assertTrue(color.alpha > 0.99f, "Background must cover the viewport, including corners")
            if (color.red > color.blue + 0.08f) warm++
            if (color.blue > color.red + 0.08f) cool++
            movement += abs(color.red - b[x, y].red) + abs(color.blue - b[x, y].blue)
            if (x > 0) largestStep = maxOf(largestStep, abs(color.red - a[x - 1, y].red))
            if (y > 0) largestStep = maxOf(largestStep, abs(color.red - a[x, y - 1].red))
        }
        assertTrue(warm > 100 && cool > 100, "Cover's warm and cool regions should survive blending")
        assertTrue(movement / (a.width * a.height) > 0.01f, "Artwork layers should flow")
        assertTrue(largestStep < 0.08f, "Blur should hide the edges of artwork copies")
    }

    @Test
    fun monochromeCoverDoesNotAcquireAColorCastOrBrightWhiteBackground() = runComposeUiTest {
        val texture = ImageBitmap(64, 64)
        val canvas = Canvas(texture)
        canvas.drawRect(Rect(0f, 0f, 64f, 64f), Paint().apply { color = Color.White })
        canvas.drawRect(Rect(0f, 0f, 32f, 64f), Paint().apply { color = Color.Black })
        setContent {
            Box(Modifier.size(480.dp, 300.dp).testTag("background")) {
                LayeredArtworkBackground(texture, 0f)
            }
        }
        waitForIdle()
        val image = onNodeWithTag("background").captureToImage()
        savePreview(image, "tide-artwork-background-monochrome.png")
        val pixels = image.toPixelMap()
        for (y in 0 until pixels.height step 5) for (x in 0 until pixels.width step 5) {
            val color = pixels[x, y]
            assertTrue(abs(color.red - color.blue) < 0.01f && abs(color.red - color.green) < 0.01f)
            assertTrue(color.red < 0.60f)
        }
    }

    @Test
    fun crossfadeNeverRevealsTheUnderlyingPage() = runComposeUiTest {
        fun texture(color: Color) = ImageBitmap(64, 64).also {
            Canvas(it).drawRect(Rect(0f, 0f, 64f, 64f), Paint().apply { this.color = color })
        }
        val palette = mutableStateOf(ArtworkPalette(
            Color.Gray, Color.Gray, Color.Gray, backgroundTexture = texture(Color.Gray),
        ))
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(480.dp, 300.dp).background(Color.Magenta).testTag("background")) {
                AppleMusicDesktopBackground(palette.value)
            }
        }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        runOnIdle { palette.value = palette.value.copy(backgroundTexture = texture(Color.White)) }
        for (frame in 0..10) {
            mainClock.advanceTimeBy(90)
            waitForIdle()
            val pixels = onNodeWithTag("background").captureToImage().toPixelMap()
            for (y in 0 until pixels.height step 20) for (x in 0 until pixels.width step 20) {
                val color = pixels[x, y]
                assertTrue(abs(color.red - color.green) < 0.01f, "Underlying magenta leaked at frame $frame")
                assertTrue(abs(color.blue - color.green) < 0.01f, "Underlying magenta leaked at frame $frame")
            }
        }
    }

    @Test
    fun transparentCoverStillHasAnOpaqueBackground() = runComposeUiTest {
        val texture = ImageBitmap(64, 64)
        Canvas(texture).drawRect(Rect(24f, 24f, 40f, 40f), Paint().apply { color = Color.Red })
        setContent {
            Box(Modifier.size(480.dp, 300.dp).testTag("background")) {
                LayeredArtworkBackground(texture, 0f)
            }
        }
        waitForIdle()
        val pixels = onNodeWithTag("background").captureToImage().toPixelMap()
        for (y in 0 until pixels.height step 10) for (x in 0 until pixels.width step 10) {
            assertTrue(pixels[x, y].alpha > 0.99f)
        }
    }

    @Test
    fun bundledAlbumCoversRenderWithoutMissingEdges() = runComposeUiTest {
        val index = mutableIntStateOf(0)
        val covers = listOf(
            CoreRes.drawable.sample_cover_1, CoreRes.drawable.sample_cover_2,
            CoreRes.drawable.sample_cover_3, CoreRes.drawable.sample_cover_4,
            CoreRes.drawable.sample_cover_5, CoreRes.drawable.sample_cover_6,
        )
        setContent {
            val texture = imageResource(covers[index.intValue])
            Box(Modifier.size(480.dp, 300.dp).testTag("background")) {
                LayeredArtworkBackground(texture, 0.5f)
            }
        }
        for (cover in covers.indices) {
            runOnIdle { index.intValue = cover }
            waitForIdle()
            val image = onNodeWithTag("background").captureToImage()
            savePreview(image, "tide-artwork-background-cover-${cover + 1}.png")
            val pixels = image.toPixelMap()
            for (y in 0 until pixels.height step 10) for (x in 0 until pixels.width step 10) {
                assertTrue(pixels[x, y].alpha > 0.99f)
            }
        }
    }

    private fun savePreview(bitmap: ImageBitmap, filename: String) {
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                File(System.getProperty("java.io.tmpdir"), filename).writeBytes(data.bytes)
            }
        }
    }
}
