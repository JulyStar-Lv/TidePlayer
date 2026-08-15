package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricWord
import io.github.julystar.musicapp.core.domain.model.LYRIC_HEADER_PLACEHOLDER
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NowPlayingLyricsAdapterTest {
    @Test
    fun filtersHeaderTagsOnly() {
        val lines = listOf(
            LyricLine(0.milliseconds, "[ar:Artist]"),
            LyricLine(1_000.milliseconds, "Instrumental"),
            LyricLine(2_000.milliseconds, "Keep me"),
        )

        val visible = lines.filterVisibleLyrics(LyricDisplaySettings.Default)

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "Instrumental", "Keep me"),
            visible.map(LyricLine::text),
        )
    }

    @Test
    fun splitsUnsynchronisedBlocksBeforeFiltering() {
        val lines = listOf(LyricLine(0.milliseconds, "[ti:Song]\nFirst\nSecond"))

        val visible = lines.filterVisibleLyrics(LyricDisplaySettings.Default)

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "First", "Second"),
            visible.map(LyricLine::text),
        )
    }

    @Test
    fun collapsesVisibleTitleAndCreditBlockIntoPlaceholder() {
        val lines = listOf(
            LyricLine(0.milliseconds, "My story, your song - 孙燕姿\n//"),
            LyricLine(6_210.milliseconds, "Lyrics by：孙燕姿\n//"),
            LyricLine(12_420.milliseconds, "Composed by：李伟菘\n//"),
            LyricLine(18_640.milliseconds, "孙燕姿：\n//"),
            LyricLine(30_060.milliseconds, "Is your smile genuine\n你的笑是发自真心么"),
        )

        val visible = lines.filterVisibleLyrics(LyricDisplaySettings.Default)

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "Is your smile genuine\n你的笑是发自真心么"),
            visible.map(LyricLine::text),
        )
        assertEquals(0, visible.first().duration.inWholeMilliseconds)
    }

    @Test
    fun removesNonBodyLinesAfterTimedLyricsBegin() {
        val lines = listOf(
            LyricLine(0.milliseconds, "[ti:Song]"),
            LyricLine(1_000.milliseconds, "First line"),
            LyricLine(2_000.milliseconds, "[by:Provider]"),
            LyricLine(3_000.milliseconds, "//"),
            LyricLine(4_000.milliseconds, "Lyrics by: Someone"),
            LyricLine(5_000.milliseconds, "Second line"),
        )

        val visible = lines.filterVisibleLyrics(LyricDisplaySettings.Default)

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "First line", "Second line"),
            visible.map(LyricLine::text),
        )
    }

    @Test
    fun collapsesCurrentTrackProductionAndRightsBlockIntoPlaceholder() {
        val lines = listOf(
            LyricLine(0.milliseconds, "Song title - Artist"),
            LyricLine(1_510.milliseconds, "词：Writer"),
            LyricLine(3_030.milliseconds, "曲：Composer"),
            LyricLine(4_540.milliseconds, "编曲：Arranger"),
            LyricLine(6_060.milliseconds, "弦乐：Orchestra"),
            LyricLine(7_570.milliseconds, "录音：Engineer"),
            LyricLine(9_090.milliseconds, "混音：Mixer"),
            LyricLine(10_600.milliseconds, "OP：Publisher [SP:Sub-publisher]"),
            LyricLine(12_120.milliseconds, "（本着作之使用经著作权人授权）"),
            LyricLine(35_590.milliseconds, "第一句歌词"),
        )

        val visible = lines.filterVisibleLyrics(LyricDisplaySettings.Default)

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "第一句歌词"),
            visible.map(LyricLine::text),
        )
        assertEquals(0, visible.first().duration.inWholeMilliseconds)
    }

    @Test
    fun convertsTimestampLinesIntoContinuousTimeline() {
        val lyrics = listOf(
            LyricLine(duration = 1.seconds, text = "First"),
            LyricLine(duration = 3.seconds, text = "Second"),
        ).toSyncedLyrics(trackTitle = "Song", trackDurationMs = 5_000)

        assertEquals("Song", lyrics.title)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000, lyrics.lines[0].start)
        assertEquals(3_000, lyrics.lines[0].end)
        assertEquals(3_000, lyrics.lines[1].start)
        assertEquals(5_000, lyrics.lines[1].end)
        assertIs<SyncedLine>(lyrics.lines[0])
    }

    @Test
    fun preservesSecondaryTextAsTranslationForTimedLyrics() {
        val lyrics = listOf(
            LyricLine(duration = 1.seconds, text = "Hello\n你好"),
            LyricLine(duration = 3.seconds, text = "World\n世界"),
        ).toSyncedLyrics(trackTitle = "Song", trackDurationMs = 5_000)

        val first = assertIs<SyncedLine>(lyrics.lines.first())
        assertEquals("Hello", first.content)
        assertEquals("你好", first.translation)
    }

    @Test
    fun preservesWordTimingAsAbsoluteKaraokeSyllables() {
        val lyrics = listOf(
            LyricLine(
                duration = 2.seconds,
                text = "Hello world",
                words = persistentListOf(
                    LyricWord("Hello", 0.milliseconds, 400.milliseconds),
                    LyricWord("world", 500.milliseconds, 500.milliseconds),
                ),
            ),
        ).toSyncedLyrics(trackTitle = "Song", trackDurationMs = 4_000)

        val line = assertIs<KaraokeLine.MainKaraokeLine>(lyrics.lines.single())
        assertEquals("Hello ", line.syllables[0].content)
        assertEquals(2_000, line.syllables[0].start)
        assertEquals(2_400, line.syllables[0].end)
        assertEquals(2_500, line.syllables[1].start)
        assertEquals(3_000, line.syllables[1].end)
    }

    @Test
    fun doesNotDuplicateSpacesAlreadyContainedInTimedWords() {
        val lyrics = listOf(
            LyricLine(
                duration = 2.seconds,
                text = "Hello world",
                words = persistentListOf(
                    LyricWord("Hello", 0.milliseconds, 400.milliseconds),
                    LyricWord(" world", 500.milliseconds, 500.milliseconds),
                ),
            ),
        ).toSyncedLyrics(trackTitle = "Song", trackDurationMs = 4_000)

        val line = assertIs<KaraokeLine.MainKaraokeLine>(lyrics.lines.single())
        assertEquals("Hello", line.syllables[0].content)
        assertEquals(" world", line.syllables[1].content)
    }
}
