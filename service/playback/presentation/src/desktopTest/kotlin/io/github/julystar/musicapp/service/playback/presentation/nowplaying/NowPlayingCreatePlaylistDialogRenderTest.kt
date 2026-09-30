package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.player_create_playlist
import musicapp.service.playback.presentation.generated.resources.player_playlist_name
import java.io.File
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NowPlayingCreatePlaylistDialogRenderTest {
    @Test
    fun recolorsGeneratedPatternsWhenCurrentArtworkChanges() = runDesktopComposeUiTest(width = 1280, height = 800) {
        fun bitmap(color: Color) = ImageBitmap(64, 64).also {
            Canvas(it).drawRect(0f, 0f, 64f, 64f, Paint().apply { this.color = color })
        }
        val blue = bitmap(Color.Blue)
        val red = bitmap(Color.Red)
        val firstArtwork = Artwork.LibraryTrack(99001)
        val nextArtwork = Artwork.LibraryTrack(99002)
        var currentArtwork by mutableStateOf<Artwork>(firstArtwork)
        startKoin {
            modules(module {
                single<ArtworkImageLoader> {
                    object : ArtworkImageLoader {
                        override fun cachedBitmap(artwork: Artwork): ImageBitmap = if (artwork == firstArtwork) blue else red
                        override suspend fun loadBitmap(artwork: Artwork): ImageBitmap = cachedBitmap(artwork)
                    }
                }
            })
        }
        try {
            setContent {
                MiuixTheme {
                    NowPlayingCreatePlaylistDialog(
                        show = true, name = "Palette Mix", currentTrackTitle = "Current track",
                        currentTrackArtwork = currentArtwork, onNameChange = {}, onCancel = {}, onCreate = {},
                    )
                }
            }
            val dialog = onNodeWithTag("now-playing-create-playlist-dialog")
            waitUntil(timeoutMillis = 5000) {
                val pixels = dialog.captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, 110]
                color.blue > color.red + 0.2f
            }
            runOnIdle { currentArtwork = nextArtwork }
            waitUntil(timeoutMillis = 5000) {
                val pixels = dialog.captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, 110]
                color.red > color.blue + 0.2f
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun fitsCompactWindow() = runDesktopComposeUiTest(width = 320, height = 568) {
        var createLabel = ""
        setContent {
            MiuixTheme {
                createLabel = stringResource(Res.string.player_create_playlist)
                NowPlayingCreatePlaylistDialog(
                    show = true,
                    name = "Compact Mix",
                    currentTrackTitle = "Current track",
                    currentTrackArtwork = null,
                    onNameChange = {},
                    onCancel = {},
                    onCreate = {},
                )
            }
        }
        onNodeWithText(createLabel).assertIsDisplayed()
    }

    @Test
    fun rendersWindowDialogAndAcceptsPlaylistTitle() = runDesktopComposeUiTest(width = 1280, height = 800) {
        var name by mutableStateOf("")
        var nameLabel = ""
        var createLabel = ""
        setContent {
            MiuixTheme {
                nameLabel = stringResource(Res.string.player_playlist_name)
                createLabel = stringResource(Res.string.player_create_playlist)
                Box(Modifier.fillMaxSize()) {
                    NowPlayingCreatePlaylistDialog(
                        show = true,
                        name = name,
                        currentTrackTitle = "Current track",
                        currentTrackArtwork = null,
                        onNameChange = { name = it },
                        onCancel = {},
                        onCreate = {},
                    )
                }
            }
        }

        onNodeWithTag("now-playing-create-playlist-dialog").assertIsDisplayed()
        onNodeWithText(createLabel).assertIsDisplayed()
        val image = onNodeWithTag("now-playing-create-playlist-dialog").captureToImage()
        val output = File("build/reports/now-playing-create-playlist-dialog.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)

        onNodeWithContentDescription(nameLabel).performTextInput("New Mix")
        assertEquals("New Mix", name)
    }
}
