package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.player_next_track
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NowPlayingOverlayInputTest {
    @Test
    fun blankSpaceNeverClicksThroughWhileTracksChangeButPlayerControlsStillWork() = runComposeUiTest {
        var underlyingClicks = 0
        var nextClicks = 0
        var dismissClicks = 0
        val viewport = mutableStateOf(980.dp to 600.dp)
        var nextLabel = ""
        val bitmap = ImageBitmap(64, 64)
        Canvas(bitmap).drawRect(Rect(0f, 0f, 64f, 64f), Paint().apply { color = Color.Blue })
        val loader = object : ArtworkImageLoader {
            override fun cachedBitmap(artwork: Artwork): ImageBitmap = bitmap
            override suspend fun loadBitmap(artwork: Artwork): ImageBitmap = bitmap
        }
        fun track(id: Long) = NowPlayingTrackItem(
            id = id, title = "Track $id", durationMs = 180_000,
            artwork = Artwork.LibraryTrack(id), mediaId = null,
        )
        val state = mutableStateOf(NowPlayingState(
            currentTrack = track(1), queue = NowPlayingQueueState(canPlayNext = true),
        ))
        setContent {
            KoinApplication(configuration = koinConfiguration { modules(module { single<ArtworkImageLoader> { loader } }) }) {
                MiuixTheme {
                    nextLabel = stringResource(Res.string.player_next_track)
                    Box(Modifier.size(viewport.value.first, viewport.value.second)) {
                        Box(Modifier.fillMaxSize().background(Color.Magenta).clickable { underlyingClicks++ })
                        NowPlayingScreen(
                            state = state.value,
                            currentPositionMs = 0,
                            progressContent = {}, compactProgressContent = {},
                            isFavorite = false, onToggleFavorite = {},
                            onAction = {
                                if (it == NowPlayingAction.PlayNext) nextClicks++
                                if (it == NowPlayingAction.NavigateBack) dismissClicks++
                            },
                            modifier = Modifier.testTag("player"),
                        )
                    }
                }
            }
        }
        waitForIdle()
        for ((index, current) in listOf(track(1), null, track(2), track(3).copy(artwork = null)).withIndex()) {
            runOnIdle { state.value = state.value.copy(currentTrack = current) }
            waitForIdle()
            onNodeWithTag("player").performTouchInput { click(Offset(10f, 100f)) }
            onNodeWithTag("player").performMouseInput { click(Offset(10f, 100f)) }
            assertEquals(0, underlyingClicks, "Blank space must stay modal while switching tracks")
            onNodeWithContentDescription(nextLabel).performTouchInput { click() }
            assertEquals(index + 1, nextClicks)
        }
        runOnIdle { viewport.value = 360.dp to 640.dp }
        waitForIdle()
        onNodeWithTag("player").performTouchInput { click(Offset(10f, 100f)) }
        assertEquals(0, underlyingClicks, "Compact player should also block the page underneath")
        onNodeWithTag("player").performTouchInput {
            swipe(start = Offset(10f, 100f), end = Offset(10f, 500f), durationMillis = 500)
        }
        waitForIdle()
        assertEquals(1, dismissClicks, "Blocking empty-space taps must preserve swipe-to-dismiss")
    }
}
