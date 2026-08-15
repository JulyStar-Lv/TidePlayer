package io.github.julystar.musicapp.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class LyricFilteringTest {
    @Test
    fun filtersKugouMetadataTagsFromWhitelist() {
        val content = """
            [id:abc]
            [hash:123]
            [sign:]
            [qq:]
            [total:0]
            [offset:0]
            第一句歌词
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "第一句歌词"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersCommonQrcMetadataTags() {
        val content = """
            [ti:Song]
            [ar:Artist]
            [al:Album]
            [by:]
            [offset:0]
            正文
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "正文"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun ignoresCommonHeaderVariantsWhenEnabled() {
        val content = """
            ﻿[ar:Artist]
            [ encoding : UTF-8]
            [provider：Example]
            [bg:Backing vocal]
            Keep me
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "[bg:Backing vocal]", "Keep me"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsBackgroundLyricTagsEvenNextToMetadata() {
        val content = """
            [bg:Backing vocal]
            [ar:Artist]
            [x-bg:Background lyric]
            Keep me
        """.trimIndent()

        assertEquals(
            listOf("[bg:Backing vocal]", "[x-bg:Background lyric]", "Keep me"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsUnknownAndTtmlLikeTags() {
        val content = """
            [custom:value]
            [songwriters:Someone]
            [itunes:key:C]
            [ttm:title:Section]
            Keep me
        """.trimIndent()

        assertEquals(
            content.lines(),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersKrcTimingTagsForDetectionWithoutChangingBodyText() {
        val content = """
            [729,364]<0,60,0>制<60,60,0>作<120,60,0>人<180,60,0>：<240,60,0>雷<300,60,0>声
            [1200,500]<0,200,0>H<200,150,0>o<350,150,0>w
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "[1200,500]<0,200,0>H<200,150,0>o<350,150,0>w"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersEnglishCreditsCaseInsensitively() {
        val content = """
            Lyricist: A
            Composer: B
            ARRANGER: C
            Vocal Producer: D
            Executive Producer: E
            Recording Engineer: F
            Mixed by: G
            Mastered by: H
            Real lyric line
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "Real lyric line"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersExtendedChineseCredits() {
        val content = """
            配唱制作人：A
            制作统筹：B
            录音师：C
            混音工程师：D
            母带工程师：E
            和声编写：F
            出品公司：G
            发行方：H
            正文
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "正文"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersControlledCompoundCredits() {
        val content = """
            吉他贝斯鼓：A
            吉他/贝斯/鼓：A
            混音/母带：A
            作词/作曲：A
            正文
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "正文"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsRoleWordsUsedAsLyricSentences() {
        val content = """
            吉他贝斯鼓声响起
            我把作词作曲写进青春
            混音像夜色一样模糊
            这是我们的和声
            我要和你一起发行梦想
            制作一个新的世界
        """.trimIndent()

        assertEquals(
            content.lines(),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersHeaderOnlyCreditsOnlyAtBeginning() {
        val content = """
            策划：A
            统筹：B
            正文第一句
            特别鸣谢那些陪我走过的人
            正文第三句
        """.trimIndent()

        assertEquals(
            listOf(
                LYRIC_HEADER_PLACEHOLDER,
                "正文第一句",
                "特别鸣谢那些陪我走过的人",
                "正文第三句",
            ),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsHeaderOnlyCreditFormatAfterLyricsBegin() {
        val content = """
            正文第一句
            正文第二句
            特别鸣谢：陪伴我的人
            正文第三句
        """.trimIndent()

        assertEquals(
            content.lines(),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersStrongMetadataAfterLyricsBegin() {
        val content = """
            正文第一句
            正文第二句
            [ar:Should not display]
            正文第三句
        """.trimIndent()

        assertEquals(
            listOf("正文第一句", "正文第二句", "正文第三句"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsHeaderTagsWhenFilteringIsDisabled() {
        val settings = LyricDisplaySettings.Default.copy(ignoreHeaderTags = false)

        val content = """
            [hash:123]
            制作人：A
            策划：B
            //
            Keep me
        """.trimIndent()

        assertEquals(
            content.lines(),
            settings.filterLyricTextBlock(content),
        )
    }

    @Test
    fun filtersHeadersInsideUnsynchronisedLyricsForPlatformOutput() {
        val lyrics = Lyrics(
            lines = persistentListOf(
                LyricLine(0.milliseconds, "[ti:Song]\n[provider:Example]\nFirst\nSecond"),
            ),
            loadState = LyricsLoadState.Loaded,
        )

        val filtered = lyrics.filteredForDisplay(LyricDisplaySettings.Default)

        assertEquals("$LYRIC_HEADER_PLACEHOLDER\nFirst\nSecond", filtered.lines.single().text)
    }

    @Test
    fun doesNotCreatePlaceholderWhenHeadersAreTheOnlyContent() {
        assertEquals(
            emptyList(),
            LyricDisplaySettings.Default.filterLyricTextBlock("制作人：A\n作词：B\n作曲：C"),
        )
    }

    @Test
    fun preservesTimedBodyLineAndWordTiming() {
        val body = LyricLine(
            duration = 3_400.milliseconds,
            text = "How long",
            words = persistentListOf(
                LyricWord("How", 0.milliseconds, 300.milliseconds),
                LyricWord("long", 350.milliseconds, 450.milliseconds),
            ),
        )
        val lines = listOf(
            LyricLine(729.milliseconds, "制作人：雷声"),
            body,
        )

        val filtered = lines.filterLyricLinesForDisplay(LyricDisplaySettings.Default)

        assertEquals(listOf(LYRIC_HEADER_PLACEHOLDER, "How long"), filtered.map(LyricLine::text))
        assertEquals(body, filtered.last())
    }

    @Test
    fun collapsesTimedVisibleCreditBlockIntoPlaceholder() {
        val content = """
            [00:00.00]<00:00.000>My<00:00.441> story, your song - 孙燕姿
            [00:01.00]//
            [00:02.00]<00:02.000>Lyrics<00:02.500> by：孙燕姿
            [00:03.00]//
            [00:04.00]<00:04.000>Composed<00:04.500> by：李伟菘
            [00:05.00]孙燕姿：
            [00:06.00]//
            [00:10.00]When I was a little girl
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "[00:10.00]When I was a little girl"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun keepsOnlyBodyLinesWhenHeaderFilteringIsEnabled() {
        val content = """
            [ti:Song]
            First line
            [by:Provider]
            //
            Lyrics by: Someone
            Second line
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "First line", "Second line"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }

    @Test
    fun removesInstrumentPublishingAndRightsCreditsFromCurrentLyricShape() {
        val content = """
            [00:00.00]Song title - Artist
            [00:01.51]词：Writer
            [00:03.03]曲：Composer
            [00:04.54]编曲：Arranger
            [00:06.06]弦乐：Orchestra
            [00:07.57]录音：Engineer
            [00:09.09]混音：Mixer
            [00:10.60]OP：Publisher [SP:Sub-publisher]
            [00:12.12]（本着作之使用经著作权人授权）
            [00:35.59]第一句歌词
        """.trimIndent()

        assertEquals(
            listOf(LYRIC_HEADER_PLACEHOLDER, "[00:35.59]第一句歌词"),
            LyricDisplaySettings.Default.filterLyricTextBlock(content),
        )
    }
}
