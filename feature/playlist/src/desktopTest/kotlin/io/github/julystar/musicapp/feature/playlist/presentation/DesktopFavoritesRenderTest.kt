package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.background
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import io.github.julystar.musicapp.core.presentation.components.desktopWindowBackgroundColor
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
class DesktopFavoritesRenderTest {
    @Test
    fun rendersFavoritesAndSupportsSearchAndActions() = runDesktopComposeUiTest(width = 1544, height = 1200) {
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
            val actions = mutableListOf<PlaylistAction>()
            var toggled = 0L
            val state = PlaylistState(
                title = "喜爱歌曲",
                isFavorites = true,
                durationMs = 402_000L,
                tracks = persistentListOf(
                    PlaylistTrackItem(1, "Lover", "Taylor Swift", "Lover", 1, 221_000L, 0,
                        MediaId(SourceId("test"), MediaType.Track, "test-media")),
                    PlaylistTrackItem(2, "我怀念的", "孙燕姿", "逆光", 2, 181_000L, 1, null),
                ),
            )
            setContent {
                AppTheme(darkTheme = true, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Box(Modifier.fillMaxSize().background(desktopWindowBackgroundColor()).testTag("favorites")) {
                            DesktopFavoritesScreen(
                                state = state,
                                currentPlayingTrackId = 1L,
                                isPlaying = true,
                                favoriteTrackIds = setOf(1, 2),
                                onToggleFavorite = { toggled = it },
                                onAction = { actions += it },
                                onShuffle = {},
                                onBack = { actions += PlaylistAction.NavigateBack },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("desktop-playlist-playing-1", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("desktop-playlist-playing-2", useUnmergedTree = true).assertDoesNotExist()
            val coverPixel = onNodeWithTag("desktop-favorites-hero-cover").captureToImage().toPixelMap()[20, 20]
            assertTrue(coverPixel.red > 0.98f && coverPixel.green > 0.98f && coverPixel.blue > 0.98f)
            val image = onNodeWithTag("favorites").captureToImage()
            val output = File("build/reports/playlists/favorites.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            onAllNodesWithText("Lover")[0].performMouseInput { moveTo(center) }
            waitForIdle()
            val hoverImage = onNodeWithTag("favorites").captureToImage()
            File("build/reports/playlists/favorites-hover.png")
                .writeBytes(Image.makeFromBitmap(hoverImage.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)

            onAllNodesWithText("喜爱歌曲").assertCountEquals(1)
            onNodeWithContentDescription("返回").performClick()
            assertEquals(PlaylistAction.NavigateBack, actions.last())
            onNodeWithText("我怀念的").assertIsDisplayed()
            onNodeWithContentDescription("在播放列表中查找").performTextInput("Lover")
            onNodeWithText("我怀念的").assertDoesNotExist()
            onNodeWithText("播放").performClick()
            assertEquals(PlaylistAction.PlayAll, actions.last())
            onNodeWithContentDescription("将 Lover 从收藏中移除").performClick()
            assertEquals(1L, toggled)
        } finally {
            stopKoin()
        }
    }
}
