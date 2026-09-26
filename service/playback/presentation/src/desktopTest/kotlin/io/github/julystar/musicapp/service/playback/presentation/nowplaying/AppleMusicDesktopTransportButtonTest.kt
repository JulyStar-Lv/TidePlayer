package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_next
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_pause
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_play
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_previous
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_repeat_one
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_shuffle
import org.jetbrains.compose.resources.DrawableResource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppleMusicDesktopTransportButtonTest {
    @Test
    fun animatedModeSymbolsPreserveTheStaticIconsAspectRatio() = runComposeUiTest {
        val motion = mutableStateOf(AppleMusicDesktopTransportMotion.Standard)
        setContent {
            MiuixTheme {
                AppleMusicDesktopTransportButton(
                    painter = Res.drawable.icon_apple_music_shuffle,
                    contentDescription = "mode", tint = Color.White,
                    iconSize = 60.dp, buttonWidth = 80.dp, onClick = {},
                    motion = motion.value, modifier = Modifier.size(80.dp).testTag("mode"),
                )
            }
        }
        waitForIdle()
        val reference = onNodeWithTag("mode").captureToImage()
        runOnIdle { motion.value = AppleMusicDesktopTransportMotion.Shuffle }
        waitForIdle()
        assertTrue(pixelDifference(reference, onNodeWithTag("mode").captureToImage()) < 0.0001f)
    }

    @Test
    fun allFiveControlsAnimateForAccessibilityClicksAndSettleWithoutDuplicateActions() = runComposeUiTest {
        val cases = listOf(
            Triple(Res.drawable.icon_apple_music_shuffle, AppleMusicDesktopTransportMotion.Shuffle, 0),
            Triple(Res.drawable.icon_apple_music_previous, AppleMusicDesktopTransportMotion.Seek, -1),
            Triple(Res.drawable.icon_apple_music_next, AppleMusicDesktopTransportMotion.Seek, 1),
            Triple(Res.drawable.icon_apple_music_play, AppleMusicDesktopTransportMotion.PlayPause, 0),
            Triple(Res.drawable.icon_apple_music_repeat_one, AppleMusicDesktopTransportMotion.Repeat, 0),
        )
        val current = mutableStateOf(cases.first())
        var clicks = 0
        setContent {
            MiuixTheme {
                Box(Modifier.size(80.dp).background(Color(0xFF493124))) {
                    AppleMusicDesktopTransportButton(
                        painter = current.value.first,
                        motion = current.value.second,
                        seekDirection = current.value.third,
                        contentDescription = "transport",
                        tint = Color.White,
                        iconSize = 52.dp,
                        buttonWidth = 80.dp,
                        onClick = { clicks++ },
                        modifier = Modifier.fillMaxSize().testTag("transport"),
                    )
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        for ((index, case) in cases.withIndex()) {
            runOnIdle { current.value = case }
            mainClock.advanceTimeBy(1_200)
            val node = onNodeWithTag("transport")
            val before = node.captureToImage()
            node.performClick()
            mainClock.advanceTimeBy(112)
            assertTrue(pixelDifference(before, node.captureToImage()) > 0.0001f, "No motion for $case")
            mainClock.advanceTimeBy(1_200)
            assertEquals(index + 1, clicks)
            assertTrue(pixelDifference(before, node.captureToImage()) < 0.0001f, "Did not settle for $case")
        }
    }

    @Test
    fun rapidPlayPauseReplacementLeavesTheLatestSymbolAndOneActionPerClick() = runComposeUiTest {
        val playing = mutableStateOf(false)
        var clicks = 0
        setContent {
            MiuixTheme {
                Box(Modifier.size(80.dp).background(Color(0xFF493124))) {
                    AppleMusicDesktopTransportButton(
                        painter = if (playing.value) Res.drawable.icon_apple_music_pause else Res.drawable.icon_apple_music_play,
                        motion = AppleMusicDesktopTransportMotion.PlayPause,
                        contentDescription = if (playing.value) "pause" else "play",
                        tint = Color.White,
                        iconSize = 43.dp,
                        buttonWidth = 80.dp,
                        onClick = { clicks++; playing.value = !playing.value },
                        modifier = Modifier.fillMaxSize().testTag("transport"),
                    )
                }
            }
        }
        waitForIdle()
        val node = onNodeWithTag("transport")
        val initial = node.captureToImage()
        mainClock.autoAdvance = false
        repeat(6) {
            node.performClick()
            mainClock.advanceTimeBy(32)
        }
        mainClock.advanceTimeBy(1_200)
        assertEquals(6, clicks)
        assertTrue(!playing.value)
        assertTrue(pixelDifference(initial, node.captureToImage()) < 0.0001f)
    }

    @Test
    fun cancelledAndDisabledPressesDoNotInvokePlayback() = runComposeUiTest {
        val enabled = mutableStateOf(true)
        var clicks = 0
        setContent {
            MiuixTheme {
                Box(Modifier.size(80.dp)) {
                    AppleMusicDesktopTransportButton(
                        painter = Res.drawable.icon_apple_music_shuffle,
                        contentDescription = "shuffle",
                        motion = AppleMusicDesktopTransportMotion.Shuffle,
                        selected = true,
                        enabled = enabled.value,
                        tint = Color.White,
                        iconSize = 40.dp,
                        buttonWidth = 80.dp,
                        onClick = { clicks++ },
                        modifier = Modifier.fillMaxSize().testTag("transport"),
                    )
                }
            }
        }
        waitForIdle()
        val node = onNodeWithTag("transport")
        node.assertIsSelected()
        node.performMouseInput { moveTo(center); press(); moveTo(Offset(-40f, -40f)); release() }
        assertEquals(0, clicks)
        node.performMouseInput { moveTo(center); press() }
        runOnIdle { enabled.value = false }
        node.performMouseInput { release() }
        node.assertIsNotEnabled()
        node.performClick()
        assertEquals(0, clicks)
    }
}

private fun pixelDifference(first: ImageBitmap, second: ImageBitmap): Float {
    val a = first.toPixelMap()
    val b = second.toPixelMap()
    var total = 0f
    for (y in 0 until a.height) for (x in 0 until a.width) {
        total += abs(a[x, y].red - b[x, y].red) + abs(a[x, y].alpha - b[x, y].alpha)
    }
    return total / (a.width * a.height)
}
