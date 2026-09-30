package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.desktopWindowBackgroundColor
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
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
class DesktopPlaylistRenderTest {
    @Test
    fun coverIndicatorAnimatesOnlyDuringPlaybackAndFollowsCurrentTrack() = runDesktopComposeUiTest(width = 1544, height = 1200) {
        startArtworkLoader()
        try {
            var currentTrackId by mutableStateOf<Long?>(1L)
            var isPlaying by mutableStateOf(true)
            val state = PlaylistState(playlistId = 10, title = "Demo", tracks = persistentListOf(
                PlaylistTrackItem(1, "Lover", "Taylor Swift", "Lover", 1, 221_000L, 0, null),
                PlaylistTrackItem(2, "我怀念的", "孙燕姿", "逆光", 2, 181_000L, 1, null),
            ))
            mainClock.autoAdvance = false
            setContent {
                AppTheme(darkTheme = false, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Box(Modifier.fillMaxSize().background(desktopWindowBackgroundColor()).testTag("playlist")) {
                            PlaylistScreen(state, currentTrackId, emptySet(), PaddingValues(), {}, {}, isPlaying = isPlaying)
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(32)
            waitForIdle()
            val indicator = onNodeWithTag("desktop-playlist-playing-1", useUnmergedTree = true)
            indicator.assertIsDisplayed()
            onNodeWithTag("desktop-playlist-playing-2", useUnmergedTree = true).assertDoesNotExist()
            fun indicatorPixels(): IntArray {
                val image = indicator.captureToImage()
                return IntArray(image.width * image.height).also { image.readPixels(it) }
            }
            val firstFrame = indicatorPixels()
            mainClock.advanceTimeBy(320)
            assertTrue(!firstFrame.contentEquals(indicatorPixels()), "Playing bars must change between frames")
            val image = onNodeWithTag("playlist").captureToImage()
            val output = File("build/reports/playlists/playlist-playing.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            repeat(8) { frame ->
                val motionFrame = indicator.captureToImage()
                File(output.parentFile, "playlist-bars-$frame.png").writeBytes(
                    Image.makeFromBitmap(motionFrame.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
                mainClock.advanceTimeBy(160)
            }

            runOnIdle { isPlaying = false }
            mainClock.advanceTimeBy(32)
            indicator.assertDoesNotExist()
            mainClock.advanceTimeBy(320)
            indicator.assertDoesNotExist()
            runOnIdle { isPlaying = true }
            mainClock.advanceTimeBy(32)
            val resumedFrame = indicatorPixels()
            mainClock.advanceTimeBy(320)
            assertTrue(!resumedFrame.contentEquals(indicatorPixels()), "Resumed playback must animate again")
            onNodeWithText("Taylor Swift").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(32)
            indicator.assertIsDisplayed()
            runOnIdle { currentTrackId = 2L }
            mainClock.advanceTimeBy(32)
            indicator.assertDoesNotExist()
            onNodeWithTag("desktop-playlist-playing-2", useUnmergedTree = true).assertIsDisplayed()
            runOnIdle { currentTrackId = null }
            mainClock.advanceTimeBy(32)
            onNodeWithTag("desktop-playlist-playing-2", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            stopKoin()
        }
    }

    @Test
    fun ordinaryPlaylistSupportsDesktopColumnsAndActions() = runDesktopComposeUiTest(width = 1544, height = 1200) {
        startArtworkLoader()
        try {
            val actions = mutableListOf<PlaylistAction>()
            var shuffled = false
            val state = PlaylistState(
                playlistId = 10,
                title = "Demo",
                cover = Artwork.LibraryPlaylist(10),
                durationMs = 402_000L,
                tracks = persistentListOf(
                    PlaylistTrackItem(1, "Lover", "Taylor Swift", "Z Album", 1, 221_000L, 0, null),
                    PlaylistTrackItem(2, "我怀念的", "孙燕姿", "A Album", 2, 181_000L, 1, null),
                ),
            )
            setContent {
                AppTheme(darkTheme = false, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Box(Modifier.fillMaxSize().background(desktopWindowBackgroundColor()).testTag("playlist")) {
                            PlaylistScreen(state, null, emptySet(), PaddingValues(), {}, { actions += it },
                                onShuffle = { shuffled = true })
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("desktop-playlist-hero-cover").assertIsDisplayed()
            onAllNodesWithText("Demo").assertCountEquals(1)
            onNodeWithText("专辑").assertIsDisplayed()
            val title = onNodeWithText("Lover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val artist = onNodeWithText("Taylor Swift", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val album = onNodeWithText("Z Album", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(title.left < artist.left && artist.left < album.left)
            assertTrue(kotlin.math.abs(title.center.y - album.center.y) < 4f)
            onNodeWithText("Lover").performMouseInput { moveTo(center) }
            waitForIdle()
            val image = onNodeWithTag("playlist").captureToImage()
            val output = File("build/reports/playlists/playlist-light.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)

            onNodeWithTag("desktop-playlist-track-1").performTouchInput {
                down(Offset(144f, center.y))
            }
            mainClock.advanceTimeBy(32)
            onNodeWithTag("desktop-playlist-track-1").performTouchInput {
                moveTo(Offset(144f, center.y + 30f))
            }
            mainClock.advanceTimeBy(32)
            onNodeWithTag("desktop-playlist-track-1").performTouchInput {
                moveTo(Offset(144f, center.y + height))
            }
            mainClock.advanceTimeBy(32)
            onNodeWithTag("desktop-playlist-track-1").performTouchInput {
                up()
            }
            waitForIdle()
            assertTrue(actions.any { it == PlaylistAction.MoveTrack(0, 1) })

            onNodeWithText("播放").performClick()
            assertEquals(PlaylistAction.PlayAll, actions.last())
            onNodeWithText("随机播放").performClick()
            assertTrue(shuffled)
            onNodeWithContentDescription("编辑").performClick()
            assertEquals(PlaylistAction.EditPlaylist, actions.last())
            onNodeWithContentDescription("返回").performClick()
            assertEquals(PlaylistAction.NavigateBack, actions.last())
            onNodeWithContentDescription("在播放列表中查找").performTextInput("A Album")
            onNodeWithText("Lover").assertDoesNotExist()
            onNodeWithText("我怀念的").assertIsDisplayed()
            onNodeWithContentDescription("在播放列表中查找").performTextClearance()
            onNodeWithText("专辑").performClick()
            assertTrue(onNodeWithText("我怀念的", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top <
                onNodeWithText("Lover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top)
            onNodeWithContentDescription("Lover 的更多操作").performClick()
            onNodeWithText("移除").performClick()
            assertEquals(PlaylistAction.RemoveTrack(1), actions.last())
            onNodeWithContentDescription("Demo 的更多操作").performClick()
            onNodeWithText("导入歌曲").performClick()
            assertEquals(PlaylistAction.ImportTracks, actions.last())
            onNodeWithContentDescription("Demo 的更多操作").performClick()
            onNodeWithText("移除").performClick()
            assertEquals(PlaylistAction.OpenRemoveDialog, actions.last())
        } finally {
            stopKoin()
        }
    }

    @Test
    fun emptyPlaylistKeepsImportAvailableAndDisablesPlayback() = runDesktopComposeUiTest(width = 1544, height = 1200) {
        startArtworkLoader()
        try {
            val actions = mutableListOf<PlaylistAction>()
            setContent {
                AppTheme(darkTheme = true, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Box(Modifier.fillMaxSize().background(desktopWindowBackgroundColor()).testTag("playlist")) {
                            PlaylistScreen(PlaylistState(playlistId = 10, title = "Empty"), null, emptySet(),
                                PaddingValues(), {}, { actions += it }, showDesktopBackButton = false)
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithContentDescription("返回").assertDoesNotExist()
            onNodeWithText("播放").assertIsNotEnabled()
            onNodeWithText("随机播放").assertIsNotEnabled()
            val image = onNodeWithTag("playlist").captureToImage()
            val output = File("build/reports/playlists/playlist-dark-empty.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            onNodeWithContentDescription("Empty 的更多操作").performClick()
            onNodeWithText("导入歌曲").performClick()
            assertEquals(PlaylistAction.ImportTracks, actions.last())
        } finally {
            stopKoin()
        }
    }

    private fun startArtworkLoader() {
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
    }
}
