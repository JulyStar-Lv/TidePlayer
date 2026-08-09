package io.github.julystar.musicapp.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class LyricFilteringTest {
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
    fun keepsHeaderTagsWhenFilteringIsDisabled() {
        val settings = LyricDisplaySettings.Default.copy(ignoreHeaderTags = false)

        assertEquals(
            listOf("[ar:Artist]", "Keep me"),
            settings.filterLyricTextBlock("[ar:Artist]\nKeep me"),
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
            LyricDisplaySettings.Default.filterLyricTextBlock("[ar:Artist]\n[ti:Song]"),
        )
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
}
