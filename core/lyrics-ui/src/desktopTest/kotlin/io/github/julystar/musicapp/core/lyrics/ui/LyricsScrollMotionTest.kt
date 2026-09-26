package io.github.julystar.musicapp.core.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import io.github.julystar.musicapp.core.lyrics.ui.reference.LyricsMotionSpec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LyricsScrollMotionTest {
    @Test
    fun alignsTheFocusedRowInViewportCoordinatesIncludingTopPadding() = runComposeUiTest {
        setContent { LyricsTestSurface(positionMs = 4_000) }
        waitForIdle()

        assertEquals(160f, lineCenter("Line 4"), 1f)
    }

    @Test
    fun centersTheFirstAndLastRowsWithHalfViewportPadding() = runComposeUiTest {
        val position = mutableIntStateOf(0)
        setContent {
            LyricsTestSurface(
                positionMs = position.intValue,
                height = 512.dp,
                focusLineCenterY = 256.dp,
                verticalContentPaddingFraction = 0.5f,
            )
        }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 0"), 1f)

        runOnIdle { position.intValue = 19_000 }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 19"), 1f)
    }

    @Test
    fun keepsTheWholeRowCenteredWhenTranslationIsToggled() = runComposeUiTest {
        val showTranslation = mutableStateOf(true)
        val lyrics = testLyrics("Line", translated = true)
        setContent {
            LyricsTestSurface(
                positionMs = 4_000,
                lyrics = lyrics,
                height = 512.dp,
                focusLineCenterY = 256.dp,
                verticalContentPaddingFraction = 0.5f,
                showTranslation = showTranslation.value,
            )
        }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 4"), 1f)

        runOnIdle { showTranslation.value = false }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 4"), 1f)

        runOnIdle { showTranslation.value = true }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 4"), 1f)
    }

    @Test
    fun anchorsPrimaryTextStartAcrossTranslationChangesAndFirstLastRows() = runComposeUiTest {
        val showTranslation = mutableStateOf(true)
        val position = mutableIntStateOf(0)
        val height = mutableStateOf(512.dp)
        setContent {
            LyricsTestSurface(
                positionMs = position.intValue,
                lyrics = testLyrics("Primary with enough words to wrap", translated = true),
                height = height.value,
                focusLineCenterY = 104.dp,
                showTranslation = showTranslation.value,
                focusPrimaryText = true,
                focusLineContentAnchorFraction = 0f,
            )
        }
        fun assertPrimaryCentered(index: Int) {
            waitForIdle()
            val bounds = onNodeWithText(
                "Primary with enough words to wrap $index", useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            assertEquals(104f, bounds.top.value, 1f)
        }
        assertPrimaryCentered(0)
        runOnIdle { showTranslation.value = false }
        assertPrimaryCentered(0)
        runOnIdle { showTranslation.value = true; position.intValue = 19_000 }
        assertPrimaryCentered(19)
        runOnIdle { height.value = 400.dp }
        assertPrimaryCentered(19)
    }

    @Test
    fun reanchorsTheCurrentRowWhenTheViewportHeightChanges() = runComposeUiTest {
        val height = mutableStateOf(512.dp)
        setContent {
            LyricsTestSurface(
                positionMs = 4_000,
                height = height.value,
                focusLineCenterY = height.value / 2f,
                verticalContentPaddingFraction = 0.5f,
            )
        }
        waitForIdle()
        assertEquals(256f, lineCenter("Line 4"), 1f)

        runOnIdle { height.value = 400.dp }
        waitForIdle()
        assertEquals(200f, lineCenter("Line 4"), 1f)
    }

    @Test
    fun followsAdjacentRowsWithOneContinuousStaggeredSpring() = runComposeUiTest {
        val position = mutableIntStateOf(4_000)
        setContent { LyricsTestSurface(positionMs = position.intValue) }
        waitForIdle()
        mainClock.autoAdvance = false
        val focusedStart = lineCenter("Line 5")
        val followingStart = lineCenter("Line 6")

        runOnIdle { position.intValue = 5_000 }
        mainClock.advanceTimeBy(160)
        val focusedDuring = lineCenter("Line 5")
        val followingDuring = lineCenter("Line 6")
        assertTrue(focusedDuring > 161f, "The new focus must not snap directly to its anchor")
        assertTrue(focusedDuring < focusedStart, "The focused row must advance toward its anchor")
        assertTrue(
            focusedStart - focusedDuring > followingStart - followingDuring,
            "The following row should trail the focused row's spring",
        )

        val positions = List(5) {
            mainClock.advanceTimeBy(120)
            lineCenter("Line 5")
        }
        assertTrue(positions.zipWithNext().all { (before, after) -> after <= before + 1f })
        mainClock.advanceTimeBy(2_000)
        assertEquals(160f, lineCenter("Line 5"), 1f)
    }

    @Test
    fun keepsTheExitingRowPlacedUntilItsSpringMovesItOutOfView() = runComposeUiTest {
        val position = mutableIntStateOf(4_000)
        setContent { LyricsTestSurface(positionMs = position.intValue) }
        waitForIdle()
        mainClock.autoAdvance = false
        val previousTop = lineCenter("Line 2")

        runOnIdle { position.intValue = 5_000 }
        mainClock.advanceTimeBy(80)
        val during = lineCenter("Line 2")
        assertTrue(during > -20f && during < previousTop)
    }

    @Test
    fun wheelBrowsingSurvivesLineChangesAndResumesAfterIdle() = runComposeUiTest {
        val position = mutableIntStateOf(4_000)
        setContent { LyricsTestSurface(positionMs = position.intValue) }
        waitForIdle()
        mainClock.autoAdvance = false
        val beforeScroll = lineCenter("Line 5")
        scrollLyrics()
        val afterScroll = lineCenter("Line 5")
        assertTrue(abs(afterScroll - beforeScroll) > 1f, "The wheel must move the lyrics")

        runOnIdle { position.intValue = 5_000 }
        mainClock.advanceTimeBy(800)
        assertEquals(afterScroll, lineCenter("Line 5"), 1f)

        // Further wheel input renews the idle interval rather than being ignored while browsing.
        scrollLyrics()
        val afterSecondScroll = lineCenter("Line 5")
        mainClock.advanceTimeBy(3_000)
        assertEquals(afterSecondScroll, lineCenter("Line 5"), 1f)
        mainClock.advanceTimeBy(3_000)
        assertRecovered("Line 5")
    }

    @Test
    fun clickingTheCurrentRowResumesEvenWhenItsIndexDoesNotChange() = runComposeUiTest {
        var seekCount = 0
        setContent {
            LyricsTestSurface(positionMs = 4_000, onLineClick = { seekCount++ })
        }
        waitForIdle()
        mainClock.autoAdvance = false
        scrollLyrics()
        assertTrue(abs(lineCenter("Line 4") - 160f) > 1f)

        onNodeWithText("Line 4").performClick()
        mainClock.advanceTimeBy(2_000)
        assertEquals(1, seekCount)
        assertRecovered("Line 4")
    }

    @Test
    fun replacingLyricsWithTheSameLineCountResetsManualBrowsing() = runComposeUiTest {
        val lyrics = mutableStateOf(testLyrics("First"))
        setContent { LyricsTestSurface(positionMs = 4_000, lyrics = lyrics.value) }
        waitForIdle()
        mainClock.autoAdvance = false
        scrollLyrics()

        runOnIdle { lyrics.value = testLyrics("Second") }
        mainClock.advanceTimeBy(500)
        assertRecovered("Second 4")
    }

    private fun ComposeUiTest.lineCenter(text: String): Float {
        val bounds = onNodeWithText(text).getUnclippedBoundsInRoot()
        return (bounds.top.value + bounds.bottom.value) / 2f
    }

    private fun ComposeUiTest.assertRecovered(text: String) {
        val positions = List(10) {
            mainClock.advanceTimeBy(100)
            lineCenter(text)
        }
        assertEquals(160f, positions.last(), 1f, "Recovery positions: $positions")
    }

    private fun ComposeUiTest.scrollLyrics() {
        onNodeWithTag("lyrics").performMouseInput {
            moveTo(center)
            scroll(2f)
        }
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }
}

@Composable
private fun LyricsTestSurface(
    positionMs: Int,
    lyrics: SyncedLyrics = TestLyrics,
    onLineClick: (ISyncedLine) -> Unit = {},
    height: Dp = 400.dp,
    focusLineCenterY: Dp = 160.dp,
    verticalContentPaddingFraction: Float = 0.34f,
    showTranslation: Boolean = false,
    focusPrimaryText: Boolean = false,
    focusLineContentAnchorFraction: Float = 0.5f,
) {
    Box(Modifier.size(360.dp, height)) {
        LyricsView(
            lyrics = lyrics,
            currentPositionMs = positionMs,
            isPlaying = false,
            modifier = Modifier.testTag("lyrics"),
            onLineClick = onLineClick,
            activeTextStyle = TestTextStyle,
            inactiveTextStyle = TestTextStyle,
            wordLiftEnabled = false,
            useBlurEffect = false,
            showTranslation = showTranslation,
            lineSpacing = 32.dp,
            lineHorizontalPadding = 0.dp,
            lineVerticalPadding = 0.dp,
            contextLinesBeforeActive = 0,
            verticalContentPaddingFraction = verticalContentPaddingFraction,
            focusLineCenterY = focusLineCenterY,
            focusPrimaryText = focusPrimaryText,
            focusLineContentAnchorFraction = focusLineContentAnchorFraction,
            motionSpec = LyricsMotionSpec(),
        )
    }
}

private val TestTextStyle = TextStyle(fontSize = 24.sp, lineHeight = 40.sp)
private val TestLyrics = testLyrics("Line")

private fun testLyrics(prefix: String, translated: Boolean = false) = SyncedLyrics(
    lines = List(20) { index ->
        SyncedLine(
            content = "$prefix $index",
            translation = if (translated) "Translation $index\nSecond translation row" else null,
            start = index * 1_000,
            end = (index + 1) * 1_000,
        )
    },
)
