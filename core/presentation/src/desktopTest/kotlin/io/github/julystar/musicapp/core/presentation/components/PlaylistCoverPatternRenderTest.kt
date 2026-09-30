package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlaylistCoverPatternRenderTest {
    @Test
    fun encodedCoverMatchesDisplayedPattern() = runDesktopComposeUiTest(width = 512, height = 512) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PlaylistCoverPattern(5, DefaultPlaylistCoverPalette, Modifier.size(512.dp).testTag("selected-cover"))
            }
        }
        val rendered = onNodeWithTag("selected-cover").captureToImage().toPixelMap()
        val encoded = Image.makeFromEncoded(encodePlaylistCover(5, DefaultPlaylistCoverPalette)).toComposeImageBitmap().toPixelMap()
        assertEquals(512, encoded.width)
        assertEquals(512, encoded.height)
        for (x in listOf(40, 150, 350, 450)) {
            for (y in listOf(40, 150, 350, 450)) {
                assertEquals(rendered[x, y], encoded[x, y])
            }
        }
    }

    @Test
    fun rendersAllProceduralCoverPatterns() = runDesktopComposeUiTest(width = 972, height = 972) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                Column(Modifier.testTag("cover-patterns")) {
                    repeat(3) { row ->
                        Row {
                            repeat(3) { column ->
                                PlaylistCoverPattern(row * 3 + column, DefaultPlaylistCoverPalette, Modifier.size(162.dp))
                            }
                        }
                    }
                }
            }
        }
        val image = onNodeWithTag("cover-patterns").captureToImage()
        val output = File("build/reports/playlist-cover-patterns.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
