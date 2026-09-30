package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class DesktopCreatePlaylistDialogTest {
    @Test
    fun showsCoverArrowsOnHoverAndCyclesPatterns() = runDesktopComposeUiTest(width = 958, height = 1100) {
        setContent {
            AppTheme(darkTheme = true, manageSystemBars = false) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    CreatePlaylistScreen(CreatePlaylistState(isOpen = true), onAction = {})
                }
            }
        }
        val dialog = onNodeWithTag("desktop-create-playlist-dialog")
        onNodeWithContentDescription("下一个封面").assertDoesNotExist()
        dialog.performMouseInput { moveTo(Offset(324f, 270f)) }
        onNodeWithContentDescription("上一个封面").assertIsDisplayed()
        onNodeWithContentDescription("下一个封面").assertIsDisplayed()
        repeat(9) { onNodeWithContentDescription("下一个封面").performClick() }
        onNodeWithContentDescription("封面图案 1").assertIsSelected()
        onNodeWithContentDescription("上一个封面").performClick()
        onNodeWithContentDescription("封面图案 9").assertIsSelected()
        repeat(3) { onNodeWithContentDescription("上一个封面").performClick() }
        onNodeWithContentDescription("封面图案 6").assertIsSelected()
        val image = dialog.captureToImage()
        val output = File("build/reports/playlists/create-playlist-hover.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithContentDescription("播放列表标题").performMouseInput { moveTo(center) }
        onNodeWithContentDescription("下一个封面").assertDoesNotExist()
    }

    @Test
    fun keepsFullActionButtonsVisibleInDesktopWindow() = verifyActionButtons(1960, 1200, "desktop")

    @Test
    fun keepsFullActionButtonsVisibleInShortWindow() = verifyActionButtons(1280, 760, "short")

    private fun verifyActionButtons(width: Int, height: Int, label: String) = runDesktopComposeUiTest(width = width, height = height) {
        val actions = mutableListOf<CreatePlaylistAction>()
        setContent {
            AppTheme(darkTheme = true, manageSystemBars = false) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    CreatePlaylistScreen(
                        state = CreatePlaylistState(isOpen = true, name = "新歌单"),
                        onAction = { actions += it },
                    )
                }
            }
        }
        val dialog = onNodeWithTag("desktop-create-playlist-dialog")
        val dialogBounds = dialog.fetchSemanticsNode().boundsInRoot
        for (text in listOf("取消", "创建")) {
            val button = onNodeWithText(text)
            val bounds = button.fetchSemanticsNode().boundsInRoot
            assertEquals(56f, bounds.height, 1f, "$text must show the entire 28 dp pill")
            assertTrue(bounds.bottom <= dialogBounds.bottom - 24f, "$text must keep its bottom margin")
        }
        val image = dialog.captureToImage()
        val output = File("build/reports/playlists/create-playlist-$label.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        onNodeWithText("创建").performClick()
        assertNotNull(assertIs<CreatePlaylistAction.SubmitWithCover>(actions.last()).coverImage)
        onNodeWithText("取消").performClick()
        assertEquals(CreatePlaylistAction.Close, actions.last())
    }

    @Test
    fun rendersAppleMusicStyleDialogAndCreatesNamedPlaylist() = runDesktopComposeUiTest(width = 958, height = 1100) {
        var name by mutableStateOf("")
        val actions = mutableListOf<CreatePlaylistAction>()
        setContent {
            AppTheme(darkTheme = true, manageSystemBars = false) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    CreatePlaylistScreen(
                        state = CreatePlaylistState(isOpen = true, name = name),
                        onAction = { action ->
                            if (action is CreatePlaylistAction.UpdateName) name = action.name
                            actions += action
                        },
                    )
                }
            }
        }

        val dialog = onNodeWithTag("desktop-create-playlist-dialog")
        dialog.assertIsDisplayed()
        onNodeWithText("新建播放列表").assertIsDisplayed()
        onNodeWithText("创建").assertIsNotEnabled()
        val image = dialog.captureToImage()
        val output = File("build/reports/playlists/create-playlist.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)

        onNodeWithContentDescription("播放列表标题").performTextInput("新歌单")
        onNodeWithContentDescription("描述（可选）").performTextInput("夜间聆听")
        onNodeWithContentDescription("封面图案 6").performClick()
        onNodeWithText("创建").assertIsEnabled()
        onNodeWithText("创建").performClick()
        assertEquals("新歌单", name)
        assertNotNull(assertIs<CreatePlaylistAction.SubmitWithCover>(actions.last()).coverImage)
        onNodeWithText("取消").performClick()
        assertEquals(CreatePlaylistAction.Close, actions.last())
    }
}
