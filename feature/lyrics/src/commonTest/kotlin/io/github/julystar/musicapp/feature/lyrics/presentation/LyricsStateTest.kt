package io.github.julystar.musicapp.feature.lyrics.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class LyricsStateTest {

    @Test
    fun `default state is loading with empty lines`() {
        val state = LyricsState()

        assertTrue(state.isLoading)
        assertEquals("", state.trackTitle)
        assertNull(state.trackArtist)
        assertEquals(persistentListOf(), state.lines)
        assertNull(state.error)
    }

    @Test
    fun `loaded state carries lyric data`() {
        val lines = persistentListOf("Line 1", "Line 2")
        val state = LyricsState(
            isLoading = false,
            trackTitle = "My Song",
            trackArtist = "Artist",
            lines = lines,
            format = "lrc",
            synchronized = true,
        )

        assertFalse(state.isLoading)
        assertEquals("My Song", state.trackTitle)
        assertEquals("Artist", state.trackArtist)
        assertEquals(2, state.lines.size)
        assertEquals("lrc", state.format)
        assertTrue(state.synchronized)
    }

    @Test
    fun `error state carries message`() {
        val state = LyricsState(isLoading = false, error = UiMessage.Text("Failed to load lyrics"))

        assertFalse(state.isLoading)
        assertEquals(UiMessage.Text("Failed to load lyrics"), state.error)
    }

    @Test
    fun `navigate back and retry actions are singletons`() {
        assertEquals(LyricsAction.NavigateBack, LyricsAction.NavigateBack)
        assertEquals(LyricsAction.Retry, LyricsAction.Retry)
    }

    @Test
    fun `error state preserves previous title and artist`() {
        val state = LyricsState(
            isLoading = false,
            trackTitle = "Lost Song",
            trackArtist = "Ghost",
            error = UiMessage.Text("Not found"),
        )

        assertEquals("Lost Song", state.trackTitle)
        assertEquals("Ghost", state.trackArtist)
        assertEquals(UiMessage.Text("Not found"), state.error)
    }

    @Test
    fun `wordTimedLines defaults to empty`() {
        val state = LyricsState()

        assertEquals(persistentListOf(), state.wordTimedLines)
    }

    @Test
    fun `wordTimedLyricLine carries duration text and tokens`() {
        val line = WordTimedLyricLine(
            duration = 5000L.milliseconds,
            text = "Hello world",
            words = persistentListOf(
                WordTimedToken(
                    text = "Hello",
                    startOffset = Duration.ZERO,
                    duration = 1000L.milliseconds,
                ),
                WordTimedToken(
                    text = " world",
                    startOffset = 1000L.milliseconds,
                    duration = 4000L.milliseconds,
                ),
            ),
        )

        assertEquals("Hello world", line.text)
        assertEquals(5000L, line.duration.inWholeMilliseconds)
        assertEquals(2, line.words.size)
        assertEquals("Hello", line.words[0].text)
        assertEquals(0L, line.words[0].startOffset.inWholeMilliseconds)
    }

    @Test
    fun `lyrics screen dismisses after half screen drag`() {
        assertFalse(
            shouldDismissLyricsScreen(
                dragOffsetPx = 499f,
                viewportHeightPx = 1000f,
                velocityPxPerSecond = 899f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertTrue(
            shouldDismissLyricsScreen(
                dragOffsetPx = 500f,
                viewportHeightPx = 1000f,
                velocityPxPerSecond = 0f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }

    @Test
    fun `lyrics screen dismisses only for fast downward fling`() {
        assertTrue(
            shouldDismissLyricsScreen(
                dragOffsetPx = 100f,
                viewportHeightPx = 1000f,
                velocityPxPerSecond = 900f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertFalse(
            shouldDismissLyricsScreen(
                dragOffsetPx = 100f,
                viewportHeightPx = 1000f,
                velocityPxPerSecond = -1200f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }
}
