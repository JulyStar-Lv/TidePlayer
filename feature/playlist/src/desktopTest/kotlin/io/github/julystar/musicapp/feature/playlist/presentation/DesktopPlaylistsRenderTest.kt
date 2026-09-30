package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopPlaylistsRenderTest {
    @Test
    fun keepsFavoritesCoverWhiteInDarkGrid() = runDesktopComposeUiTest(width = 900, height = 700) {
        setContent {
            AppTheme(darkTheme = true, manageSystemBars = false) {
                CompositionLocalProvider(LocalDensity provides Density(2f)) {
                    DesktopPlaylistsScreen(
                        state = PlaylistsListState(),
                        favoriteCount = 0,
                        onOpenPlaylist = {},
                        onOpenFavorites = {},
                        onCreatePlaylist = {},
                        onPlayPlaylist = {},
                        onPlayFavorites = {},
                    )
                }
            }
        }
        val cover = onNodeWithTag("desktop-favorites-cover").captureToImage()
        val pixel = cover.toPixelMap()[20, 20]
        assertTrue(pixel.red > 0.98f && pixel.green > 0.98f && pixel.blue > 0.98f)
        val output = File("build/reports/playlists/favorites-cover-dark.png")
        output.parentFile.mkdirs()
        output.writeBytes(Image.makeFromBitmap(cover.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    @Test
    fun rendersTheGridAndSupportsSearchSortingAndOpeningFavorites() = runDesktopComposeUiTest(width = 1544, height = 1200) {
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
            var openedFavorites = 0
            setContent {
                AppTheme(darkTheme = false, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f), LocalDesktopWindowFocused provides true) {
                        Box(Modifier.fillMaxSize().testTag("playlists")) {
                            DesktopPlaylistsScreen(
                                state = PlaylistsListState(
                                    playlists = persistentListOf(
                                        PlaylistListItem(1, "Demo", "1", "3:00", null, 100),
                                        PlaylistListItem(2, "Demo2", "1", "3:00", null, 200),
                                    ),
                                    isEmpty = false,
                                ),
                                favoriteCount = 1,
                                onOpenPlaylist = {},
                                onOpenFavorites = { openedFavorites++ },
                                onCreatePlaylist = {},
                                onPlayPlaylist = {},
                                onPlayFavorites = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val coverPixel = onNodeWithTag("desktop-favorites-cover").captureToImage().toPixelMap()[20, 20]
            assertTrue(coverPixel.red > 0.98f && coverPixel.green > 0.98f && coverPixel.blue > 0.98f)
            val image = onNodeWithTag("playlists").captureToImage()
            assertEquals(1544, image.width)
            assertEquals(1200, image.height)
            val output = File("build/reports/playlists/title-grid.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)

            onNodeWithContentDescription("在所有播放列表中查找").performTextInput("不存在的歌单")
            onNodeWithText("无结果").assertIsDisplayed()
            onNodeWithContentDescription("在所有播放列表中查找").performTextClearance()
            onNodeWithContentDescription("喜爱歌曲").performTouchInput { doubleClick() }
            assertEquals(1, openedFavorites)
            onNodeWithContentDescription("排序选项").performClick()
            onNodeWithText("排序选项").performClick()
            onNodeWithText("最近添加").assertIsDisplayed().performClick()
            onNodeWithContentDescription("排序选项").performClick()
            onNodeWithText("排序选项").performClick()
            onNodeWithText("降序").performClick()
            assertTrue(onNodeWithText("Demo2").fetchSemanticsNode().boundsInRoot.left <
                onNodeWithText("Demo").fetchSemanticsNode().boundsInRoot.left)
            onNodeWithContentDescription("排序选项").performClick()
            val menuImage = onNodeWithTag("playlists").captureToImage()
            File(output.parentFile, "sort-menu.png").writeBytes(
                Image.makeFromBitmap(menuImage.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes,
            )
        } finally {
            stopKoin()
        }
    }
}
