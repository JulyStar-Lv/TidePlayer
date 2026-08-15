package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingLyricsLayoutTest {
    @Test
    fun placesBilingualFocusOnFirstRowInPortrait() {
        assertEquals(
            0,
            nowPlayingLyricsContextLinesBeforeActive(
                isPortrait = true,
                showTranslation = true,
                hasTranslation = true,
            ),
        )
    }

    @Test
    fun keepsSecondRowForSingleLanguageLyrics() {
        assertEquals(
            1,
            nowPlayingLyricsContextLinesBeforeActive(
                isPortrait = true,
                showTranslation = true,
                hasTranslation = false,
            ),
        )
    }

    @Test
    fun keepsSecondRowWhenTranslationIsHidden() {
        assertEquals(
            1,
            nowPlayingLyricsContextLinesBeforeActive(
                isPortrait = true,
                showTranslation = false,
                hasTranslation = true,
            ),
        )
    }

    @Test
    fun keepsSecondRowForLandscapeLyrics() {
        assertEquals(
            1,
            nowPlayingLyricsContextLinesBeforeActive(
                isPortrait = false,
                showTranslation = true,
                hasTranslation = true,
            ),
        )
    }
}
