package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NowPlayingCreatePlaylistDialogRenderTest {
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
