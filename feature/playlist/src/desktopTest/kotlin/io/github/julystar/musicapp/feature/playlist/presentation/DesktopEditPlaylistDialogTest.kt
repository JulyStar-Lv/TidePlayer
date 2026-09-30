package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class DesktopEditPlaylistDialogTest {
    @Test
    fun reusesCreationDialogAndPreservesCoverUntilAnotherPatternIsChosen() = runDesktopComposeUiTest(width = 1960, height = 1200) {
        startKoin {
            modules(module {
                single<ArtworkImageLoader> {
                    object : ArtworkImageLoader {
                        override fun cachedBitmap(artwork: Artwork): ImageBitmap? = null
                        override suspend fun loadBitmap(artwork: Artwork): ImageBitmap? = null
                    }
                }
            })
        }
        try {
            var name by mutableStateOf("Demo")
            val actions = mutableListOf<EditPlaylistAction>()
            setContent {
                AppTheme(darkTheme = true, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        EditPlaylistScreen(
                            state = EditPlaylistState(isOpen = true, name = name,
                                coverArtwork = Artwork.LibraryPlaylist(10, revision = 1), canSubmit = true),
                            onAction = {
                                if (it is EditPlaylistAction.UpdateName) name = it.name
                                actions += it
                            },
                        )
                    }
                }
            }
            onNodeWithText("编辑播放列表").assertIsDisplayed()
            onNodeWithText("新建播放列表").assertDoesNotExist()
            onNodeWithContentDescription("播放列表标题").assertIsDisplayed()
            onNodeWithText("Demo").assertIsDisplayed()
            val image = onNodeWithTag("desktop-edit-playlist-dialog").captureToImage()
            val output = File("build/reports/playlists/edit-playlist.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            onNodeWithText("保存").performClick()
            assertNull(assertIs<EditPlaylistAction.SubmitWithCover>(actions.last()).coverImage)
            onNodeWithContentDescription("封面图案 6").performClick()
            onNodeWithText("保存").performClick()
            assertNotNull(assertIs<EditPlaylistAction.SubmitWithCover>(actions.last()).coverImage)
            onNodeWithContentDescription("播放列表标题").performTextClearance()
            onNodeWithText("保存").assertIsNotEnabled()
            onNodeWithContentDescription("播放列表标题").performTextInput("Renamed")
            assertEquals("Renamed", name)
            onNodeWithText("取消").performClick()
            assertEquals(EditPlaylistAction.Close, actions.last())
        } finally {
            stopKoin()
        }
    }
}
