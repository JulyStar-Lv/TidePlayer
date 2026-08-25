package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.core.data.toPlaybackLyrics
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ManualMetadataServiceTest {
    @Test
    fun keepsOnlyResultsWithLyricsAndCover() {
        val candidate = MetaSongCandidate(
            id = "song-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            date = "2026",
            pictureUrl = "  https://example.test/song-1.jpg  ",
            sourceId = "source-a",
        )
        val lyrics = MetaLyricsCandidate(
            id = "lyrics-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            date = "2026",
            lyrics = MetaLyrics(rawPlainLrc = "[00:00]Song"),
            sourceId = "source-a",
        )

        assertEquals(
            ManualMetadataResult(candidate, lyrics),
            candidate.toManualMetadataResult(listOf(lyrics)),
        )
        assertNull(candidate.copy(pictureUrl = "  ").toManualMetadataResult(listOf(lyrics)))
        assertNull(candidate.copy(pictureUrl = null).toManualMetadataResult(listOf(lyrics)))
        assertNull(candidate.toManualMetadataResult(emptyList()))

        assertEquals(
            MetaCoverCandidate(
                url = "https://example.test/song-1.jpg",
                id = "song-1",
                title = "Song",
                artist = "Artist",
                album = "Album",
                date = "2026",
                sourceId = "source-a",
            ),
            candidate.toManualCoverCandidate(),
        )
        assertNull(candidate.copy(pictureUrl = "  ").toManualCoverCandidate())
        assertNull(candidate.copy(pictureUrl = null).toManualCoverCandidate())
    }

    @Test
    fun ranksClosestManualMetadataMatchFirstAndRemovesSourceDuplicates() {
        val exact = MetaSongCandidate(
            id = "exact",
            title = "兰亭序",
            artist = "周杰伦",
            durationMs = 253_000,
            sourceId = "source-a",
        )
        val ranked = rankManualMetadataCandidates(
            candidates = listOf(
                MetaSongCandidate(
                    id = "other",
                    title = "兰亭序 (Live)",
                    artist = "其他歌手",
                    durationMs = 280_000,
                    sourceId = "source-b",
                ),
                exact,
                exact.copy(title = "duplicate"),
            ),
            track = NowPlayingTrackItem(
                id = 1,
                title = "兰亭序",
                artist = "周杰伦",
                durationMs = 254_000,
                artwork = null,
                mediaId = null,
            ),
            keyword = "兰亭序 周杰伦",
        )

        assertEquals(listOf("exact", "other"), ranked.map(MetaSongCandidate::id))
    }

    @Test
    fun prefersPlainLrcReturnedByPlugin() {
        val entity = assertNotNull(
            MetaLyrics(
                lines = listOf(MetaLyricLine(text = "Parsed", startMs = 1_000)),
                rawPlainLrc = "[00:01.00]Raw",
            ).toEntity(trackId = 42, updatedAt = 7),
        )

        assertEquals(42, entity.trackId)
        assertEquals("LRC", entity.format)
        assertEquals("[00:01.00]Raw", entity.content)
        assertEquals(true, entity.synchronized)
    }

    @Test
    fun buildsLrcFromStructuredLines() {
        val entity = assertNotNull(
            MetaLyrics(
                lines = listOf(
                    MetaLyricLine(text = "First", startMs = 1_230),
                    MetaLyricLine(text = "Second", startMs = 61_090),
                ),
            ).toEntity(trackId = 1, updatedAt = 2),
        )

        assertEquals("[00:01.23]First\n[01:01.09]Second", entity.content)
        assertEquals(true, entity.synchronized)
    }

    @Test
    fun preservesStructuredWordTimingAsEnhancedLrc() {
        val entity = assertNotNull(
            MetaLyrics(
                lines = listOf(
                    MetaLyricLine(
                        text = "Hello world",
                        startMs = 2_000,
                        endMs = 3_000,
                        words = listOf(
                            MetaLyricWord("Hello", startMs = 2_000, endMs = 2_500),
                            MetaLyricWord(" world", startMs = 2_500, endMs = 3_000),
                        ),
                    ),
                ),
                rawPlainLrc = "[00:02.00]Hello world",
            ).toEntity(trackId = 1, updatedAt = 2),
        )

        assertEquals(
            "[00:02.00]<00:02.000>Hello<00:02.500> world<00:03.000>",
            entity.content,
        )
        assertEquals("LRC", entity.format)
        assertEquals(true, entity.synchronized)
        assertEquals("ExternalWordTimed", entity.sourceKind)
    }

    @Test
    fun prefersRawWordTimedLyricsOverPlainLrc() {
        val enhanced = "[00:02.00]<00:02.000>Hello<00:03.000>"
        val entity = assertNotNull(
            MetaLyrics(
                rawPlainLrc = "[00:02.00]Hello",
                rawEnhancedLrc = enhanced,
            ).toEntity(trackId = 1, updatedAt = 2),
        )

        assertEquals(enhanced, entity.content)
    }

    @Test
    fun structuredPluginLyricsRoundTripIntoKaraokeWords() {
        val pluginLyrics = assertNotNull(
            PluginResultParser().lyrics(
                """
                {
                  "type": "structured",
                  "original": [
                    [2000, 3000, [[2000, 2500, "Hello"], [2500, 3000, " world"]]]
                  ]
                }
                """.trimIndent(),
            ),
        )

        val playbackLyrics = assertNotNull(
            pluginLyrics.toEntity(trackId = 1, updatedAt = 2),
        ).toPlaybackLyrics()

        val line = playbackLyrics.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
        assertEquals(0, line.words[0].startOffset.inWholeMilliseconds)
        assertEquals(500, line.words[1].startOffset.inWholeMilliseconds)
    }

    @Test
    fun structuredPluginLyricsRoundTripWithTranslation() {
        val pluginLyrics = assertNotNull(
            PluginResultParser().lyrics(
                """
                {
                  "type": "structured",
                  "original": [
                    [2000, 3000, [[2000, 2500, "Hello"], [2500, 3000, " world"]]]
                  ],
                  "translated": [
                    [2000, 3000, "你好世界"]
                  ]
                }
                """.trimIndent(),
            ),
        )

        val playbackLyrics = assertNotNull(
            pluginLyrics.toEntity(trackId = 1, updatedAt = 2),
        ).toPlaybackLyrics()

        assertEquals("Hello world\n你好世界", playbackLyrics.lines.single().text)
    }

    @Test
    fun topLevelTranslatedTrackIsAttachedToStructuredLyrics() {
        val playbackLyrics = assertNotNull(
            MetaLyrics(
                lines = listOf(
                    MetaLyricLine(
                        text = "Hello world",
                        startMs = 0,
                        endMs = 1_000,
                        words = listOf(
                            MetaLyricWord("Hello", startMs = 0, endMs = 400),
                            MetaLyricWord(" world", startMs = 500, endMs = 1_000),
                        ),
                    ),
                ),
                rawPlainLrc = "[00:00.00]Hello world",
                translated = "你好世界",
            ).toEntity(trackId = 1, updatedAt = 2),
        ).toPlaybackLyrics()

        assertEquals("Hello world\n你好世界", playbackLyrics.lines.single().text)
    }

    @Test
    fun rawTtmlRoundTripsIntoKaraokeWords() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="00:02.000" end="00:03.000"><span begin="00:02.000" end="00:02.500">Hello</span> <span begin="00:02.500" end="00:03.000">world</span></p>
                </div></body>
            </tt>
        """.trimIndent()

        val entity = assertNotNull(
            MetaLyrics(rawTtml = ttml).toEntity(trackId = 1, updatedAt = 2),
        )
        val line = entity.toPlaybackLyrics().lines.single()

        assertEquals("TTML", entity.format)
        assertEquals("ExternalTtml", entity.sourceKind)
        assertEquals("Hello world", line.text)
        assertEquals(2, line.words.size)
    }

    @Test
    fun displaysMetadataSourceIds() {
        listOf(
            "com.qqmusic.source",
            "com.kugou.source",
            "com.applemusic.source",
            "com.sodamusic.source",
            "com.neteasecloudmusic.source",
            "com.example.source",
        ).forEach { sourceId ->
            assertEquals(sourceId, metadataSourceDisplayName(sourceId))
        }
    }
}
